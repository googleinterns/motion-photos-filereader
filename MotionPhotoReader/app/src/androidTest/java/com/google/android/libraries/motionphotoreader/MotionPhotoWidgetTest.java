package com.google.android.libraries.motionphotoreader;

import android.content.pm.ActivityInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * Instrumented test for MotionPhotoWidget class.
 */
@RunWith(AndroidJUnit4.class)
public class MotionPhotoWidgetTest {

    /** A list of opened motion photo readers to close afterwards. */
    private final List<Runnable> cleanup = new ArrayList<>();

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

    @Test
    public void widgetUI_surface_isPrepared() throws InterruptedException {
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(1000);
    }

    @Test
    public void widgetUI_playPause_isCorrect() throws InterruptedException {
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(1000);
        // Video should play
        onView(withId(R.id.motion_photo_widget)).perform(click());
        Thread.sleep(1000);
        // Video should pause
        onView(withId(R.id.motion_photo_widget)).perform(click());
    }

    @Test
    public void widgetUI_onRotation_isCorrect() throws InterruptedException {
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(1000);
        // Video should play
        onView(withId(R.id.motion_photo_widget)).perform(click());
        Thread.sleep(1000);
        // Video should continue playing in landscape mode, starting from where the video left off
        // as the screen was rotated
        activityRule
                .getActivity()
                .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(2000);
        // Video should continue playing in portrait mode, starting from where the video left off
        // as the screen was rotated
        activityRule
                .getActivity()
                .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        onView(withId(R.id.motion_photo_widget)).check(matches(isDisplayed()));
        Thread.sleep(2000);
    }

    @Test
    public void widgetUI_setFile_isCorrect() throws InterruptedException {
        Thread.sleep(1000);
        // Video file should change to new video
        onView(withId(R.id.motion_photo_widget)).perform(longClick());
        Thread.sleep(1000);
        // Video should play
        onView(withId(R.id.motion_photo_widget)).perform(click());
        Thread.sleep(2000);
        // Video file should change back to original video
        onView(withId(R.id.motion_photo_widget)).perform(longClick());
        Thread.sleep(2000);
    }

    @Test
    public void widgetUI_setFileOnRotation_isCorrect() throws InterruptedException {
        Thread.sleep(1000);
        // Video file should change to new video
        onView(withId(R.id.motion_photo_widget)).perform(longClick());
        Thread.sleep(1000);
        // Video should play
        onView(withId(R.id.motion_photo_widget)).perform(click());
        Thread.sleep(1000);
        // Video should continue playing in landscape mode, starting from where the video left off
        // as the screen was rotated
        activityRule
                .getActivity()
                .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        Thread.sleep(1000);
        // Video should change to new file
        onView(withId(R.id.motion_photo_widget)).perform(longClick());
        Thread.sleep(1000);
        // New video should begin playing
        onView(withId(R.id.motion_photo_widget)).perform(click());
        Thread.sleep(1000);
        // Video should continue playing in portrait mode, starting from where the video left off
        // as the screen was rotated
        activityRule
                .getActivity()
                .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        Thread.sleep(1000);
    }
}