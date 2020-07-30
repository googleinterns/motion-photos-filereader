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
import static org.mockito.Mockito.when;

/**
 * Local unit test for the BufferHandler class.
 */
public class BufferProcessorTest {

    // define a mock media format for motion photo v1 format
    private final static int KEY_WIDTH = 4032;
    private final static int KEY_HEIGHT = 3024;
    private final static long KEY_DURATION = 1499400;
    private final static int KEY_ROTATION = 90;
    private final static String KEY_MIME = "video/avc";
    private final static int VIDEO_OFFSET = 2592317;

    private OutputSurface outputSurface;
    private MediaExtractor extractor;
    private MediaCodec decoder;
    private BlockingQueue<Integer> availableInputBuffers;
    private BlockingQueue<Bundle> availableOutputBuffers;

    @Before
    public void setUp() {
        // Set up mock objects
        extractor = mock(MediaExtractor.class);
        decoder = mock(MediaCodec.class);
        availableInputBuffers = new LinkedBlockingQueue<>();
        availableOutputBuffers = new LinkedBlockingQueue<>();

        MediaFormat mediaFormat = createFakeVideoFormat(
                KEY_WIDTH,
                KEY_HEIGHT,
                KEY_ROTATION,
                KEY_ROTATION,
                KEY_MIME
        );
        MotionPhotoInfo motionPhotoInfo = new MotionPhotoInfo(mediaFormat, VIDEO_OFFSET);
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
        when(videoFormat.getInteger(MediaFormat.KEY_WIDTH)).thenReturn(width);
        when(videoFormat.getInteger(MediaFormat.KEY_HEIGHT)).thenReturn(height);
        when(videoFormat.getLong(MediaFormat.KEY_DURATION)).thenReturn(duration);
        when(videoFormat.getInteger(MediaFormat.KEY_ROTATION)).thenReturn(rotation);
        when(videoFormat.getString(MediaFormat.KEY_MIME)).thenReturn(mime);

        return videoFormat;
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameTrue_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                availableInputBuffers,
                availableOutputBuffers
        ));

        when(extractor.readSampleData(any(ByteBuffer.class), anyInt())).thenReturn(16);
        when(decoder.getInputBuffer(anyInt())).thenReturn(mock(ByteBuffer.class));

        Bundle messageData = mock(Bundle.class);
        when(messageData.getInt(anyString())).thenReturn(MotionPhotoReader.MSG_NEXT_FRAME);
        bufferProcessor.process(messageData);

        verify(extractor).readSampleData(any(ByteBuffer.class), eq(0));
        verify(decoder, times(1))
                .queueInputBuffer(eq(1), eq(0), anyInt(), anyLong(), eq(0));
        verify(extractor, times(1)).advance();
        verify(extractor, never()).seekTo(anyLong(), anyInt());
        verify(decoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameFalse_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                availableInputBuffers,
                availableOutputBuffers
        ));

        when(extractor.readSampleData(any(ByteBuffer.class), anyInt())).thenReturn(-1);
        when(decoder.getInputBuffer(anyInt())).thenReturn(mock(ByteBuffer.class));

        Bundle messageData = mock(Bundle.class);
        when(messageData.getInt(anyString())).thenReturn(MotionPhotoReader.MSG_NEXT_FRAME);
        bufferProcessor.process(messageData);

        verify(extractor).readSampleData(any(ByteBuffer.class), anyInt());
        verify(decoder, times(1)).queueInputBuffer(
                eq(1),
                eq(0),
                eq(0),
                eq(0L),
                eq(MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        );
        verify(extractor, never()).seekTo(anyLong(), anyInt());
        verify(decoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test
    public void handleSeekToFrameMsg_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                availableInputBuffers,
                availableOutputBuffers
        ));

        Bundle messageData = mock(Bundle.class);
        when(messageData.getInt(anyString())).thenReturn(MotionPhotoReader.MSG_SEEK_TO_FRAME);
        bufferProcessor.process(messageData);

        verify(extractor, times(1)).seekTo(anyLong(), anyInt());
        verify(decoder).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test(expected = IllegalStateException.class)
    public void handleIncorrectMsg_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                availableInputBuffers,
                availableOutputBuffers
        ));

        Bundle messageData = mock(Bundle.class);
        when(messageData.getInt(anyString())).thenReturn(0x0100);
        bufferProcessor.process(messageData);

        verify(extractor, never()).seekTo(anyLong(), anyInt());
        verify(decoder, never()).releaseOutputBuffer(anyInt(), anyLong());
    }
}