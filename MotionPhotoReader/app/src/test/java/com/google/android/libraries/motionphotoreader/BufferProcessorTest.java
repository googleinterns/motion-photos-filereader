package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V1;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_HEIGHT;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_MIME;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_ROTATION;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_WIDTH;
import static com.google.android.libraries.motionphotoreader.TestConstants.MOTION_TRACK_INDEX;
import static com.google.android.libraries.motionphotoreader.TestConstants.VIDEO_OFFSET;
import static com.google.android.libraries.motionphotoreader.TestConstants.VIDEO_TRACK_INDEX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

    private OutputSurface outputSurface;
    private MediaExtractor extractor;
    private MediaCodec decoder;
    MotionPhotoInfo motionPhotoInfo;
    private BlockingQueue<Integer> availableInputBuffers;
    private BlockingQueue<Bundle> availableOutputBuffers;

    @Before
    public void setUp() {
        // Set up mock objects
        MediaFormat videoFormat = createFakeVideoFormat(
                KEY_WIDTH,
                KEY_HEIGHT,
                KEY_ROTATION,
                KEY_ROTATION,
                KEY_MIME
        );
        MediaFormat motionFormat = createFakeMotionFormat(Constants.MICROVIDEO_META_MIMETYPE);
        extractor = mock(MediaExtractor.class);

        // The extractor should set both the video and motion track indices
        when(extractor.getTrackFormat(eq(VIDEO_TRACK_INDEX))).thenReturn(videoFormat);
        when(extractor.getTrackFormat(eq(MOTION_TRACK_INDEX))).thenReturn(motionFormat);

        // The extractor should read samples from both the video track and the motion track
        when(extractor.getSampleTrackIndex()).thenAnswer(new Answer<Integer>() {
            private int count = 0;

            public Integer answer(InvocationOnMock invocation) {
                if (count == 0) {
                    count++;
                    return VIDEO_TRACK_INDEX;
                } else {
                    return MOTION_TRACK_INDEX;
                }
            }
        });

        decoder = mock(MediaCodec.class);
        availableInputBuffers = new LinkedBlockingQueue<>();
        availableOutputBuffers = new LinkedBlockingQueue<>();

        motionPhotoInfo = new MotionPhotoInfo(videoFormat, VIDEO_OFFSET, MOTION_PHOTO_V1);
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
    /**
     * Creates a mock media format object. Used to represent the motion track media format.
     * @param mime The mime type of the motion photo motion track.
     * @return a mock MediaFormat object.
     */
    private MediaFormat createFakeMotionFormat(String mime) {
        MediaFormat videoFormat = mock(MediaFormat.class);
        when(videoFormat.getString(MediaFormat.KEY_MIME)).thenReturn(mime);
        return videoFormat;
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameTrue_stabilizationOff_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                /* stabilizationOn = */ false,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                /* testMode = */ true,
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
        verify(decoder).releaseOutputBuffer(anyInt(), anyBoolean());
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameTrue_stabilizationOn_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                /* stabilizationOn = */ true,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                /* testMode = */ true,
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
        verify(extractor, times(2)).advance();
        verify(extractor, never()).seekTo(anyLong(), anyInt());
        verify(decoder).releaseOutputBuffer(anyInt(), anyBoolean());
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameFalse_stabilizationOff_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                /* stabilizationOn = */ false,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                /* testMode = */ true,
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
        verify(decoder).releaseOutputBuffer(anyInt(), anyBoolean());
    }

    @Test
    public void handleNextFrameMsg_hasNextFrameFalse_stabilizationOn_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                /* stabilizationOn = */ true,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                /* testMode = */ true,
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
        verify(decoder).releaseOutputBuffer(anyInt(), anyBoolean());
    }

    @Test
    public void handleSeekToFrameMsg_stabilizationOff_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                false,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                true,
                availableInputBuffers,
                availableOutputBuffers
        ));

        Bundle messageData = mock(Bundle.class);
        when(messageData.getInt(anyString())).thenReturn(MotionPhotoReader.MSG_SEEK_TO_FRAME);
        bufferProcessor.process(messageData);

        verify(extractor, times(1)).seekTo(anyLong(), anyInt());
        verify(decoder).releaseOutputBuffer(anyInt(), anyBoolean());
    }

    @Test
    public void handleSeekToFrameMsg_stabilizationOn_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                true,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                true,
                availableInputBuffers,
                availableOutputBuffers
        ));

        Bundle messageData = mock(Bundle.class);
        when(messageData.getInt(anyString())).thenReturn(MotionPhotoReader.MSG_SEEK_TO_FRAME);
        bufferProcessor.process(messageData);

        verify(extractor, times(1)).seekTo(anyLong(), anyInt());
        verify(decoder).releaseOutputBuffer(anyInt(), anyBoolean());
    }

    @Test(expected = IllegalStateException.class)
    public void handleIncorrectMsg_stabilizationOff_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                /* stabilizationOn = */ false,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                /* testMode = */ true,
                availableInputBuffers,
                availableOutputBuffers
        ));

        Bundle messageData = mock(Bundle.class);
        when(messageData.getInt(anyString())).thenReturn(0x0100);
        bufferProcessor.process(messageData);

        verify(extractor, never()).seekTo(anyLong(), anyInt());
        verify(decoder, never()).releaseOutputBuffer(anyInt(), anyLong());
    }

    @Test(expected = IllegalStateException.class)
    public void handleIncorrectMsg_stabilizationOn_isCorrect() {
        BufferProcessor bufferProcessor = spy(new BufferProcessor(
                outputSurface,
                extractor,
                decoder,
                /* stabilizationOn = */ true,
                VIDEO_TRACK_INDEX,
                MOTION_TRACK_INDEX,
                /* testMode = */ true,
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