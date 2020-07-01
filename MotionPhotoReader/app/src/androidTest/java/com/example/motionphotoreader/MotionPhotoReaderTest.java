package com.example.motionphotoreader;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.adobe.internal.xmp.XMPException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MotionPhotoReaderTest {
    private static final String filename = "/sdcard/MVIMG_20200621_200240.jpg";
    private static final int NUM_FRAMES = 44;
    private static final long SEEK_AMOUNT_US = 100_000L;

    @Before
    public void setUp() {

    }

    @Test
    public void numberOfFramesPlayed_isCorrect() throws IOException, XMPException, InterruptedException, ExecutionException, TimeoutException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);

        int frameCount = 0;
        while (reader.hasNextFrame().get(1000L, TimeUnit.MILLISECONDS)) {
            reader.nextFrame();
            frameCount++;
        }
        assertEquals(NUM_FRAMES, frameCount);
    }

    @Test
    public void seekTo_beginningOfVideo_timestampIsCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);

        reader.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        long currentTimestepUs = reader.getCurrentTimestamp();
        assertEquals(0, currentTimestepUs);
    }

    @Test
    public void hasNextFrame_atLastFrame_returnsFalse()
            throws IOException, XMPException, InterruptedException, ExecutionException, TimeoutException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);

        long timestampUs = reader.getMotionPhotoInfo().getDuration();
        reader.seekTo(timestampUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
        boolean flag = reader.hasNextFrame().get(1000L, TimeUnit.MILLISECONDS);
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
    public void availableOutputBufferQueue_isQueried()
            throws IOException, XMPException, NoSuchFieldException, IllegalAccessException,
            InterruptedException, TimeoutException, ExecutionException {
        MotionPhotoReader reader = MotionPhotoReader.open(filename, null);
        Field outputBufferQueueField = reader.getClass().getDeclaredField("availableOutputBuffers");
        outputBufferQueueField.setAccessible(true);
        BlockingQueue<Integer> availableOutputBufferQueue = spy((BlockingQueue<Integer>) outputBufferQueueField.get(reader));

        while (reader.hasNextFrame().get(1000L, TimeUnit.MILLISECONDS)) {
            reader.nextFrame();
        }
        verify(availableOutputBufferQueue).offer(anyInt());
        verify(availableOutputBufferQueue).poll(anyLong(), eq(TimeUnit.MILLISECONDS));
    }
}