package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * A processor specifically used to handle nextFrame() and seekTo() calls from MotionPhotoReader.
 */
class BufferProcessor {
    private static final String TAG = "BufferProcessor";

    private static final long TIMEOUT_US = 1000L;
    private static final long US_TO_NS = 1000L;
    private static final long FALLBACK_FRAME_DELTA_NS = 1_000_000_000L / 30;

    private long prevRenderTimestampNs;
    private long prevTimestampUs;

    /**
     * Fields shared with motion photo reader.
     */
    private final OutputSurface outputSurface;
    private final MediaExtractor lowResExtractor;
    private final MediaCodec lowResDecoder;
    private final BlockingQueue<Integer> availableInputBuffers;
    private final BlockingQueue<Bundle> availableOutputBuffers;

    /**
     * Constructor for setting up a buffer processor from a motion photo reader.
     * @param lowResExtractor The low resolution channel MediaExtractor from the motion photo reader.
     * @param lowResDecoder The low resolution MediaCodec from the motion photo reader.
     * @param availableInputBuffers The queue of available input buffers.
     * @param availableOutputBuffers The queue of available output buffers.
     */
    public BufferProcessor(OutputSurface outputSurface,
                           MediaExtractor lowResExtractor,
                           MediaCodec lowResDecoder,
                           BlockingQueue<Integer> availableInputBuffers,
                           BlockingQueue<Bundle> availableOutputBuffers) {
        this.outputSurface = outputSurface;
        this.lowResExtractor = lowResExtractor;
        this.lowResDecoder = lowResDecoder;
        this.availableInputBuffers = availableInputBuffers;
        this.availableOutputBuffers = availableOutputBuffers;
        prevRenderTimestampNs = 0;
        prevTimestampUs = 0;
    }

    /**
     * Retrieve the index of the next available input buffer.
     * @return the index of the next available input buffer, or -1 if the poll call results in a
     * timeout.
     */
    @RequiresApi(api = LOLLIPOP)
    private int getAvailableInputBufferIndex() {
        int bufferIndex = -1;
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
    private void readFromExtractor(ByteBuffer inputBuffer, int bufferIndex) {
        int sampleSize = lowResExtractor.readSampleData(inputBuffer, 0);
        if (sampleSize < 0) {
            lowResDecoder.queueInputBuffer(
                    bufferIndex,
                    /* offset = */ 0,
                    /* size = */ 0,
                    /* presentationTimeUs = */ 0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
            );
        } else {
            lowResDecoder.queueInputBuffer(
                    bufferIndex,
                    /* offset = */ 0,
                    sampleSize,
                    lowResExtractor.getSampleTime(),
                    /* flags = */ 0
            );
            lowResExtractor.advance();
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
    @RequiresApi(api = LOLLIPOP)
    public void process(Bundle messageData) {
        int key = messageData.getInt("MESSAGE_KEY");
        Bundle bufferData;
        int bufferIndex;
        long timestampUs;
        switch (key) {
            case MotionPhotoReader.MSG_NEXT_FRAME:
                // Get the next available input buffer and read frame data
                // TODO: Consider the case when this call times out and returns -1
                bufferIndex = getAvailableInputBufferIndex();
                // TODO: Consider the case when this call returns null
                ByteBuffer inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                readFromExtractor(inputBuffer, bufferIndex);

                // Get the next available output buffer and release frame data
                // TODO: Consider the case when this call times out and returns null
                bufferData = getAvailableOutputBufferData();
                timestampUs = bufferData.getLong("TIMESTAMP_US");
                bufferIndex = bufferData.getInt("BUFFER_INDEX");

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
                lowResDecoder.releaseOutputBuffer(bufferIndex, renderTimestampNs);
                prevTimestampUs = timestampUs;
                prevRenderTimestampNs = renderTimestampNs;

                // Wait for the image and render it after it arrives
                if (outputSurface != null) {
                    outputSurface.awaitNewImage();
                    outputSurface.drawImage();
                }
                break;

            case MotionPhotoReader.MSG_SEEK_TO_FRAME:
                // TODO: Same considerations as MSG_NEXT_FRAME
                // Get the next available input buffer and read frame data
                lowResExtractor.seekTo(messageData.getLong("TIME_US"), messageData.getInt("MODE"));

                bufferIndex = getAvailableInputBufferIndex();
                inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                readFromExtractor(inputBuffer, bufferIndex);

                // Get the next available output buffer and release frame data
                bufferData = getAvailableOutputBufferData();
                timestampUs = bufferData.getLong("TIMESTAMP_US");
                bufferIndex = bufferData.getInt("BUFFER_INDEX");

                renderTimestampNs = prevRenderTimestampNs;
                lowResDecoder.releaseOutputBuffer(bufferIndex, renderTimestampNs);

                // Reset the previous timestamp and previous render timestamp
                prevTimestampUs = timestampUs;

                // Wait for the image and render it after it arrives
                if (outputSurface != null) {
                    outputSurface.awaitNewImage();
                    outputSurface.drawImage();
                }
                break;

            default:
                throw new IllegalStateException("Invalid message key provided: " + key);
        }
    }
}
