package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Local unit test for the BufferHandler class.
 */
public class BufferProcessorTest {

    // define a mock media format for motion photo v1 format
    private final static int V1 = 1;
    private final static int KEY_WIDTH = 4032;
    private final static int KEY_HEIGHT = 3024;
    private final static long KEY_DURATION = 1499400;
    private final static int KEY_ROTATION = 90;
    private final static String KEY_MIME = "video/avc";
    private final static int VIDEO_OFFSET = 2592317;

    private OutputSurface outputSurface;
    private MediaExtractor lowResExtractor;
    private MediaCodec lowResDecoder;
    MotionPhotoInfo motionPhotoInfo;
    private BlockingQueue<Integer> availableInputBuffers;
    private BlockingQueue<Bundle> availableOutputBuffers;

    @Before
    public void setUp() {
        // Set up mock objects
        lowResExtractor = mock(MediaExtractor.class);
        lowResDecoder = mock(MediaCodec.class);
        availableInputBuffers = new LinkedBlockingQueue<>();
        availableOutputBuffers = new LinkedBlockingQueue<>();

        MediaFormat mediaFormat = createFakeVideoFormat(
                KEY_WIDTH,
                KEY_HEIGHT,
                KEY_ROTATION,
                KEY_ROTATION,
                KEY_MIME
        );
         motionPhotoInfo = new MotionPhotoInfo(mediaFormat, VIDEO_OFFSET, V1);
        outputSurface = new OutputSurface(mock(Handler.class), motionPhotoInfo);

        availableInputBuffers.offer(1);
        availableInputBuffers.offer(2);
        availableInputBuffers.offer(3);

        Bundle bundle = mock(Bundle.class);
        doAnswer((Answer<Long>) invocation -> 1_000L).when(bundle).getLong(eq("TIMESTAMP_US"));
        doAnswer((Answer<Integer>) invocation -> 0).when(bundle).getInt(eq("BUFFER_INDEX"));
        availableOutputBuffers.offer(bundle);
    }

    /**
     * Creates a mock media format object. Used to represent video formats of motion photo files.
     * @param width The width of the motion photo video, in pixels.
     * @param height The height of the motion photo video, in pixels.
     * @param duration The duration of the motion photo video, in microseconds.
     * @param rotation The rotation applied to the motion photo, in degrees.
     * @param mime The mime type of the motion photo video track.
     * @return a mock MediaFormat object.
     */
    private MediaFormat createFakeVideoFormat(int width,
                                              int height,
                                              long duration,
                                              int rotation,
                                              String mime) {
        MediaFormat videoFormat = mock(MediaFormat.class);
        doAnswer((Answer<Integer>) invocation -> width)
                .when(videoFormat)
                .getInteger(eq(MediaFormat.KEY_WIDTH));
        doAnswer((Answer<Integer>) invocation -> height)
                .when(videoFormat)
                .getInteger(eq(MediaFormat.KEY_HEIGHT));
        doAnswer((Answer<Long>) invocation -> duration)
                .when(videoFormat)
                .getLong(eq(MediaFormat.KEY_DURATION));
        doAnswer((Answer<Integer>) invocation -> rotation)
                .when(videoFormat)
                .getInteger(eq(MediaFormat.KEY_ROTATION));
        doAnswer((Answer<String>) invocation -> mime)
                .when(videoFormat)
                .getString(eq(MediaFormat.KEY_MIME));

        return videoFormat;
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameTrue_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                lowResExtractor,
                lowResDecoder,
                true,
                availableInputBuffers,
                availableOutputBuffers
        ));

        doAnswer((Answer<Integer>) invocation -> 16)
                .when(lowResExtractor)
                .readSampleData(any(ByteBuffer.class), anyInt());
        doAnswer((Answer<ByteBuffer>) invocation -> mock(ByteBuffer.class))
                .when(lowResDecoder)
                .getInputBuffer(anyInt());

        Bundle messageData = mock(Bundle.class);
        doAnswer((Answer<Integer>) invocation -> MotionPhotoReader.MSG_NEXT_FRAME)
                .when(messageData)
                .getInt(anyString());
        bufferProcessor.process(messageData);

        verify(lowResExtractor).readSampleData(any(ByteBuffer.class), eq(0));
        verify(lowResDecoder, times(1))
                .queueInputBuffer(eq(1), eq(0), anyInt(), anyLong(), eq(0));
        verify(lowResExtractor, times(1)).advance();
        verify(lowResExtractor, never()).seekTo(anyLong(), anyInt());
        verify(lowResDecoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameFalse_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                lowResExtractor,
                lowResDecoder,
                true,
                availableInputBuffers,
                availableOutputBuffers
        ));

        doAnswer((Answer<Integer>) invocation -> -1)
                .when(lowResExtractor)
                .readSampleData(any(ByteBuffer.class), anyInt());
        doAnswer((Answer<ByteBuffer>) invocation -> mock(ByteBuffer.class))
                .when(lowResDecoder)
                .getInputBuffer(anyInt());

        Bundle messageData = mock(Bundle.class);
        doAnswer((Answer<Integer>) invocation -> MotionPhotoReader.MSG_NEXT_FRAME)
                .when(messageData).getInt(anyString());
        bufferProcessor.process(messageData);

        verify(lowResExtractor).readSampleData(any(ByteBuffer.class), anyInt());
        verify(lowResDecoder, times(1)).queueInputBuffer(
                eq(1),
                eq(0),
                eq(0),
                eq(0L),
                eq(MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        );
        verify(lowResExtractor, never()).seekTo(anyLong(), anyInt());
        verify(lowResDecoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test
    public void handleSeekToFrameMsg_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                lowResExtractor,
                lowResDecoder,
                true,
                availableInputBuffers,
                availableOutputBuffers
        ));

        Bundle messageData = mock(Bundle.class);
        doAnswer((Answer<Integer>) invocation -> MotionPhotoReader.MSG_SEEK_TO_FRAME)
                .when(messageData)
                .getInt(anyString());
        bufferProcessor.process(messageData);

        verify(lowResExtractor, times(1)).seekTo(anyLong(), anyInt());
        verify(lowResDecoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test(expected = IllegalStateException.class)
    public void handleIncorrectMsg_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                lowResExtractor,
                lowResDecoder,
                true,
                availableInputBuffers,
                availableOutputBuffers
        ));

        Bundle messageData = mock(Bundle.class);
        doAnswer((Answer<Integer>) invocation -> 0x0100).when(messageData).getInt(anyString());
        bufferProcessor.process(messageData);

        verify(lowResExtractor, never()).seekTo(anyLong(), anyInt());
        verify(lowResDecoder, never()).releaseOutputBuffer(anyInt(), anyLong());
    }
}