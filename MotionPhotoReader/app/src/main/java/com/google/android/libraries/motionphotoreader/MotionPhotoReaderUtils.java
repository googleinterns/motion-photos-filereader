package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.google.protobuf.InvalidProtocolBufferException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

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
class MotionPhotoReaderUtils {
    private static final String TAG = "MotionPhotoReaderUtils";

//    @VisibleForTesting
//    MotionPhotoReaderUtils(OutputSurface outputSurface,
//                           MediaExtractor extractor,
//                           MediaCodec decoder,
//                           boolean stabilizationOn,
//                           int videoTrackIndex,
//                           int motionTrackIndex,
//                           boolean testMode,
//                           BlockingQueue<Integer> availableInputBuffers,
//                           BlockingQueue<Bundle> availableOutputBuffers) {
//        this.outputSurface = outputSurface;
//        this.extractor = extractor;
//        this.decoder = decoder;
//        this.videoTrackIndex = videoTrackIndex;
//        this.motionTrackIndex = motionTrackIndex;
//        this.testMode = testMode;
//        this.availableInputBuffers = availableInputBuffers;
//        this.availableOutputBuffers = availableOutputBuffers;
//        prevRenderTimestampNs = 0;
//        prevTimestampUs = 0;
//        this.stabilizationOn = stabilizationOn;
//
//        // Set the stabilization matrices to the identity for each strip
//        homographyList = new ArrayList<>();
//        for (int i = 0; i < NUM_OF_STRIPS; i++) {
//            homographyList.add(new HomographyMatrix());
//        }
//    }

    /**
     * Retrieve the index of the next available input buffer.
     * @return the index of the next available input buffer, or null if the poll call results in a
     * timeout.
     */
    public static Integer getInputBuffer(BlockingQueue<Integer> availableInputBuffers) {
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
    public static void readFromVideoTrack(MediaExtractor extractor, MediaCodec decoder,
                                          ByteBuffer inputBuffer, int bufferIndex) {
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
    public static Bundle getOutputBuffer(BlockingQueue<Bundle> availableOutputBuffers) {
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
    public static List<HomographyMatrix> getHomographies(MediaExtractor extractor,
                                                         ByteBuffer inputBuffer) {
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
}
