package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.protobuf.InvalidProtocolBufferException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.google.android.libraries.motionphotoreader.Constants.NUM_OF_STRIPS;
import static com.google.android.libraries.motionphotoreader.Constants.TIMEOUT_US;

/**
 * A collection of helper methods used by MotionPhotoReader to access buffers and read track data.
 */
@RequiresApi(api = 28)
class MotionPhotoReaderUtils {
    private static final String TAG = "MotionPhotoReaderUtils";

    /**
     * Retrieve the index of the next available input buffer.
     * @param availableInputBuffers A blocking queue containing the indices of free input buffers.
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
     * @param extractor An extractor connected to the data source from which we want to read.
     * @param decoder A decoder used to decode samples read from the extractor.
     * @param inputBuffer The input buffer to read samples to and queue to the MediaCodec.
     * @param bufferIndex The index of the input buffer.
     */
    public static void readFromVideoTrack(MediaExtractor extractor, MediaCodec decoder,
                                          ByteBuffer inputBuffer, int bufferIndex) {
        int sampleSize = extractor.readSampleData(inputBuffer, /* offset = */ 0);
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
     * @param availableOutputBuffers A blocking queue of Bundle objects containing information about
     * free output buffers.
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
     * Retrieve the stabilization homographies from the motion track. If the frame has already been
     * stabilized, then return an empty list.
     * @param extractor The extractor connected to the track containing the stabilization data.
     * @param inputBuffer A free input buffer for the extractor to read data into.
     * @return a List of HomographyMatrix objects.
     */
    public static List<HomographyMatrix> getHomographies(MediaExtractor extractor,
                                                         ByteBuffer inputBuffer,
                                                         List<Float> prevHomographyDataList) {
        List<HomographyMatrix> homographyList = new ArrayList<>();
        boolean protocolBufferIsAvailable = false;
        int sampleSize = extractor.readSampleData(inputBuffer, 0);
        if (sampleSize >= 0) {
            // Deserialize data
            Stabilization.Data stabilizationData = null;
            try {
                stabilizationData = Stabilization.Data.parseFrom(inputBuffer);
                if (stabilizationData == null) {
                    Log.e(TAG, "Protocol buffer is null");
                } else {
                    protocolBufferIsAvailable = true;
                }
            } catch (InvalidProtocolBufferException e) {
                Log.e(TAG, "Could not parse from protocol buffer");
            }

            // If the protocol buffer was not null, then extract the homography data list from the
            // protocol buffer data; otherwise, just use the homography data list from the most
            // recent non-null protocol buffer
            List<Float> homographyDataList = protocolBufferIsAvailable ?
                    stabilizationData.getMotionHomographyDataList() : prevHomographyDataList;

            // Add homography for each strip to the homography list, only if the motion data type
            // for this frame is not MOTION_TYPE_STABILIZATION (this flag indicates the frame has
            // already been stabilized)
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

            // Update the most recent homography data list
            prevHomographyDataList = homographyDataList;
        }

        return homographyList;
    }
}
