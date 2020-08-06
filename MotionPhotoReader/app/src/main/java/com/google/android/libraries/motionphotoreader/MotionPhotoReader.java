package com.google.android.libraries.motionphotoreader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.adobe.internal.xmp.XMPException;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.os.Build.VERSION_CODES.M;
import static com.google.android.libraries.motionphotoreader.Constants.MICROVIDEO_META_MIMETYPE;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_IMAGE_META_MIMETYPE;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V1;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V2;
import static com.google.android.libraries.motionphotoreader.Constants.VIDEO_MIME_PREFIX;

/**
 * The MotionPhotoReader API allows developers to read through the video portion of Motion Photos in
 * a frame-by-frame manner.
 */

@RequiresApi(api = 28)
public class MotionPhotoReader {

    private static final String TAG = "MotionPhotoReaderClass";

    private final File file;
    private final Surface surface;
    private final MotionPhotoInfo motionPhotoInfo;

    private MediaExtractor extractor;
    private MediaCodec decoder;
    private MediaFormat videoFormat;
    private FileInputStream fileInputStream;
    private BufferProcessor bufferProcessor;

    // message keys
    static final int MSG_NEXT_FRAME = 0x0001;
    static final int MSG_SEEK_TO_FRAME = 0x0010;

    /**
     * The renderWorker and renderHandler are in charge of executing all calls relevant to rendering
     * and transforming the current frame (if stabilization is on).
     */
    private HandlerThread renderWorker;
    private Handler renderHandler;

    /** Available buffer queues **/
    private final BlockingQueue<Integer> availableInputBuffers;
    private final BlockingQueue<Bundle> availableOutputBuffers;

    /** Fields passed onto OpenGL pipeline. */
    private OutputSurface outputSurface;
    private int surfaceWidth;
    private int surfaceHeight;

    /**
     * Standard MotionPhotoReader constructor.
     */
    private MotionPhotoReader(File file,
                              Surface surface,
                              int surfaceWidth,
                              int surfaceHeight,
                              BlockingQueue<Integer> availableInputBuffers,
                              BlockingQueue<Bundle> availableOutputBuffers,
                              MotionPhotoInfo motionPhotoInfo) {
        this.file = file;
        this.surface = surface;
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;
        this.extractor = new MediaExtractor();
        this.availableInputBuffers = availableInputBuffers;
        this.availableOutputBuffers = availableOutputBuffers;
        this.motionPhotoInfo = motionPhotoInfo;
    }

    /**
     * Opens and prepares a new MotionPhotoReader for a particular file.
     * @param file The motion photo file to open.
     * @param surface The surface for the motion photo reader to decode.
     * @param surfaceWidth The width of the surface, in pixels.
     * @param surfaceHeight The height of the surface, in pixels.
     * @param stabilizationOn If true, the video will be stabilized
     * @return a MotionPhotoReader object for the specified file.
     * @throws IOException when the file cannot be found.
     * @throws XMPException when parsing invalid XML syntax.
     */
    public static MotionPhotoReader open(File file,
                                         Surface surface,
                                         int surfaceWidth,
                                         int surfaceHeight,
                                         boolean stabilizationOn
    ) throws IOException, XMPException {
        return open(
                file,
                surface, surfaceWidth, surfaceHeight,
                /* stabilizationOn = */ stabilizationOn,
                /* availableInputBuffers = */ new LinkedBlockingQueue<>(),
                /* availableOutputBuffers = */ new LinkedBlockingQueue<>()
        );
    }

    public static MotionPhotoReader open(File file, Surface surface)
            throws IOException, XMPException {
        return open(
                file,
                surface, /* surfaceWidth = */ 0, /* surfaceHeight = */ 0,
                /* stabilizationOn = */ true,
                /* availableInputBuffers = */ new LinkedBlockingQueue<>(),
                /* availableOutputBuffers = */ new LinkedBlockingQueue<>()
        );
    }

    public static MotionPhotoReader open(String filename, Surface surface)
            throws IOException, XMPException {
        return open(new File(filename), surface);
    }

    /**
     * Opens and prepares a new MotionPhotoReader for testing.
     */
    @VisibleForTesting
    static MotionPhotoReader open(File file,
                                  Surface surface,
                                  int surfaceWidth,
                                  int surfaceHeight,
                                  boolean stabilizationOn,
                                  BlockingQueue<Integer> availableInputBuffers,
                                  BlockingQueue<Bundle> availableOutputBuffers)
            throws IOException, XMPException {
        MotionPhotoInfo motionPhotoInfo = MotionPhotoInfo.newInstance(file);
        MotionPhotoReader reader = new MotionPhotoReader(
                file,
                surface,
                surfaceWidth,
                surfaceHeight,
                availableInputBuffers,
                availableOutputBuffers,
                motionPhotoInfo
        );
        reader.startRenderThread(motionPhotoInfo, stabilizationOn);
        return reader;
    }

    @VisibleForTesting
    static MotionPhotoReader open(File file,
                                  Surface surface,
                                  boolean stabilizationOn,
                                  BlockingQueue<Integer> availableInputBuffers,
                                  BlockingQueue<Bundle> availableOutputBuffers)
            throws IOException, XMPException {
        MotionPhotoInfo motionPhotoInfo = MotionPhotoInfo.newInstance(file);
        MotionPhotoReader reader = new MotionPhotoReader(
                file,
                surface,
                /* surfaceWidth = */ 0,
                /* surfaceHeight = */ 0,
                availableInputBuffers,
                availableOutputBuffers,
                motionPhotoInfo
        );
        reader.startRenderThread(motionPhotoInfo, stabilizationOn);
        return reader;
    }

    /**
     * Sets up and starts a new handler thread for the rendering pipeline and media decoders and
     * extractors.
     *
     * An extractor is set up for both the video and motion track (if applicable). The extractor
     * reads samples for both tracks and passes the information to a buffer processor. A decoder is
     * set up for the video track to read frame data.
     */
    private void startRenderThread(MotionPhotoInfo motionPhotoInfo, boolean stabilizationOn) throws IOException {
        // Set up the render handler and thread
        renderWorker = new HandlerThread("renderHandler");
        renderWorker.start();
        renderHandler = new Handler(renderWorker.getLooper());
        bufferProcessor = new BufferProcessor(availableInputBuffers, availableOutputBuffers);

        // Set up input stream from Motion Photo file for media extractor
        fileInputStream = new FileInputStream(file);
        FileDescriptor fd = fileInputStream.getFD();
        int videoOffset = motionPhotoInfo.getVideoOffset();
        extractor.setDataSource(fd, file.length() - videoOffset, videoOffset);

        // Find the do_not_stabilize bit in the image metadata track and set stabilizationOn
        int version = motionPhotoInfo.getVersion();
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new RuntimeException("Null track mime: " + i);
            }
            if (mime.startsWith(MOTION_PHOTO_IMAGE_META_MIMETYPE)) {
                // 1. Check if the bit exists
                //   a. If the bit exists, set stabilizationOn to true if it was originally true
                //   b. If the bit does not exist, override stabilizationOn and set it to false
                // 2. If the bit does not exist, then override stabilizationOn and set it to false
                if (version == MOTION_PHOTO_V1) {
                    Log.d(TAG, "selected image meta track: " + i);
                    extractor.selectTrack(i);
                    ByteBuffer inputBuffer = ByteBuffer.allocateDirect((int) extractor.getSampleSize());
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize >= 0) {
                        // The do_not_stabilize bit is available
                        ImageMeta.ImageData imageData = ImageMeta.ImageData.parseFrom(inputBuffer);
                        if (imageData.hasDoNotStabilize()) {
                            stabilizationOn = stabilizationOn && !imageData.getDoNotStabilize();
                        } else {
                            stabilizationOn = false;
                        }
                    } else {
                        // The do_not_stabilize bit is unavailable
                        stabilizationOn = false;
                    }
                    extractor.unselectTrack(i);
                    break;
                } else if (version == MOTION_PHOTO_V2) {
                    Log.d(TAG, "selected image meta track: " + i);
                    extractor.selectTrack(i);
                    ByteBuffer inputBuffer = ByteBuffer.allocateDirect((int) extractor.getSampleSize());
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize >= 0) {
                        byte[] bytes = inputBuffer.array();

                        // Ignore padding at the beginning
                        int index = 0;
                        while ((bytes[index] & 0xFF) == 0x00) {
                            index++;
                        }

                        // Get the isStabilized bit
                        while (index < bytes.length) {
                            byte descriptorTag = bytes[index++];
                            // The isStabilized bit lies inside the 0xC4 descriptor
                            if ((descriptorTag & 0xFF) == 0xC4) {
                                // Skip over the variable length block
                                while ((bytes[index] & 0xFF) == 0x80) {
                                    index++;
                                }
                                index++;

                                // The low res 0xC5 descriptor contains the isStabilized bit we want
                                descriptorTag = bytes[index++];
                                assert (descriptorTag & 0xFF) == 0xC5;

                                // The isStabilized bit appears after the variable length for the
                                // 0xC5 block, which should always be equal to 0x01
                                int variableLength = Byte.toUnsignedInt(bytes[index++]);
                                assert variableLength == 1;
                                boolean isStabilized = ((bytes[index] & 0xFF) == 0x01);
                                stabilizationOn = stabilizationOn && !isStabilized;
                                break;
                            } else if ((descriptorTag & 0xFF) == 0xC0) {
                                // We want to ignore the variable length information in the 0xC0
                                // descriptor, since this descriptor contains all the other
                                // descriptors in which we are interested
                                while ((bytes[index] & 0xFF) == 0x80) {
                                    index++;
                                }
                                index++;
                            } else {
                                // Not the descriptor we want, so look for the variable length and
                                // skip that many bytes
                                while ((bytes[index] & 0xFF) == 0x80) {
                                    index++;
                                }
                                int variableLength = Byte.toUnsignedInt(bytes[index++]);
                                index += variableLength;
                            }
                        }

                    } else {
                        // The do_not_stabilize bit is unavailable
                        stabilizationOn = false;
                    }
                    extractor.unselectTrack(i);
                    break;
                } else {
                    throw new RuntimeException("Invalid motion photo version: " + version);
                }
            }
        }

        // Find the appropriate tracks (motion and video) and configure them
        boolean videoTrackSelected = false;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            assert mime != null;
            // Set the video track (which should be the first video track) and create an
            // appropriate media decoder
            if (mime.startsWith(VIDEO_MIME_PREFIX) && !videoTrackSelected) {
                Log.d(TAG, "selected video track: " + i);
                extractor.selectTrack(i);
                bufferProcessor.setVideoTrackIndex(i);
                videoFormat = format;
                decoder = MediaCodec.createDecoderByType(mime);
                videoTrackSelected = true;
            }
            // Set the motion track (if appropriate)
            if (mime.startsWith(MICROVIDEO_META_MIMETYPE)) {
                Log.d(TAG, "stabilizationOn: " + stabilizationOn);
                if (stabilizationOn) {
                    Log.d(TAG, "selected motion track: " + i);
                    extractor.selectTrack(i);
                    bufferProcessor.setMotionTrackIndex(i);
                }
            }
        }

        // Set the MediaCodec callback to send buffer information to the corresponding blocking
        // queues (make sure the Android version is capable of supporting MediaCodec callbacks)
        if (Build.VERSION.SDK_INT < M) {
            Log.e("MotionPhotoReader", "Insufficient Android build version");
            return;
        }
        decoder.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                boolean result = availableInputBuffers.offer(index);
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec,
                                                int index,
                                                @NonNull MediaCodec.BufferInfo info) {
                Bundle bufferData = new Bundle();
                bufferData.putInt("BUFFER_INDEX", index);
                bufferData.putLong("TIMESTAMP_US", info.presentationTimeUs);
                boolean result = availableOutputBuffers.offer(bufferData);
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                Log.e(TAG, "Error while decoding", e);
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec,
                                              @NonNull MediaFormat format) {

            }
        }, renderHandler);

        // Set up OpenGL pipeline if the surface is not null
        if (surface != null) {
            outputSurface = new OutputSurface(renderHandler, motionPhotoInfo);
            outputSurface.setSurface(surface, surfaceWidth, surfaceHeight);
            decoder.configure(videoFormat, outputSurface.getDecodeSurface(), null, 0);
        } else {
            decoder.configure(videoFormat, null, null, 0);
        }
        decoder.start();

        // Configure the buffer processor
        bufferProcessor.configure(outputSurface, extractor, decoder, stabilizationOn);
    }

    /**
     * Shut down all resources allocated to the MotionPhotoReader instance.
     */
    public void close() {
        Log.d(TAG, "Closing motion photo reader");
        if (outputSurface != null) {
            outputSurface.release();
        }
        try {
            fileInputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            renderHandler.getLooper().quitSafely();
        } else {
            renderHandler.getLooper().quit();
        }
        decoder.release();
        extractor.release();
    }

    /**
     * Checks whether the Motion Photo video has a succeeding frame.
     * @return true if there is a frame, otherwise return false.
     */
    public boolean hasNextFrame() {
        // Read the next packet and check if it shows a full frame
        long sampleSize = extractor.getSampleSize();
        return (sampleSize >= 0);
    }

    /**
     * Advances the decoder and extractor by one frame.
     */
    public void nextFrame() {
        Bundle messageData = new Bundle();
        messageData.putInt("MESSAGE_KEY", MSG_NEXT_FRAME);
        bufferProcessor.process(messageData);
    }

    /**
     * Sets the decoder and extractor to the frame specified by the given timestamp.
     * @param timeUs The desired timestamp of the video.
     * @param mode The sync mode of the extractor.
     */
    public void seekTo(long timeUs, int mode) {
        Bundle messageData = new Bundle();
        messageData.putInt("MESSAGE_KEY", MSG_SEEK_TO_FRAME);
        messageData.putLong("TIME_US", timeUs);
        messageData.putInt("MODE", mode);
        bufferProcessor.process(messageData);
    }

    /**
     * Gets the current video timestamp at which the extractor is set (in microseconds).
     * @return a long representing the current timestamp of the video that the reader is at.
     */
    public long getCurrentTimestampUs() {
        return extractor.getSampleTime();
    }

    /**
     * @return a MotionPhotoInfo object containing motion photo metadata.
     */
    public MotionPhotoInfo getMotionPhotoInfo() throws IOException, XMPException {
        return MotionPhotoInfo.newInstance(file);
    }

    /**
     * @return a bitmap of the JPEG stored by the motion photo.
     */
    public Bitmap getMotionPhotoImageBitmap() throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return BitmapFactory.decodeFileDescriptor(input.getFD());
        }
    }
}
