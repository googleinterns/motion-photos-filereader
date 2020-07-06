package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * A handler meant specifically for handling messages received from a motion photo reader.
 */
class BufferHandler extends Handler {
    private static final long TIMEOUT_US = 1000L;
    private static final long US_TO_NS = 1000L;

    /**
     * Fields shared with motion photo reader.
     */
    private final MediaExtractor lowResExtractor;
    private final MediaCodec lowResDecoder;
    private final BlockingQueue<Integer> availableInputBuffers;
    private final BlockingQueue<Bundle> availableOutputBuffers;

    public BufferHandler(Looper looper, MediaExtractor lowResExtractor, MediaCodec lowResDecoder,
                         BlockingQueue<Integer> availableInputBuffers, BlockingQueue<Bundle> availableOutputBuffers) {
        super(looper);
        this.lowResExtractor = lowResExtractor;
        this.lowResDecoder = lowResDecoder;
        this.availableInputBuffers = availableInputBuffers;
        this.availableOutputBuffers = availableOutputBuffers;
    }

    @RequiresApi(api = LOLLIPOP)
    private int getAvailableInputBufferIndex() {
        int bufferIndex = -1;
        try {
            bufferIndex = availableInputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bufferIndex;
    }

    private void readFromExtractor(ByteBuffer inputBuffer, int bufferIndex) {
        int sampleSize = lowResExtractor.readSampleData(inputBuffer, 0);
        if (sampleSize < 0) {
            lowResDecoder.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        else {
            lowResDecoder.queueInputBuffer(bufferIndex, 0, sampleSize, lowResExtractor.getSampleTime(), 0);
            lowResExtractor.advance();
        }
    }

    private Bundle getAvailableOutputBufferData() {
        Bundle bufferData = null;
        try {
            bufferData = availableOutputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bufferData;
    }

    @RequiresApi(api = LOLLIPOP)
    public void handleMessage(@NonNull Message inputMessage) {
        Bundle messageData = inputMessage.getData();
        int key = messageData.getInt("MESSAGE_KEY");
        Bundle bufferData;
        int bufferIndex;
        long timestamp;
        switch (key) {
            case MotionPhotoReader.MSG_NEXT_FRAME:
                // Get the next available input buffer and read frame data
                bufferIndex = getAvailableInputBufferIndex();
                ByteBuffer inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                readFromExtractor(inputBuffer, bufferIndex);

                // Get the next available output buffer and release frame data
                bufferData = getAvailableOutputBufferData();
                timestamp = bufferData.getLong("TIMESTAMP_US");
                bufferIndex = bufferData.getInt("BUFFER_INDEX");
                lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp * US_TO_NS);
                break;

            case MotionPhotoReader.MSG_SEEK_TO_FRAME:
                // Get the next available input buffer and read frame data
                lowResExtractor.seekTo(messageData.getLong("TIME_US"), messageData.getInt("MODE"));

                bufferIndex = getAvailableInputBufferIndex();
                inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                readFromExtractor(inputBuffer, bufferIndex);

                // Get the next available output buffer and release frame data
                bufferData = getAvailableOutputBufferData();
                timestamp = bufferData.getLong("TIMESTAMP_US");
                bufferIndex = bufferData.getInt("BUFFER_INDEX");
                lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp * US_TO_NS);
                break;

            default:
                throw new IllegalStateException();
        }
    }
}
