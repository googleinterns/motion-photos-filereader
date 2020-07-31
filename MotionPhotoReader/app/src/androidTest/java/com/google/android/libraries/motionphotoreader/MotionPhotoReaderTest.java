package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.adobe.internal.xmp.XMPException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test for MotionPhotoReader class.
 */
@RunWith(AndroidJUnit4.class)
public class MotionPhotoReaderTest {
    private static final String filename = "MVIMG_20200621_200240.jpg";
    private static final int NUM_FRAMES = 44;
    private static final long SEEK_AMOUNT_US = 10_000L;
    
    private Context context;

    /** A list of opened motion photo readers to close afterwards. */
    private final List<Runnable> cleanup = new ArrayList<>();

    @Before 
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @After
    public void tearDown() {
        for (Runnable r : cleanup) {
            r.run();
        }
        cleanup.clear();
    }

    @Test(expected = IOException.class)
    public void openMotionPhotoReader_invalidFile_throwsIOException()
            throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open("test_photo.jpg", null);
        cleanup.add(reader::close);
    }

    @Test
    public void numberOfFramesPlayed_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null
        );

        int frameCount = 0;
        while (reader.hasNextFrame()) {
            reader.nextFrame();
            frameCount++;
        }
        assertEquals(NUM_FRAMES, frameCount);
        cleanup.add(reader::close);
    }

    @Test
    public void getCurrentTimestamp_onStart_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null
        );
        assertEquals(0, reader.getCurrentTimestampUs());
        cleanup.add(reader::close);
    }

    @Test
    public void getCurrentTimestamp_nextFrame_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null
        );

        long currentTimestampUs = -1L;
        while (reader.hasNextFrame()) {
            long newTimestampUs = reader.getCurrentTimestampUs();
            reader.nextFrame();
            boolean flag = currentTimestampUs < newTimestampUs;
            assertTrue("Timestamp did not increase: "
                    + currentTimestampUs + " vs. " + newTimestampUs,
                    flag);
            currentTimestampUs = newTimestampUs;
        }
        cleanup.add(reader::close);
    }

    @Test
    public void getCurrentTimestamp_seekTo_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null
        );

        long currentTimestampUs = -1L;
        while (reader.hasNextFrame()) {
            long newTimestampUs = reader.getCurrentTimestampUs();
            reader.seekTo(reader.getCurrentTimestampUs() + SEEK_AMOUNT_US,
                    MediaExtractor.SEEK_TO_NEXT_SYNC);
            boolean flag = currentTimestampUs < newTimestampUs;
            assertTrue("Timestamp did not increase: "
                    + currentTimestampUs + " vs. " + newTimestampUs,
                    flag);
            currentTimestampUs = newTimestampUs;
        }
        cleanup.add(reader::close);
    }

    @Test
    public void hasNextFrame_atBeginningOfVideo_returnsTrue() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null
        );
        boolean flag = reader.hasNextFrame();
        assertTrue("No next frame", flag);
        cleanup.add(reader::close);
    }

    @Test
    public void hasNextFrame_atLastFrame_returnsFalse() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null
        );

        long timestampUs = reader.getMotionPhotoInfo().getDurationUs();
        reader.seekTo(timestampUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
        boolean flag = reader.hasNextFrame();
        assertFalse("Did not seek to end of video", flag);
        cleanup.add(reader::close);
    }

    @Test
    public void availableInputBufferQueue_isNotEmpty() throws IOException, XMPException {
        TrackedLinkedBlockingQueue<Integer> fakeAvailableInputBuffers =
                new TrackedLinkedBlockingQueue<>();
        TrackedLinkedBlockingQueue<Bundle> fakeAvailableOutputBuffers =
                new TrackedLinkedBlockingQueue<>();

        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null,
                true,
                fakeAvailableInputBuffers,
                fakeAvailableOutputBuffers
        );
        assertFalse("Available input buffer queue is empty",
                fakeAvailableInputBuffers.isEmpty());
        cleanup.add(reader::close);
    }

    @Test
    public void availableOutputBufferQueue_isQueried() throws IOException, XMPException {
        TrackedLinkedBlockingQueue<Integer> fakeAvailableInputBuffers =
                new TrackedLinkedBlockingQueue<>();
        TrackedLinkedBlockingQueue<Bundle> fakeAvailableOutputBuffers =
                new TrackedLinkedBlockingQueue<>();

        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null,
                true,
                fakeAvailableInputBuffers,
                fakeAvailableOutputBuffers
        );

        while (reader.hasNextFrame()) {
            reader.nextFrame();
        }

        assertEquals(NUM_FRAMES, fakeAvailableOutputBuffers.getOfferCount());
        assertEquals(NUM_FRAMES, fakeAvailableOutputBuffers.getPollCount());
        cleanup.add(reader::close);
    }

    @Test
    public void getMotionPhotoImage_isNotNull() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                null
        );
        Bitmap bmp = reader.getMotionPhotoImageBitmap();
        assertNotNull(bmp);
        cleanup.add(reader::close);
    }

    /** Mock LinkedBlockingQueue class to simulate and test input/output buffer queue behaviors. */
    private static class TrackedLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

        private final AtomicInteger offerCount = new AtomicInteger(0);
        private final AtomicInteger pollCount = new AtomicInteger(0);
        private final AtomicInteger size = new AtomicInteger(0);

        @Override
        public boolean offer(E e) {
            offerCount.incrementAndGet();
            size.incrementAndGet();
            return super.offer(e);
        }

        @Override
        public E poll() {
            pollCount.incrementAndGet();
            size.decrementAndGet();
            return super.poll();
        }

        @Override
        public E poll(long timeout, TimeUnit timeUnit) throws InterruptedException {
            pollCount.incrementAndGet();
            size.decrementAndGet();
            return super.poll(timeout, timeUnit);
        }

        public int getOfferCount() {
            return offerCount.get();
        }

        public int getPollCount() {
            return pollCount.get();
        }

        public boolean isEmpty() {
            return size.get() < 1;
        }
    }
}