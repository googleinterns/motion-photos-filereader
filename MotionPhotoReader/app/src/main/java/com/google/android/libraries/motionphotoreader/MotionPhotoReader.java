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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.os.Build.VERSION_CODES.M;
import static com.google.android.libraries.motionphotoreader.Constants.BOTTOM_LEFT;
import static com.google.android.libraries.motionphotoreader.Constants.BOTTOM_RIGHT;
import static com.google.android.libraries.motionphotoreader.Constants.FALLBACK_FRAME_DELTA_NS;
import static com.google.android.libraries.motionphotoreader.Constants.MICROVIDEO_META_MIMETYPE;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_IMAGE_META_MIMETYPE;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V1;
import static com.google.android.libraries.motionphotoreader.Constants.NUM_OF_STRIPS;
import static com.google.android.libraries.motionphotoreader.Constants.TOP_LEFT;
import static com.google.android.libraries.motionphotoreader.Constants.TOP_RIGHT;
import static com.google.android.libraries.motionphotoreader.Constants.US_TO_NS;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V2;
import static com.google.android.libraries.motionphotoreader.Constants.VIDEO_MIME_PREFIX;

/**
 * The MotionPhotoReader API allows developers to read through the video portion of Motion Photos in
 * a frame-by-frame manner.
 *
 * Each motion photo reader is meant to decode a single motion photo file. A reader must be closed
 * when it is no longer in use to prevent leaking resources. If a Surface is passed to the reader
 * to display the video to, then the reader renders the video via a separate OpenGL pipeline. This
 * pipeline is comprised of the OutputSurface.java and TextureRender.java classes.
 */

@RequiresApi(api = 28)
public class MotionPhotoReader {

    private static final String TAG = "MotionPhotoReaderClass";

    private final File file;
    private final Surface surface;
    private final boolean stabilizationOn;

    private MediaExtractor extractor;
    private MediaCodec decoder;
    private MediaFormat videoFormat;
    private FileInputStream fileInputStream;

    /**
     * Fields which are used to play the next frame or seek to a frame.
     */
    private int videoTrackIndex;
    private int motionTrackIndex;
    private List<HomographyMatrix> homographyList;
    private long prevRenderTimestampNs;
    private long prevTimestampUs;

    /**
     * The renderWorker and renderHandler are in charge of executing all calls relevant to rendering
     * and transforming the current frame (if stabilization is on).
     */
    private HandlerThread renderWorker;
    private Handler renderHandler;

    /** Available buffer queues **/
    private final BlockingQueue<Integer> inputBufferQueue;
    private final BlockingQueue<Bundle> outputBufferQueue;

    /** Fields passed onto OpenGL pipeline. */
    private OutputSurface outputSurface;
    private final int surfaceWidth;
    private final int surfaceHeight;
    private float scaleFactor = 1.0f;

    /** Flag used for debugging. */
    private final boolean testMode;

    /**
     * Standard MotionPhotoReader constructor.
     * @param file A motion photo file to open.
     * @param extractor A MediaExtractor for reading frame data and stabilization data (if needed).
     * @param surface The surface on which the final video should be displayed.
     * @param surfaceWidth The width of the surface to display.
     * @param surfaceHeight The height of the surface to display.
     * @param stabilizationOn If true, then the video should be stabilized (if possible). Otherwise,
     * we should not stabilize the video.
     * @param testMode If true, then we use mock video frame and stabilization data. This should
     * only be set to true if the reader is being used in a testing environment.
     * @param inputBufferQueue A blocking queue to hold available input buffer information.
     * @param outputBufferQueue A blocking queue to hold available output buffer information.
     */
    private MotionPhotoReader(File file,
                              MediaExtractor extractor,
                              Surface surface, int surfaceWidth, int surfaceHeight,
                              boolean stabilizationOn, boolean testMode,
                              BlockingQueue<Integer> inputBufferQueue,
                              BlockingQueue<Bundle> outputBufferQueue) {
        this.file = file;
        this.surface = surface;
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;
        this.stabilizationOn = stabilizationOn;
        this.testMode = testMode;
        this.extractor = extractor;
        this.inputBufferQueue = inputBufferQueue;
        this.outputBufferQueue = outputBufferQueue;

        // Set the stabilization matrices to the identity for each strip
        homographyList = new ArrayList<>();
        for (int i = 0; i < NUM_OF_STRIPS; i++) {
            homographyList.add(new HomographyMatrix());
        }
    }

    @VisibleForTesting
    static MotionPhotoReader open(File file, MediaExtractor extractor,
                                  Surface surface, int surfaceWidth, int surfaceHeight,
                                  boolean stabilizationOn,
                                  BlockingQueue<Integer> inputBufferQueue,
                                  BlockingQueue<Bundle> outputBufferQueue)
            throws IOException, XMPException {
        MotionPhotoInfo motionPhotoInfo = MotionPhotoInfo.newInstance(file);
        MotionPhotoReader reader = new MotionPhotoReader(
                file, extractor,
                surface, surfaceWidth, surfaceHeight,
                stabilizationOn, /* testMode = */ true,
                inputBufferQueue, outputBufferQueue
        );
        reader.startRenderThread(motionPhotoInfo, stabilizationOn);
        return reader;
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
                                         Surface surface, int surfaceWidth, int surfaceHeight,
                                         boolean stabilizationOn
    ) throws IOException, XMPException {
        MotionPhotoInfo motionPhotoInfo = MotionPhotoInfo.newInstance(file);
        MotionPhotoReader reader = new MotionPhotoReader(
                file,
                new MediaExtractor(),
                surface, surfaceWidth, surfaceHeight,
                stabilizationOn, /* testMode = */ false,
                /* inputBufferQueue = */ new LinkedBlockingQueue<>(),
                /* outputBufferQueue = */ new LinkedBlockingQueue<>()
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
     * @throws IOException if the extractor cannot read the motion photo file.
     */
    private void startRenderThread(MotionPhotoInfo motionPhotoInfo,
                                   boolean stabilizationOn) throws IOException {
        // Set up the render handler and thread
        renderWorker = new HandlerThread("renderHandler");
        renderWorker.start();
        renderHandler = new Handler(renderWorker.getLooper());

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

        // Set the motion track, and find an auto-crop if stabilization is on
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(MICROVIDEO_META_MIMETYPE)) {
                if (stabilizationOn) {
                    extractor.selectTrack(i);
                    motionTrackIndex = i;

                    // Find an auto-crop scale
                    float[] bottomLeft = Arrays.copyOf(BOTTOM_LEFT, BOTTOM_LEFT.length);
                    float[] bottomRight = Arrays.copyOf(BOTTOM_RIGHT, BOTTOM_RIGHT.length);
                    float[] topRight = Arrays.copyOf(TOP_RIGHT, TOP_RIGHT.length);
                    float[] topLeft = Arrays.copyOf(TOP_LEFT, TOP_LEFT.length);

                    int videoWidth = motionPhotoInfo.getWidth();
                    int videoHeight = motionPhotoInfo.getHeight();
                    while (extractor.getSampleSize() >= 0) {
                        ByteBuffer inputBuffer = ByteBuffer.allocateDirect((int) extractor.getSampleSize());
                        List<HomographyMatrix> newHomographyList =
                                MotionPhotoReaderUtils.getHomographies(extractor, inputBuffer);

                        // Update corner positions
                        bottomLeft = newHomographyList
                                .get(NUM_OF_STRIPS - 1)
                                .convertFromImageToGL(videoWidth, videoHeight)
                                .rightMultiplyBy(bottomLeft);
                        bottomRight = newHomographyList
                                .get(NUM_OF_STRIPS - 1)
                                .convertFromImageToGL(videoWidth, videoHeight)
                                .rightMultiplyBy(bottomRight);
                        topRight = newHomographyList
                                .get(0)
                                .convertFromImageToGL(videoWidth, videoHeight)
                                .rightMultiplyBy(topRight);
                        topLeft = newHomographyList
                                .get(0)
                                .convertFromImageToGL(videoWidth, videoHeight)
                                .rightMultiplyBy(topLeft);

                        extractor.advance();
                    }
                    Log.d(TAG, "\nBottom left : " + Arrays.toString(bottomLeft)
                            + "\nBottom right: " + Arrays.toString(bottomRight)
                            + "\nTop right   : " + Arrays.toString(topRight)
                            + "\nTop left    : " + Arrays.toString(topLeft));

                    // Calculate furthest distance that a corner moves
                    float bottomLeftDist = HomographyMatrix.distanceBetween(bottomLeft, BOTTOM_LEFT);
                    float bottomRightDist = HomographyMatrix.distanceBetween(bottomRight, BOTTOM_RIGHT);
                    float topRightDist = HomographyMatrix.distanceBetween(topRight, TOP_RIGHT);
                    float topLeftDist = HomographyMatrix.distanceBetween(topLeft, TOP_LEFT);
                    float maxDist = Math.max(Math.max(bottomLeftDist, bottomRightDist), Math.max(topRightDist, topLeftDist));
                    scaleFactor = 1.0f + 1.5f * maxDist;
                }

                // Reset the extractor to the beginning
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                break;
            }
        }

        // Find the appropriate tracks (motion and video) and configure them
        Log.d(TAG, "Stabilization on: " + stabilizationOn);
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            // Set the video track (which should be the first video track) and create an
            // appropriate media decoder
            if (mime.startsWith(VIDEO_MIME_PREFIX)) {
                extractor.selectTrack(i);
                Log.d(TAG, "Selected video track: " + i);
                videoTrackIndex = i;
                videoFormat = format;
                decoder = MediaCodec.createDecoderByType(mime);
                break;
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
                boolean result = inputBufferQueue.offer(index);
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec,
                                                int index,
                                                @NonNull MediaCodec.BufferInfo info) {
                Bundle bufferData = new Bundle();
                bufferData.putInt("BUFFER_INDEX", index);
                bufferData.putLong("TIMESTAMP_US", info.presentationTimeUs);
                boolean result = outputBufferQueue.offer(bufferData);
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

        // Set up OpenGL pipeline if the surface is not null, otherwise we skip the OpenGL rendering
        // steps altogether
        if (surface != null) {
            outputSurface = new OutputSurface(renderHandler, motionPhotoInfo);
            outputSurface.setSurface(surface, surfaceWidth, surfaceHeight, scaleFactor);
            decoder.configure(videoFormat, outputSurface.getDecodeSurface(), null, 0);
        } else {
            decoder.configure(videoFormat, null, null, 0);
        }
        decoder.start();
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
        Bundle bufferData;
        Integer bufferIndex = -1;
        long timestampUs = -1;

        // We only want to render the final frame if all steps are successful, so this field will
        // be set to false in the event that any step fails
        boolean doRender = true;

        // Loop through the tracks on the media extractor (note that we only visit the motion track
        // if we wish to stabilize the video)
        boolean videoTrackVisited = false;
        boolean motionTrackVisited = !stabilizationOn;
        while (!(videoTrackVisited && motionTrackVisited)) {
            int trackIndex = extractor.getSampleTrackIndex();
            ByteBuffer inputBuffer;
            if (trackIndex == videoTrackIndex) {
                // Get the next available input buffer and read frame data
                bufferIndex = MotionPhotoReaderUtils.getInputBuffer(inputBufferQueue);
                if (bufferIndex == null) {
                    doRender = false;
                    break;
                }

                inputBuffer = decoder.getInputBuffer(bufferIndex);
                if (inputBuffer == null) {
                    doRender = false;
                    break;
                }
                MotionPhotoReaderUtils.readFromVideoTrack(
                        extractor,
                        decoder,
                        inputBuffer,
                        bufferIndex
                );

                // Get the next available output buffer and release frame data
                bufferData = MotionPhotoReaderUtils.getOutputBuffer(outputBufferQueue);
                if (bufferData == null) {
                    doRender = false;
                    break;
                }
                timestampUs = bufferData.getLong("TIMESTAMP_US");
                bufferIndex = bufferData.getInt("BUFFER_INDEX");
                videoTrackVisited = true;
            } else if (trackIndex == motionTrackIndex) {
                if (!testMode) {
                    // Get stabilization data from the high resolution extractor (the list of
                    // homographies will be empty if the frame has already been stabilized)
                    inputBuffer = ByteBuffer.allocateDirect((int) extractor.getSampleSize());
                    List<HomographyMatrix> newHomographyList =
                            MotionPhotoReaderUtils.getHomographies(extractor, inputBuffer);

                    // Multiply previous stabilization matrices by new stabilization matrices
                    List<HomographyMatrix> tempHomographyList = new ArrayList<>();
                    for (int i = 0; i < newHomographyList.size(); i++) {
                        HomographyMatrix newStripMatrix = homographyList
                                .get(i)
                                .leftMultiplyBy(newHomographyList.get(i));
                        tempHomographyList.add(newStripMatrix);
                    }
                    homographyList = tempHomographyList;
                }
                motionTrackVisited = true;
            } else if (trackIndex == -1) {
                // If the track index is -1, then the extractor has no frame data to read,
                // so we don't want to render anything
                doRender = false;
                break;
            } else {
                throw new RuntimeException("Unexpected track index: " + trackIndex);
            }
            extractor.advance();
        }

        if (doRender) {
            // Compute the delay in render timestamp between the current frame and the previous
            // frame.
            long frameDeltaNs = (timestampUs - prevTimestampUs) * US_TO_NS;
            if (frameDeltaNs <= 0) {
                frameDeltaNs = FALLBACK_FRAME_DELTA_NS;
            }
            // Set the previous timestamp ("zero out" the timestamps) to the current system
            // timestamp if it has not been set yet (i.e. equals zero).
            long renderTimestampNs;
            long currentTimestampNs = System.nanoTime();
            if (prevRenderTimestampNs == 0) {
                renderTimestampNs = currentTimestampNs + frameDeltaNs;
            } else {
                renderTimestampNs = prevRenderTimestampNs + frameDeltaNs;
            }
            // Rebase the render timestamp if it has drifted too far behind
            if (renderTimestampNs < currentTimestampNs) {
                renderTimestampNs = currentTimestampNs + frameDeltaNs;
            }
            decoder.releaseOutputBuffer(bufferIndex, /* render = */ true);
            prevTimestampUs = timestampUs;
            prevRenderTimestampNs = renderTimestampNs;

            // Wait for the image and render it after it arrives
            if (outputSurface != null) {
                outputSurface.awaitNewImage();
                outputSurface.drawImage(homographyList, renderTimestampNs);
            }
        }
    }

    /**
     * Sets the decoder and extractor to the frame specified by the given timestamp.
     * @param seekTimestampUs The desired timestamp of the video.
     * @param mode The sync mode of the extractor.
     */
    public void seekTo(long seekTimestampUs, int mode) {
        // Seek extractor to correct location
        extractor.seekTo(seekTimestampUs, mode);

        Bundle bufferData;
        Integer bufferIndex = -1;
        long timestampUs = -1;

        // We only want to render the final frame if all steps are successful, so this field will
        // be set to false in the event that any step fails
        boolean doRender = true;

        // Loop through the tracks on the media extractor (note that we only visit the motion track
        // if we wish to stabilize the video)
        boolean videoTrackVisited = false;
        boolean motionTrackVisited = !stabilizationOn;
        while (!(videoTrackVisited && motionTrackVisited)) {
            int trackIndex = extractor.getSampleTrackIndex();
            ByteBuffer inputBuffer;
            if (trackIndex == videoTrackIndex) {
                // Get the next available input buffer and read frame data
                bufferIndex = MotionPhotoReaderUtils.getInputBuffer(inputBufferQueue);
                if (bufferIndex == null) {
                    doRender = false;
                    break;
                }

                inputBuffer = decoder.getInputBuffer(bufferIndex);
                if (inputBuffer == null) {
                    doRender = false;
                    break;
                }
                MotionPhotoReaderUtils.readFromVideoTrack(
                        extractor,
                        decoder,
                        inputBuffer,
                        bufferIndex
                );

                // Get the next available output buffer and release frame data
                bufferData = MotionPhotoReaderUtils.getOutputBuffer(outputBufferQueue);
                if (bufferData == null) {
                    doRender = false;
                    break;
                }
                timestampUs = bufferData.getLong("TIMESTAMP_US");
                bufferIndex = bufferData.getInt("BUFFER_INDEX");
                videoTrackVisited = true;
            } else if (trackIndex == motionTrackIndex) {
                // Set the stabilization matrices to the appropriate scaling matrix for each strip
                homographyList = new ArrayList<>();
                for (int i = 0; i < NUM_OF_STRIPS; i++) {
                    homographyList.add(new HomographyMatrix());
                }
                motionTrackVisited = true;
            } else if (trackIndex == -1) {
                doRender = false;
                break;
            } else {
                throw new RuntimeException("Unexpected track index: " + trackIndex);
            }
            extractor.advance();
        }

        if (doRender) {
            long renderTimestampNs = prevRenderTimestampNs;
            decoder.releaseOutputBuffer(bufferIndex, /* render = */ true);

            // Reset the previous timestamp and previous render timestamp
            prevTimestampUs = timestampUs;

            // Wait for the image and render it after it arrives
            if (outputSurface != null) {
                outputSurface.awaitNewImage();
                outputSurface.drawImage(homographyList, renderTimestampNs);
            }
        }
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
     * @throws IOException if the given file cannot be found.
     * @throws XMPException if attempting to parse invalid XMP data.
     */
    public MotionPhotoInfo getMotionPhotoInfo() throws IOException, XMPException {
        return MotionPhotoInfo.newInstance(file);
    }

    /**
     * @return a bitmap of the JPEG stored by the motion photo.
     * @throws IOException if the BitmapFactory cannot decode the given file.
     */
    public Bitmap getMotionPhotoImageBitmap() throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return BitmapFactory.decodeFileDescriptor(input.getFD());
        }
    }
}
