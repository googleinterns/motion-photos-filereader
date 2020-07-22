package com.google.android.libraries.motionphotoreader;

import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

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
    public ActivityTestRule<OutputSurfaceTestActivity> activityRule =
            new ActivityTestRule<>(
                    OutputSurfaceTestActivity.class,
                    /* initialTouchMode = */ true,
                    /* launchActivity= */ true
            );

    @Before
    public void setUp() {
        renderThread = new HandlerThread("renderThread");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        SurfaceView surfaceView = activityRule.getActivity().findViewById(R.id.surface_view);
        surface = new Surface(surfaceView.getSurfaceControl());
    }

    @After
    public void tearDown() {
        for (Runnable r : cleanup) {
            r.run();
        }
        cleanup.clear();
    }

    @Test
    public void setupOutputSurface_hasCorrectBehavior() throws InterruptedException {
        OutputSurface outputSurface = new OutputSurface(renderHandler, WIDTH, HEIGHT);
        Thread.sleep(1000);
        cleanup.add(outputSurface::release);
        assertEquals(0, outputSurface.getGlErrors());
    }

    @Test
    public void setSurface_hasCorrectBehavior() throws InterruptedException {
        OutputSurface outputSurface = new OutputSurface(renderHandler, WIDTH, HEIGHT);
        Thread.sleep(500);
        outputSurface.setSurface(surface);
        Thread.sleep(1000);
        cleanup.add(outputSurface::release);
        assertEquals(0, outputSurface.getGlErrors());
    }

    @Test
    public void getRenderSurface_isNotNull() throws InterruptedException {
        OutputSurface outputSurface = new OutputSurface(renderHandler, WIDTH, HEIGHT);
        Thread.sleep(500);
        outputSurface.setSurface(surface);
        Thread.sleep(1000);
        cleanup.add(outputSurface::release);
        assertNotNull(outputSurface.getDecodeSurface());
    }
}