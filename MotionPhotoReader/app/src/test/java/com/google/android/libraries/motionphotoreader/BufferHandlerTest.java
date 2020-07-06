package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Local unit test for the BufferHandler class.
 */
public class BufferHandlerTest {

    private MediaExtractor lowResExtractor;
    private MediaCodec lowResDecoder;
    private BlockingQueue<Integer> availableInputBuffers;
    private BlockingQueue<Bundle> availableOutputBuffers;

    @Before
    public void setUp() {
        lowResExtractor = mock(MediaExtractor.class);
        lowResDecoder = mock(MediaCodec.class);
        availableInputBuffers = new LinkedBlockingQueue<>();
        availableOutputBuffers = new LinkedBlockingQueue<>();

        availableInputBuffers.offer(1);
        availableInputBuffers.offer(2);
        availableInputBuffers.offer(3);

        Bundle bundle = mock(Bundle.class);
        doAnswer((Answer<Long>) invocation -> 1_000L).when(bundle).getLong(eq("TIMESTAMP_US"));
        doAnswer((Answer<Integer>) invocation -> 0).when(bundle).getInt(eq("BUFFER_INDEX"));
        availableOutputBuffers.offer(bundle);
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameTrue_isCorrect() {
        BufferHandler bufferHandler = spy(new BufferHandler(
                mock(Looper.class),
                lowResExtractor, lowResDecoder,
                availableInputBuffers, availableOutputBuffers
        ));

        doAnswer((Answer<Integer>) invocation -> 16).when(lowResExtractor).readSampleData(any(ByteBuffer.class), anyInt());
        doAnswer((Answer<ByteBuffer>) invocation -> mock(ByteBuffer.class)).when(lowResDecoder).getInputBuffer(anyInt());

        Message message = mock(Message.class);
        Bundle messageData = mock(Bundle.class);

        doAnswer((Answer<Bundle>) invocation -> messageData).when(message).getData();
        doAnswer((Answer<Integer>) invocation -> MotionPhotoReader.MSG_NEXT_FRAME).when(messageData).getInt(anyString());
        bufferHandler.handleMessage(message);

        verify(lowResExtractor).readSampleData(any(ByteBuffer.class), eq(0));
        verify(lowResDecoder, times(1)).queueInputBuffer(eq(1), eq(0), anyInt(), anyLong(), eq(0));
        verify(lowResExtractor, times(1)).advance();
        verify(lowResExtractor, never()).seekTo(anyLong(), anyInt());
        verify(lowResDecoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameFalse_isCorrect() {
        BufferHandler bufferHandler = spy(new BufferHandler(
                mock(Looper.class),
                lowResExtractor, lowResDecoder,
                availableInputBuffers, availableOutputBuffers
        ));

        doAnswer((Answer<Integer>) invocation -> -1).when(lowResExtractor).readSampleData(any(ByteBuffer.class), anyInt());
        doAnswer((Answer<ByteBuffer>) invocation -> mock(ByteBuffer.class)).when(lowResDecoder).getInputBuffer(anyInt());

        Message message = mock(Message.class);
        Bundle messageData = mock(Bundle.class);

        doAnswer((Answer<Bundle>) invocation -> messageData).when(message).getData();
        doAnswer((Answer<Integer>) invocation -> MotionPhotoReader.MSG_NEXT_FRAME).when(messageData).getInt(anyString());
        bufferHandler.handleMessage(message);

        verify(lowResExtractor).readSampleData(any(ByteBuffer.class), anyInt());
        verify(lowResDecoder, times(1)).queueInputBuffer(eq(1), eq(0), eq(0), eq(0L), eq(MediaCodec.BUFFER_FLAG_END_OF_STREAM));
        verify(lowResExtractor, never()).seekTo(anyLong(), anyInt());
        verify(lowResDecoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test
    public void handleSeekToFrameMsg_isCorrect() {
        BufferHandler bufferHandler = spy(new BufferHandler(
                mock(Looper.class),
                lowResExtractor, lowResDecoder,
                availableInputBuffers, availableOutputBuffers
        ));

        Message message = mock(Message.class);
        Bundle messageData = mock(Bundle.class);

        doAnswer((Answer<Bundle>) invocation -> messageData).when(message).getData();
        doAnswer((Answer<Integer>) invocation -> MotionPhotoReader.MSG_SEEK_TO_FRAME).when(messageData).getInt(anyString());
        bufferHandler.handleMessage(message);

        verify(lowResExtractor, times(1)).seekTo(anyLong(), anyInt());
        verify(lowResDecoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test(expected = IllegalStateException.class)
    public void handleIncorrectMsg_isCorrect() {
        BufferHandler bufferHandler = spy(new BufferHandler(
                mock(Looper.class),
                lowResExtractor, lowResDecoder,
                availableInputBuffers, availableOutputBuffers
        ));

        Message message = mock(Message.class);
        Bundle messageData = mock(Bundle.class);

        doAnswer((Answer<Bundle>) invocation -> messageData).when(message).getData();
        doAnswer((Answer<Integer>) invocation -> 0x0100).when(messageData).getInt(anyString());
        bufferHandler.handleMessage(message);

        verify(lowResExtractor, never()).seekTo(anyLong(), anyInt());
        verify(lowResDecoder, never()).releaseOutputBuffer(anyInt(), anyLong());
    }
}