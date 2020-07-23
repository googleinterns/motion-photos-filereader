package com.google.android.libraries.motionphotoreader;

import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.SurfaceView;

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
    private MotionPhotoInfo motionPhotoInfo;

    /** A list of output surfaces to release afterwards. */
    private final List<Runnable> cleanup = new ArrayList<>();

    @Rule
    public ActivityTestRule<OutputSurfaceTestActivity> activityRule =
            new ActivityTestRule<>(
                    OutputSurfaceTestActivity.class,
                    /* initialTouchMode = */ true,
                    /* launchActivity= */ true
            );

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException, XMPException {
        // Set up rendering threads
        renderThread = new HandlerThread("renderThread");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        SurfaceView surfaceView = activityRule.getActivity().findViewById(R.id.surface_view);
        surface = new Surface(surfaceView.getSurfaceControl());

        // Get motion photo info
        motionPhotoInfo = MotionPhotoInfo.newInstance(fetchAssetFile(filename));
    }

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