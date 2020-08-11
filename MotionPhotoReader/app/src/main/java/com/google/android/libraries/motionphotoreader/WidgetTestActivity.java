package com.google.android.libraries.motionphotoreader;

import android.app.Activity;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;

import java.io.IOException;
import java.util.Arrays;

@RequiresApi(api = 29)
public class WidgetTestActivity extends Activity {

    private static final String TAG = "MainActivity";

    /**
     * Asset directory containing all motion photo files.
     */
    static final String MOTION_PHOTOS_DIR = "motionphotos/";

    private String[] testMotionPhotosList;
    private int currPhotoIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_test);

        AssetManager assetManager = getAssets();
        try {
            testMotionPhotosList = assetManager.list("motionphotos");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "test image array: " + Arrays.toString(testMotionPhotosList));

        // Get the widget and set the default file to v1 motion photo
        MotionPhotoWidget motionPhotoWidget = findViewById(R.id.motion_photo_widget);
        Log.d(TAG, "Motion photo widget set up");
        try {
            motionPhotoWidget.setFile(
                    ResourceFetcher.fetchAssetFile(
                            this.getApplicationContext(),
                            MOTION_PHOTOS_DIR + testMotionPhotosList[currPhotoIndex],
                            /* prefix = */ testMotionPhotosList[currPhotoIndex],
                            /* suffix = */ "jpg"
                    )
            );
        } catch (IOException | XMPException e) {
            Log.e(TAG, "Unable to set widget file", e);
        }

        // Clicking the widget will play or pause the video
        motionPhotoWidget.setOnClickListener((View v) -> {
            if (motionPhotoWidget.isPaused()) {
                motionPhotoWidget.play();
            } else {
                motionPhotoWidget.pause();
            }
        });

        // Holding down on the widget will switch between the two motion photo files (one is v1 and
        // the other is v2)
        motionPhotoWidget.setOnLongClickListener((View v) -> {
            try {
                currPhotoIndex = (currPhotoIndex + 1) % testMotionPhotosList.length;
                motionPhotoWidget.setFile(
                        ResourceFetcher.fetchAssetFile(
                                this.getApplicationContext(),
                                MOTION_PHOTOS_DIR + testMotionPhotosList[currPhotoIndex],
                                /* prefix = */ testMotionPhotosList[currPhotoIndex],
                                /* suffix = */ ".jpg"
                        )
                );
                return true;
            } catch (IOException | XMPException e) {
                Log.e(TAG, "Unable to set widget file", e);
            }
            return false;
        });
    }
}