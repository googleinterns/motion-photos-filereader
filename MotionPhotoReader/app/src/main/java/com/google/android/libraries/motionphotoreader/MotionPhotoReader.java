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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.os.Build.VERSION_CODES.M;
import static com.google.android.libraries.motionphotoreader.Constants.FALLBACK_FRAME_DELTA_NS;
import static com.google.android.libraries.motionphotoreader.Constants.MICROVIDEO_META_MIMETYPE;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_IMAGE_META_MIMETYPE;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V1;
import static com.google.android.libraries.motionphotoreader.Constants.NUM_OF_STRIPS;
import static com.google.android.libraries.motionphotoreader.Constants.US_TO_NS;
import static com.google.android.libraries.motionphotoreader.Constants.VIDEO_MIME_PREFIX;

/**
 * The MotionPhotoReader API allows developers to read through the video portion of Motion Photos in
 * a frame-by-frame manner.
 */

@RequiresApi(api = 28)
public class MotionPhotoReader {

    private static final String TAG = "MotionPhotoReader";

    private final File file;
    private final Surface surface;
    private final boolean stabilizationOn;

    private int videoTrackIndex;
    private int motionTrackIndex;
    private List<HomographyMatrix> homographyList;
    private long prevRenderTimestampNs;
    private long prevTimestampUs;

    private MediaExtractor extractor;
    private MediaCodec decoder;
    private MediaFormat videoFormat;
    private FileInputStream fileInputStream;

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
    private final BlockingQueue<Integer> inputBufferQueue;
    private final BlockingQueue<Bundle> outputBufferQueue;

    /** Fields passed onto OpenGL pipeline. */
    private OutputSurface outputSurface;
    private final int surfaceWidth;
    private final int surfaceHeight;

    /** Flag used for debugging. */
    private final boolean testMode;

    /**
     * Standard MotionPhotoReader constructor.
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
     */
    private void startRenderThread(MotionPhotoInfo motionPhotoInfo, boolean stabilizationOn) throws IOException {
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
        // 1. Check if the bit exists
        //   a. If the bit exists, set stabilizationOn to true if it was originally true
        //   b. If the bit does not exist, override stabilizationOn and set it to false
        // 2. If the bit does not exist, then override stabilizationOn and set it to false
        int version = motionPhotoInfo.getVersion();
        if (version == MOTION_PHOTO_V1) {
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                assert mime != null;
                if (mime.startsWith(MOTION_PHOTO_IMAGE_META_MIMETYPE)) {
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
                videoTrackIndex = i;
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
                    motionTrackIndex = i;
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

        // Set up OpenGL pipeline if the surface is not null
        if (surface != null) {
            outputSurface = new OutputSurface(renderHandler, motionPhotoInfo);
            outputSurface.setSurface(surface, surfaceWidth, surfaceHeight);
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
        boolean doRender = true;
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
                // TODO: Consider the case when this call returns null
                inputBuffer = decoder.getInputBuffer(bufferIndex);
                MotionPhotoReaderUtils.readFromVideoTrack(
                        extractor,
                        decoder,
                        inputBuffer,
                        bufferIndex
                );

                // Get the next available output buffer and release frame data
                // TODO: Consider the case when this call times out and returns null
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
                    // Get stabilization data from the high resolution extractor
                    inputBuffer = ByteBuffer.allocateDirect((int) extractor.getSampleSize());
                    List<HomographyMatrix> newHomographyList =
                            MotionPhotoReaderUtils.getHomographies(extractor, inputBuffer);

                    // Multiply previous stabilization matrices by new stabilization matrices
                    // (Assume MOTION_TYPE_INTERFRAME for now)
                    List<HomographyMatrix> tempHomographyList = new ArrayList<>();
                    for (int i = 0; i < newHomographyList.size(); i++) {
                        if (stabilizationOn) {
                            HomographyMatrix newStripMatrix = homographyList
                                    .get(i)
                                    .leftMultiplyBy(newHomographyList.get(i));
                            tempHomographyList.add(newStripMatrix);
                        } else {
                            tempHomographyList.add(new HomographyMatrix());
                        }
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
        boolean doRender = true;
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
                // TODO: Consider the case when this call returns null
                inputBuffer = decoder.getInputBuffer(bufferIndex);
                MotionPhotoReaderUtils.readFromVideoTrack(
                        extractor,
                        decoder,
                        inputBuffer,
                        bufferIndex
                );

                // Get the next available output buffer and release frame data
                // TODO: Consider the case when this call times out and returns null
                bufferData = MotionPhotoReaderUtils.getOutputBuffer(outputBufferQueue);
                if (bufferData == null) {
                    doRender = false;
                    break;
                }
                timestampUs = bufferData.getLong("TIMESTAMP_US");
                bufferIndex = bufferData.getInt("BUFFER_INDEX");
                videoTrackVisited = true;
            } else if (trackIndex == motionTrackIndex) {
                // Set the stabilization matrices to the identity for each strip
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
