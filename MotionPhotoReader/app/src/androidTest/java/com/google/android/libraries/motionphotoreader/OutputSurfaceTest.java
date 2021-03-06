package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.SurfaceView;

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

import static org.junit.Assert.assertNotNull;

/**
 * Instrumented test for MotionPhotoReader class.
 */
@RunWith(AndroidJUnit4.class)
public class OutputSurfaceTest {

    /**
     * Asset directory containing all motion photo files.
     */
    static final String MOTION_PHOTOS_DIR = "motionphotos/";

    private HandlerThread renderThread;
    private Handler renderHandler;
    private Surface surface;
    private MotionPhotoInfo motionPhotoInfo;
    private String[] testMotionPhotosList;
    private String filename;

    /** A list of output surfaces to release afterwards. */
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
        // Set up rendering threads
        renderThread = new HandlerThread("renderThread");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        // Set up the display surface
        SurfaceView surfaceView = activityRule.getActivity().findViewById(R.id.surface_view);
        surface = new Surface(surfaceView.getSurfaceControl());

        // Get motion photo from assets folder
        Context context = activityRule.getActivity().getApplicationContext();
        AssetManager assetManager = context.getAssets();
        try {
            testMotionPhotosList = assetManager.list(MOTION_PHOTOS_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
        filename = MOTION_PHOTOS_DIR + testMotionPhotosList[0];

        // Get motion photo info
        motionPhotoInfo = MotionPhotoInfo.newInstance(
                ResourceFetcher.fetchAssetFile(
                        context,
                        filename,
                        /* prefix = */ "test_photo",
                        /* suffix = */ ".jpg"
                )
        );
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
        OutputSurface outputSurface = new OutputSurface(renderHandler, motionPhotoInfo);
        Thread.sleep(1000);
        cleanup.add(outputSurface::release);
    }

    @Test
    public void setSurface_hasCorrectBehavior() throws InterruptedException {
        OutputSurface outputSurface = new OutputSurface(renderHandler, motionPhotoInfo);
        Thread.sleep(500);
        outputSurface.setSurface(surface, 0, 0);
        Thread.sleep(1000);
        cleanup.add(outputSurface::release);
    }

    @Test
    public void getRenderSurface_isNotNull() throws InterruptedException {
        OutputSurface outputSurface = new OutputSurface(renderHandler, motionPhotoInfo);
        Thread.sleep(500);
        outputSurface.setSurface(surface, 0, 0);
        Thread.sleep(1000);
        cleanup.add(outputSurface::release);
        assertNotNull(outputSurface.getDecodeSurface());
    }
}