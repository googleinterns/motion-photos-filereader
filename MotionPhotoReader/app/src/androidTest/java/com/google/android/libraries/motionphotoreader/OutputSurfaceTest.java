package com.google.android.libraries.motionphotoreader;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.adobe.internal.xmp.XMPException;
import com.google.common.io.ByteStreams;

import org.junit.After;
import org.junit.Before;
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
import static org.mockito.Mockito.mock;

/**
 * Instrumented test for MotionPhotoReader class.
 */
@RunWith(AndroidJUnit4.class)
public class OutputSurfaceTest {
    private static final String filename = "MVIMG_20200621_200240.jpg";
    private static final int NUM_FRAMES = 44;
    private static final long SEEK_AMOUNT_US = 10_000L;
    private final static int WIDTH = 4032;
    private final static int HEIGHT = 3024;


    private HandlerThread renderThread;
    private Handler renderHandler;
    private Surface surface;

    /** A list of output surfaces to release afterwards. */
    private final List<Runnable> cleanup = new ArrayList<>();

    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class);

    @Before
    public void setUp() {
        renderThread = new HandlerThread("renderThread");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        SurfaceView view = new SurfaceView(activityRule.getActivity().getApplicationContext());
        surface = new Surface(view.getSurfaceControl());
    }

    @After
    public void tearDown() {
        for (Runnable r : cleanup) {
            r.run();
        }
        cleanup.clear();
    }

    @Test
    public void setupOutputSurface_hasCorrectBehavior() {
        OutputSurface outputSurface = new OutputSurface(renderHandler, WIDTH, HEIGHT);
        cleanup.add(outputSurface::release);
        assertEquals(0, outputSurface.getGlErrors());
    }

    @Test
    public void setSurface_hasCorrectBehavior() {
        OutputSurface outputSurface = new OutputSurface(renderHandler, WIDTH, HEIGHT);
        outputSurface.setSurface(surface);
        cleanup.add(outputSurface::release);
        assertEquals(0, outputSurface.getGlErrors());
    }

    @Test
    public void getRenderSurface_isNotNull() {
        OutputSurface outputSurface = new OutputSurface(renderHandler, WIDTH, HEIGHT);
        outputSurface.setSurface(surface);
        cleanup.add(outputSurface::release);
        assertNotNull(outputSurface.getRenderSurface());
    }
}