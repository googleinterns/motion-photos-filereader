package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.android.libraries.motionphotoreader.Constants.EMPTY_SAMPLE_SIZE;
import static com.google.android.libraries.motionphotoreader.Constants.INPUT_BUFFER_QUEUE_SIZE;
import static com.google.android.libraries.motionphotoreader.Constants.OUTPUT_BUFFER_QUEUE_SIZE;
import static com.google.android.libraries.motionphotoreader.Constants.SAMPLE_BUFFER_INDEX;
import static com.google.android.libraries.motionphotoreader.Constants.SAMPLE_PRESENTATION_TIME_US;
import static com.google.android.libraries.motionphotoreader.Constants.SAMPLE_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Local unit test for the BufferHandler class.
 */
public class MotionPhotoUtilsTest {

    private MediaExtractor extractor;
    private MediaCodec decoder;
    private BlockingQueue<Integer> inputBufferQueue;
    private BlockingQueue<Bundle> outputBufferQueue;

    @Before
    public void setUp() {
        inputBufferQueue = new LinkedBlockingQueue<>();
        outputBufferQueue = new LinkedBlockingQueue<>();
        extractor = mock(MediaExtractor.class);
        decoder = mock(MediaCodec.class);
    }

    @Test
    public void getInputBufferFromQueue_whenEmpty_isNull() {
        assertEquals(0, inputBufferQueue.size());
        Integer bufferIndex = MotionPhotoReaderUtils.getInputBuffer(inputBufferQueue);
        assertNull(bufferIndex);
    }

    @Test
    public void getInputBufferFromQueue_whenFull_isNotNull() {
        // Test polling queue as elements are added
        for (int i = 0; i < INPUT_BUFFER_QUEUE_SIZE; i++) {
            boolean result = inputBufferQueue.offer(i);
            assertTrue(result);
            int bufferIndex = MotionPhotoReaderUtils.getInputBuffer(inputBufferQueue);
            assertEquals(i, bufferIndex);
        }

        // Test polling queue after all elements are added
        for (int i = 0; i < INPUT_BUFFER_QUEUE_SIZE; i++) {
            boolean result = inputBufferQueue.offer(i);
            assertTrue(result);
        }
        for (int i = 0; i < INPUT_BUFFER_QUEUE_SIZE; i++) {
            int bufferIndex = MotionPhotoReaderUtils.getInputBuffer(inputBufferQueue);
            assertEquals(i, bufferIndex);
        }
    }

    @Test
    public void getOutputBufferFromQueue_whenEmpty_isNull() {
        assertEquals(0, outputBufferQueue.size());
        Bundle bufferData = MotionPhotoReaderUtils.getOutputBuffer(outputBufferQueue);
        assertNull(bufferData);
    }

    @Test
    public void getOutputBufferFromQueue_whenFull_isNotNull() {
        // Test polling queue as elements are added
        for (int i = 0; i < OUTPUT_BUFFER_QUEUE_SIZE; i++) {
            Bundle bufferData = mock(Bundle.class);
            when(bufferData.getInt(eq("BUFFER_INDEX"))).thenReturn(i);
            when(bufferData.getLong(eq("TIMESTAMP_US"))).thenReturn(1000L * i);
            boolean result = outputBufferQueue.offer(bufferData);
            assertTrue(result);

            Bundle receivedBufferData = MotionPhotoReaderUtils.getOutputBuffer(outputBufferQueue);
            assertEquals(i, receivedBufferData.getInt("BUFFER_INDEX"));
            assertEquals(1000 * i, receivedBufferData.getLong("TIMESTAMP_US"));
        }

        // Test polling queue after all elements are added
        for (int i = 0; i < OUTPUT_BUFFER_QUEUE_SIZE; i++) {
            Bundle bufferData = mock(Bundle.class);
            when(bufferData.getInt(eq("BUFFER_INDEX"))).thenReturn(i);
            when(bufferData.getLong(eq("TIMESTAMP_US"))).thenReturn(1000L * i);
            boolean result = outputBufferQueue.offer(bufferData);
            assertTrue(result);
        }
        for (int i = 0; i < OUTPUT_BUFFER_QUEUE_SIZE; i++) {
            Bundle receivedBufferData = MotionPhotoReaderUtils.getOutputBuffer(outputBufferQueue);
            assertEquals(i, receivedBufferData.getInt("BUFFER_INDEX"));
            assertEquals(1000 * i, receivedBufferData.getLong("TIMESTAMP_US"));
        }
    }

    @Test
    public void readVideoFromTrack_sampleDoesNotExist_isCorrect() {
        when(extractor.readSampleData(any(ByteBuffer.class), eq(0))).thenReturn(EMPTY_SAMPLE_SIZE);
        MotionPhotoReaderUtils.readFromVideoTrack(
                extractor, decoder,
                mock(ByteBuffer.class), SAMPLE_BUFFER_INDEX
        );
        verify(decoder).queueInputBuffer(
                eq(SAMPLE_BUFFER_INDEX),
                /* offset = */ eq(0),
                /* size = */ eq(0),
                /* presentationTimeUs = */ eq(0L),
                eq(MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        );
    }
    @Test
    public void readVideoFromTrack_sampleExists_isCorrect() {
        when(extractor.readSampleData(any(ByteBuffer.class), eq(0))).thenReturn(SAMPLE_SIZE);
        when(extractor.getSampleTime()).thenReturn(SAMPLE_PRESENTATION_TIME_US);
        MotionPhotoReaderUtils.readFromVideoTrack(
                extractor, decoder,
                mock(ByteBuffer.class), SAMPLE_BUFFER_INDEX
        );
        verify(decoder).queueInputBuffer(
                eq(SAMPLE_BUFFER_INDEX),
                /* offset = */ eq(0),
                eq(SAMPLE_SIZE),
                eq(SAMPLE_PRESENTATION_TIME_US),
                /* flags = */ eq(0)
        );
    }
}