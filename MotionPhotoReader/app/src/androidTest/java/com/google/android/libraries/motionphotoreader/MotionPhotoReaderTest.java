package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.adobe.internal.xmp.XMPException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
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

    /**
     * Asset directory containing all motion photo files.
     */
    static final String MOTION_PHOTOS_DIR = "motionphotos/";

    private static final int NUM_FRAMES = 43;
    private static final long SEEK_AMOUNT_US = 10_000L;

    private Context context;
    private String[] testMotionPhotosList;
    private String filename;
    private TrackedLinkedBlockingQueue<Integer> fakeInputBufferQueue;
    private TrackedLinkedBlockingQueue<Bundle> fakeOutputBufferQueue;
    private MediaExtractor extractor;
    private MotionPhotoReader reader;

    /** A list of opened motion photo readers to close afterwards. */
    private final List<Runnable> cleanup = new ArrayList<>();

    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(
                    MainActivity.class,
                    /* initialTouchMode = */ true,
                    /* launchActivity= */ true
            );

    @Before 
    public void setUp() throws IOException, XMPException {
        context = activityRule.getActivity().getApplicationContext();
        AssetManager assetManager = context.getAssets();
        testMotionPhotosList = assetManager.list("motionphotos");
        filename = MOTION_PHOTOS_DIR + testMotionPhotosList[0];
        
        // Prepare fake buffer queues
        fakeInputBufferQueue = new TrackedLinkedBlockingQueue<>();
        fakeOutputBufferQueue = new TrackedLinkedBlockingQueue<>();

        // Set up a media extractor
        extractor = new MediaExtractor();

        // Set up motion photo reader
        reader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, filename, "test_photo", ".jpg"),
                extractor,
                /* surface = */ null,
                /* surfaceWidth = */ 0,
                /* surfaceHeight = */ 0,
                /* enableStabilization = */ true,
                /* enableCrop = */ true,
                fakeInputBufferQueue,
                fakeOutputBufferQueue
        );
        cleanup.add(reader::close);
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
        MotionPhotoReader badReader = MotionPhotoReader.open(
                ResourceFetcher.fetchAssetFile(context, "filename", "test_photo", ".jpg"),
                extractor,
                /* surface = */ null,
                /* surfaceWidth = */ 0,
                /* surfaceHeight = */ 0,
                /* enableStabilization = */ true,
                /* enableCrop = */ true,
                fakeInputBufferQueue,
                fakeOutputBufferQueue
        );
        cleanup.add(badReader::close);
    }

    @Test
    public void numberOfFramesPlayed_isCorrect() {
        int frameCount = 0;
        while (reader.hasNextFrame()) {
            reader.nextFrame();
            frameCount++;
        }
        assertEquals(NUM_FRAMES, frameCount);
    }

    @Test
    public void getCurrentTimestamp_onStart_isCorrect() {
        assertEquals(0, reader.getCurrentTimestampUs());
    }

    @Test
    public void getCurrentTimestamp_nextFrame_isCorrect() {
        long currentTimestampUs = -1L;
        while (reader.hasNextFrame()) {
            long newTimestampUs = reader.getCurrentTimestampUs();
            reader.nextFrame();
            boolean timeIncreased = currentTimestampUs < newTimestampUs;
            assertTrue("Timestamp did not increase: "
                    + currentTimestampUs + " vs. " + newTimestampUs,
                    timeIncreased);
            currentTimestampUs = newTimestampUs;
        }
    }

    @Test
    public void getCurrentTimestamp_seekTo_isCorrect() {
        long currentTimestampUs = -1L;
        while (reader.hasNextFrame()) {
            long newTimestampUs = reader.getCurrentTimestampUs();
            reader.seekTo(reader.getCurrentTimestampUs() + SEEK_AMOUNT_US,
                    MediaExtractor.SEEK_TO_NEXT_SYNC);
            boolean timeIncreased = currentTimestampUs < newTimestampUs;
            assertTrue("Timestamp did not increase: "
                    + currentTimestampUs + " vs. " + newTimestampUs,
                    timeIncreased);
            currentTimestampUs = newTimestampUs;
        }
    }

    @Test
    public void hasNextFrame_atBeginningOfVideo_returnsTrue() {
        assertTrue("No next frame", reader.hasNextFrame());
    }

    @Test
    public void hasNextFrame_atLastFrame_returnsFalse() throws IOException, XMPException {
        long timestampUs = reader.getMotionPhotoInfo().getDurationUs();
        reader.seekTo(timestampUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
        assertFalse("Did not seek to end of video", reader.hasNextFrame());
    }


    @Test
    public void availableInputBufferQueue_isNotEmpty() {
        assertGreaterOrEqual(NUM_FRAMES, fakeInputBufferQueue.getOfferCount());
        assertGreaterOrEqual(NUM_FRAMES, fakeInputBufferQueue.getPollCount());
        cleanup.add(reader::close);
    }

    @Test
    public void availableOutputBufferQueue_isQueried() {
        while (reader.hasNextFrame()) {
            reader.nextFrame();
        }

        assertEquals(NUM_FRAMES, fakeOutputBufferQueue.getOfferCount());
        assertEquals(NUM_FRAMES, fakeOutputBufferQueue.getPollCount());
        cleanup.add(reader::close);
    }

    @Test
    public void getMotionPhotoImage_isNotNull() throws IOException {
        Bitmap bmp = reader.getMotionPhotoImageBitmap();
        assertNotNull(bmp);
        cleanup.add(reader::close);
    }

    private static boolean assertGreaterOrEqual(int expected, int actual) {
        return actual >= expected;
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

        public int size() {
            return size.get();
        }

        public boolean isEmpty() {
            return size.get() < 1;
        }
    }
}