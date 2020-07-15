package com.google.android.libraries.motionphotoreader;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.adobe.internal.xmp.XMPException;
import com.google.common.io.ByteStreams;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /** A list of opened motion photo readers to close afterwards. */
    private final List<Runnable> cleanup = new ArrayList<>();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void tearDown() {
        for (Runnable r : cleanup) {
            r.run();
        }
        cleanup.clear();
    }

    private File fetchAssetFile(String filename) throws IOException {
        InputStream input = InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getResources()
                .getAssets()
                .open(filename);

        // Write file to temporary folder for instrumentation test access
        File f = temporaryFolder.newFile(filename);
        writeBytesToFile(input, f);
        return f;
    }

    private static void writeBytesToFile(InputStream input, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteStreams.copy(input, fos);
        }
    }

    @Test(expected = IOException.class)
    public void openMotionPhotoReader_invalidFile_throwsIOException()
            throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open("test_photo.jpg", null);
        cleanup.add(reader::close);
    }

    @Test
    public void numberOfFramesPlayed_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(fetchAssetFile(filename), null);
        cleanup.add(reader::close);

        int frameCount = 0;
        long baseTimestampUs = 0;
        while (reader.hasNextFrame()) {
            reader.nextFrame(baseTimestampUs);
            frameCount++;
        }
        assertEquals(NUM_FRAMES, frameCount);
    }

    @Test
    public void getCurrentTimestamp_onStart_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(fetchAssetFile(filename), null);
        cleanup.add(reader::close);
        assertEquals(0, reader.getCurrentTimestampUs());
    }

    @Test
    public void getCurrentTimestamp_nextFrame_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(fetchAssetFile(filename), null);
        cleanup.add(reader::close);

        long currentTimestampUs = -1L;
        long baseTimestampUs = 0;
        while (reader.hasNextFrame()) {
            long newTimestampUs = reader.getCurrentTimestampUs();
            reader.nextFrame(baseTimestampUs);
            boolean flag = currentTimestampUs < newTimestampUs;
            assertTrue("Timestamp did not increase: "
                    + currentTimestampUs + " vs. " + newTimestampUs,
                    flag);
            currentTimestampUs = newTimestampUs;
        }
    }

    @Test
    public void getCurrentTimestamp_seekTo_isCorrect() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(fetchAssetFile(filename), null);
        cleanup.add(reader::close);

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
    }

    @Test
    public void hasNextFrame_atBeginningOfVideo_returnsTrue() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(fetchAssetFile(filename), null);
        cleanup.add(reader::close);
        boolean flag = reader.hasNextFrame();
        assertTrue("No next frame", flag);
    }

    @Test
    public void hasNextFrame_atLastFrame_returnsFalse() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(fetchAssetFile(filename), null);
        cleanup.add(reader::close);

        long timestampUs = reader.getMotionPhotoInfo().getDuration();
        reader.seekTo(timestampUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
        boolean flag = reader.hasNextFrame();
        assertFalse("Did not seek to end of video", flag);
    }

    @Test
    public void availableInputBufferQueue_isNotEmpty() throws IOException, XMPException {
        TrackedLinkedBlockingQueue<Integer> fakeAvailableInputBuffers =
                new TrackedLinkedBlockingQueue<>();
        TrackedLinkedBlockingQueue<Bundle> fakeAvailableOutputBuffers =
                new TrackedLinkedBlockingQueue<>();

        MotionPhotoReader reader = MotionPhotoReader.open(
                fetchAssetFile(filename),
                null,
                fakeAvailableInputBuffers,
                fakeAvailableOutputBuffers
        );
        cleanup.add(reader::close);

        assertFalse("Available input buffer queue is empty",
                fakeAvailableInputBuffers.isEmpty());
    }

    @Test
    public void availableOutputBufferQueue_isQueried() throws IOException, XMPException {
        TrackedLinkedBlockingQueue<Integer> fakeAvailableInputBuffers =
                new TrackedLinkedBlockingQueue<>();
        TrackedLinkedBlockingQueue<Bundle> fakeAvailableOutputBuffers =
                new TrackedLinkedBlockingQueue<>();

        MotionPhotoReader reader = MotionPhotoReader.open(
                fetchAssetFile(filename),
                null,
                fakeAvailableInputBuffers,
                fakeAvailableOutputBuffers
        );
        cleanup.add(reader::close);

        long baseTimestampUs = 0;
        while (reader.hasNextFrame()) {
            reader.nextFrame(baseTimestampUs);
        }

        assertEquals(NUM_FRAMES, fakeAvailableOutputBuffers.getOfferCount());
        assertEquals(NUM_FRAMES, fakeAvailableOutputBuffers.getPollCount());
    }

    @Test
    public void getMotionPhotoImage_isNotNull() throws IOException, XMPException {
        MotionPhotoReader reader = MotionPhotoReader.open(fetchAssetFile(filename), null);
        cleanup.add(reader::close);
        Bitmap bmp = reader.getMotionPhotoImageBitmap();
        assertNotNull(bmp);
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