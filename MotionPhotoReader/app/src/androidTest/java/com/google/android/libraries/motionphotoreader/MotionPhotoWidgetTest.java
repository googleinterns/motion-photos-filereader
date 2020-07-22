package com.google.android.libraries.motionphotoreader;

import android.content.Intent;
import android.content.pm.ActivityInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * Instrumented test for MotionPhotoWidget class.
 */
@RunWith(AndroidJUnit4.class)
public class MotionPhotoWidgetTest {
    private static final String filename = "MVIMG_20200621_200240.jpg";
    private static final int NUM_FRAMES = 44;
    private static final long SEEK_AMOUNT_US = 10_000L;

    /** A list of opened motion photo readers to close afterwards. */
    private final List<Runnable> cleanup = new ArrayList<>();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ActivityTestRule<WidgetTestActivity> activityRule = new ActivityTestRule<>(
            WidgetTestActivity.class,
            /* initialTouchMode = */ true,
            /* launchActivity= */ true
    );

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
    public void widgetUI_surface_isPrepared() throws InterruptedException {
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(1000);
    }

    @Test
    public void widgetUI_playPause_isCorrect() throws InterruptedException {
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(1000);
        onView(withId(R.id.motion_photo_widget)).perform(click());
        Thread.sleep(1000);
        onView(withId(R.id.motion_photo_widget)).perform(click());
    }

    @Test
    public void widgetUI_onRotation_isCorrect() throws InterruptedException {
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(1000);
        onView(withId(R.id.motion_photo_widget)).perform(click());
        Thread.sleep(1000);
        activityRule.getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(2000);
        activityRule.getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(2000);
    }
}