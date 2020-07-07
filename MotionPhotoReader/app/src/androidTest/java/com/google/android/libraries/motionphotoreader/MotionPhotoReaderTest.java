package com.google.android.libraries.motionphotoreader;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.adobe.internal.xmp.XMPException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Instrumented test for MotionPhotoReader class.
 */
@RunWith(AndroidJUnit4.class)
public class MotionPhotoReaderTest {
    private static final String filename = "/sdcard/MVIMG_20200621_200240.jpg";
    private static final int NUM_FRAMES = 44;
    private static final long SEEK_AMOUNT_US = 10_000L;

    // TODO: close motion photo readers
    @Before
    public void setUp() {

    }

    @Test(expected = IOException.class)
    public void openMotionPhotoReader_invalidFile_throwsIOException() throws IOException, XMPException {
        MotionPhotoReader.open("/sdcard/MVIMG_20200621_200241.jpg", null);
    }

    @Test
    public void numberOfFramesPlayed_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);

        int frameCount = 0;
        while (reader.hasNextFrame()) {
            reader.nextFrame();
            frameCount++;
        }
        assertEquals(NUM_FRAMES, frameCount);
    }

    @Test
    public void getCurrentTimestamp_onStart_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);
        assertEquals(0, reader.getCurrentTimestamp());
    }

    @Test
    public void getCurrentTimestamp_nextFrame_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);

        long currentTimestampUs = -1L;
        while (reader.hasNextFrame()) {
            long newTimestampUs = reader.getCurrentTimestamp();
            reader.nextFrame();
            boolean flag = currentTimestampUs < newTimestampUs;
            assertTrue("Timestamp did not increase: " + currentTimestampUs + " vs. " + newTimestampUs, flag);
            currentTimestampUs = newTimestampUs;
        }
    }

    @Test
    public void getCurrentTimestamp_seekTo_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);

        long currentTimestampUs = -1L;
        while (reader.hasNextFrame()) {
            long newTimestampUs = reader.getCurrentTimestamp();
            reader.seekTo(reader.getCurrentTimestamp() + SEEK_AMOUNT_US, MediaExtractor.SEEK_TO_NEXT_SYNC);
            boolean flag = currentTimestampUs < newTimestampUs;
            assertTrue("Timestamp did not increase: " + currentTimestampUs + " vs. " + newTimestampUs, flag);
            currentTimestampUs = newTimestampUs;
        }
    }

    @Test
    public void hasNextFrame_atBeginningOfVideo_returnsTrue() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);
        boolean flag = reader.hasNextFrame();
        assertTrue("No next frame", flag);
    }

    @Test
    public void hasNextFrame_atLastFrame_returnsFalse() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);

        long timestampUs = reader.getMotionPhotoInfo().getDuration();
        reader.seekTo(timestampUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
        boolean flag = reader.hasNextFrame();
        assertFalse("Did not seek to end of video", flag);
    }

    @Test
    public void availableInputBufferQueue_isNotEmpty()
            throws IOException, XMPException, NoSuchFieldException, IllegalAccessException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);
        Field inputBufferQueueField = reader.getClass().getDeclaredField("availableInputBuffers");
        inputBufferQueueField.setAccessible(true);
        BlockingQueue<Integer> availableInputBufferQueue = (BlockingQueue<Integer>) inputBufferQueueField.get(reader);

        boolean flag = availableInputBufferQueue.size() > 0;
        assertTrue("Available input buffer queue is empty", flag);
    }

    @Test
    public void availableOutputBufferQueue_isQueried() throws IOException, XMPException, InterruptedException {
        BlockingQueue<Integer> availableInputBuffers = new LinkedBlockingQueue<>();
        BlockingQueue<Bundle> availableOutputBuffers = new LinkedBlockingQueue<>();

        BlockingQueue<Integer> fakeAvailableInputBuffers = mock(LinkedBlockingQueue.class);
        BlockingQueue<Bundle> fakeAvailableOutputBuffers = mock(LinkedBlockingQueue.class);

        doAnswer((Answer<Void>) invocation -> {
            int index = invocation.getArgument(0);
            availableInputBuffers.offer(index);
            return null;
        }).when(fakeAvailableInputBuffers).offer(anyInt());

        doAnswer((Answer<Void>) invocation -> {
            Bundle bufferData = invocation.getArgument(0);
            availableOutputBuffers.offer(bufferData);
            return null;
        }).when(fakeAvailableOutputBuffers).offer(any(Bundle.class));

        doAnswer((Answer<Integer>) invocation -> {
            long timeout = invocation.getArgument(0);
            TimeUnit timeUnit = invocation.getArgument(1);
            int index = availableInputBuffers.poll(timeout, timeUnit);
            return index;
        }).when(fakeAvailableInputBuffers).poll(anyLong(), any(TimeUnit.class));

        doAnswer((Answer<Bundle>) invocation -> {
            long timeout = invocation.getArgument(0);
            TimeUnit timeUnit = invocation.getArgument(1);
            Bundle bufferData = availableOutputBuffers.poll(timeout, timeUnit);
            return bufferData;
        }).when(fakeAvailableOutputBuffers).poll(anyLong(), any(TimeUnit.class));

        MotionPhotoReader reader = MotionPhotoReader.open(filename, null, fakeAvailableInputBuffers, fakeAvailableOutputBuffers);

        while (reader.hasNextFrame()) {
            reader.nextFrame();
        }
        verify(fakeAvailableOutputBuffers, times(NUM_FRAMES)).offer(any(Bundle.class));
        verify(fakeAvailableOutputBuffers, times(NUM_FRAMES)).poll(anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void getMotionPhotoImage_isNotNull() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);
        Bitmap bmp = reader.getMotionPhotoImageBitmap();
        assertNotNull(bmp);
    }
}