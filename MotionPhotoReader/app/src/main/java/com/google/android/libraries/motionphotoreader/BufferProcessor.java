package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.google.protobuf.InvalidProtocolBufferException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.google.android.libraries.motionphotoreader.Constants.FALLBACK_FRAME_DELTA_NS;
import static com.google.android.libraries.motionphotoreader.Constants.NUM_OF_STRIPS;
import static com.google.android.libraries.motionphotoreader.Constants.TIMEOUT_US;
import static com.google.android.libraries.motionphotoreader.Constants.US_TO_NS;

/**
 * A processor specifically used to handle nextFrame() and seekTo() calls from MotionPhotoReader.
 *
 * In a call to nextFrame(), the buffer processor decodes the next frame an output buffer and
 * renders it to a surface texture. The buffer processor also extracts a set of stabilization
 * homographies for the next frame. The rendered frame and the stabilization homographies are passed
 * to a texture renderer which stabilizes the frame and draws it to the final output surface.
 *
 * In a call to seekTo(), the buffer processor decodes the frame specified by the seeking timestamp
 * and renders it to the surface texture. The texture renderer then displays the frame to the output
 * surface. No stabilization is needed, as this frame will be used as the base frame against which
 * subsequent frames are stabilized.
 */
@RequiresApi(api = 28)
class BufferProcessor {
    private static final String TAG = "BufferProcessor";

    private long prevRenderTimestampNs;
    private long prevTimestampUs;

    /**
     * Fields used for extracting stabilization data.
     */
    private int videoTrackIndex;
    private int motionTrackIndex;
    private List<HomographyMatrix> homographyList;
    private boolean stabilizationOn;

    /**
     * Fields shared with motion photo reader.
     */
    private OutputSurface outputSurface;
    private MediaExtractor extractor;
    private MediaCodec decoder;
    private final BlockingQueue<Integer> availableInputBuffers;
    private final BlockingQueue<Bundle> availableOutputBuffers;

    private boolean testMode;

    /**
     * Constructor for setting up a buffer processor from a motion photo reader.
     * @param availableInputBuffers The queue of available input buffers.
     * @param availableOutputBuffers The queue of available output buffers.
     */
    public BufferProcessor(BlockingQueue<Integer> availableInputBuffers,
                           BlockingQueue<Bundle> availableOutputBuffers) {
        this.availableInputBuffers = availableInputBuffers;
        this.availableOutputBuffers = availableOutputBuffers;
        prevRenderTimestampNs = 0;
        prevTimestampUs = 0;

        // Set the stabilization matrices to the identity for each strip
        homographyList = new ArrayList<>();
        for (int i = 0; i < NUM_OF_STRIPS; i++) {
            homographyList.add(new HomographyMatrix());
        }
    }

    @VisibleForTesting
    BufferProcessor(OutputSurface outputSurface,
                           MediaExtractor extractor,
                           MediaCodec decoder,
                           boolean stabilizationOn,
                           int videoTrackIndex,
                           int motionTrackIndex,
                           boolean testMode,
                           BlockingQueue<Integer> availableInputBuffers,
                           BlockingQueue<Bundle> availableOutputBuffers) {
        this.outputSurface = outputSurface;
        this.extractor = extractor;
        this.decoder = decoder;
        this.videoTrackIndex = videoTrackIndex;
        this.motionTrackIndex = motionTrackIndex;
        this.testMode = testMode;
        this.availableInputBuffers = availableInputBuffers;
        this.availableOutputBuffers = availableOutputBuffers;
        prevRenderTimestampNs = 0;
        prevTimestampUs = 0;
        this.stabilizationOn = stabilizationOn;

        // Set the stabilization matrices to the identity for each strip
        homographyList = new ArrayList<>();
        for (int i = 0; i < NUM_OF_STRIPS; i++) {
            homographyList.add(new HomographyMatrix());
        }
    }

    /**
     * Configure the buffer processor to access the fields of a certain motion photo reader.
     * @param outputSurface The output surface which connects the buffer processor and the motion
     * photo reader to the OpenGL pipeline.
     * @param extractor The MediaExtractor from the motion photo reader that reads the video track.
     * @param decoder The low resolution MediaCodec from the motion photo reader.
     * @param stabilizationOn If true, the buffer processor will also extract stabilization data.
     */
    public void configure(OutputSurface outputSurface,
                           MediaExtractor extractor,
                           MediaCodec decoder,
                           boolean stabilizationOn) {
        this.outputSurface = outputSurface;
        this.extractor = extractor;
        this.decoder = decoder;
        this.stabilizationOn = stabilizationOn;
    }

    /**
     * Indicate what track index represents the video track for the extractor. Must be set after an
     * instance is constructed.
     * @param videoTrackIndex The index of the video track.
     */
    public void setVideoTrackIndex(int videoTrackIndex) {
        this.videoTrackIndex = videoTrackIndex;
    }

    /**
     * Indicate what track index represents the motion track for the extractor. Must be set after an
     * instance is constructed.
     * @param motionTrackIndex The index of the video track.
     */
    public void setMotionTrackIndex(int motionTrackIndex) {
        this.motionTrackIndex = motionTrackIndex;
    }

    /**
     * Retrieve the index of the next available input buffer.
     * @return the index of the next available input buffer, or -1 if the poll call results in a
     * timeout.
     */
    public int getAvailableInputBufferIndex() {
        Integer bufferIndex = -1;
        try {
            bufferIndex = availableInputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "No input buffer available", e);
        }
        return bufferIndex;
    }

    /**
     * Read the next data sample from the motion photo to a given input buffer and advance the
     * extractor.
     * @param inputBuffer The input buffer to read samples to and queue to the MediaCodec.
     * @param bufferIndex The index of the input buffer.
     */
    private void readFromVideoTrack(ByteBuffer inputBuffer, int bufferIndex) {
        int sampleSize = extractor.readSampleData(inputBuffer, 0);
        if (sampleSize < 0) {
            decoder.queueInputBuffer(
                    bufferIndex,
                    /* offset = */ 0,
                    /* size = */ 0,
                    /* presentationTimeUs = */ 0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
            );
        } else {
            decoder.queueInputBuffer(
                    bufferIndex,
                    /* offset = */ 0,
                    sampleSize,
                    extractor.getSampleTime(),
                    /* flags = */ 0
            );
        }
    }

    /**
     * Retrieve the information of the next available output buffer.
     * @return the bundle containing the information of the next available output buffer, or null if
     * the poll call results in a timeout.
     */
    private Bundle getAvailableOutputBufferData() {
        Bundle bufferData = null;
        try {
            bufferData = availableOutputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "No output buffer available", e);
        }
        return bufferData;
    }

    /**
     * Retrieve the stabilization homographies from the motion track.
     * @return a List of HomographyMatrix objects.
     */
    private List<HomographyMatrix> getHomographies(ByteBuffer inputBuffer) {
        List<HomographyMatrix> homographyList = new ArrayList<>();
        int sampleSize = extractor.readSampleData(inputBuffer, 0);
        if (sampleSize >= 0) {
            // Deserialize data
            Stabilization.Data stabilizationData = null;
            try {
                stabilizationData = Stabilization.Data.parseFrom(inputBuffer);
                if (stabilizationData == null) {
                    throw new RuntimeException("Protocol buffer is null");
                }
            } catch (InvalidProtocolBufferException e) {
                Log.e(TAG, "Could not parse from protocol buffer");
            }

            // Add homography for each strip to the homography list, only if the motion data type
            // for this frame is not MOTION_TYPE_STABILIZATION (this flag indicates the frame has
            // already been stabilized)
            List<Float> homographyDataList = stabilizationData.getMotionHomographyDataList();
            if (stabilizationData.getMotionDataType() !=
                    Stabilization.Data.MotionDataType.MOTION_TYPE_STABILIZATION) {
                for (int i = 0; i < NUM_OF_STRIPS; i++) {
                    homographyList.add(
                            new HomographyMatrix(
                                    homographyDataList.subList(9 * i, 9 * (i + 1))
                            )
                    );
                }
            }
        }
        return homographyList;
    }

    /**
     * Handle calls to nextFrame() and seekTo() by the MotionPhotoReader.
     * The fields in the message bundle are:
     *     - MESSAGE_KEY: an int indicating which method call this message comes from.
     *     - TIME_US: a long containing the timestamp to seek to (seekTo() calls only)
     *     in microseconds.
     *     - MODE: an int containing the seeking mode.
     * The fields in the output buffer data bundle are:
     *     - TIMESTAMP_US: a long containing the timestamp assigned to the output buffer,
     *     in microseconds.
     *     - BUFFER_INDEX: an int containing the index of the output buffer.
     * @param messageData A Bundle containing relevant fields from a call to nextFrame(), seekTo().
     */
    public void process(Bundle messageData) {
        int key = messageData.getInt("MESSAGE_KEY");
        Bundle bufferData;
        int bufferIndex = -1;
        long timestampUs = -1;
        boolean doRender = true;
        switch (key) {
            case MotionPhotoReader.MSG_NEXT_FRAME:
                boolean videoTrackVisited = false;
                boolean motionTrackVisited = !stabilizationOn;
                while (!(videoTrackVisited && motionTrackVisited)) {
                    int trackIndex = extractor.getSampleTrackIndex();
                    ByteBuffer inputBuffer;
                    if (trackIndex == videoTrackIndex) {
                        // Get the next available input buffer and read frame data
                        bufferIndex = getAvailableInputBufferIndex();
                        if (bufferIndex == -1) {
                            doRender = false;
                            break;
                        }
                        // TODO: Consider the case when this call returns null
                        inputBuffer = decoder.getInputBuffer(bufferIndex);
                        readFromVideoTrack(inputBuffer, bufferIndex);

                        // Get the next available output buffer and release frame data
                        // TODO: Consider the case when this call times out and returns null
                        bufferData = getAvailableOutputBufferData();
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
                            List<HomographyMatrix> newHomographyList = getHomographies(inputBuffer);

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
                break;

            case MotionPhotoReader.MSG_SEEK_TO_FRAME:
                // Seek extractor to correct location
                extractor.seekTo(messageData.getLong("TIME_US"), messageData.getInt("MODE"));
                videoTrackVisited = false;
                motionTrackVisited = !stabilizationOn;
                while (!(videoTrackVisited && motionTrackVisited)) {
                    int trackIndex = extractor.getSampleTrackIndex();
                    ByteBuffer inputBuffer;
                    if (trackIndex == videoTrackIndex) {
                        // Get the next available input buffer and read frame data
                        bufferIndex = getAvailableInputBufferIndex();
                        if (bufferIndex == -1) {
                            doRender = false;
                            break;
                        }
                        // TODO: Consider the case when this call returns null
                        inputBuffer = decoder.getInputBuffer(bufferIndex);
                        readFromVideoTrack(inputBuffer, bufferIndex);

                        // Get the next available output buffer and release frame data
                        // TODO: Consider the case when this call times out and returns null
                        bufferData = getAvailableOutputBufferData();
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
                break;

            default:
                throw new IllegalStateException("Invalid message key provided: " + key);
        }
    }
}
