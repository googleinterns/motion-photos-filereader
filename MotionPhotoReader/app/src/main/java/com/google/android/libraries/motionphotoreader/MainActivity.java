package com.google.android.libraries.motionphotoreader;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String[] FILENAMES = { // replace these with appropriate file access
            "20200621_200240",
            "20200616_124008",
            "20200621_184700"
    };

    private String filename;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        filename = "/sdcard/MVIMG_" + FILENAMES[0] + ".jpg";
        MotionPhotoWidget motionPhotoWidget = findViewById(R.id.motion_photo_widget);
        Log.d(TAG, "Motion photo widget set up");
        motionPhotoWidget.setFile(filename);
        Log.d(TAG, "Set file to " + filename);
        motionPhotoWidget.setOnClickListener(new MyOnWidgetClickListener(motionPhotoWidget));
    }

    private class MyOnWidgetClickListener implements View.OnClickListener {
        private MotionPhotoWidget motionPhotoWidget;

        public MyOnWidgetClickListener(MotionPhotoWidget motionPhotoWidget) {
            this.motionPhotoWidget = motionPhotoWidget;
        }

        @RequiresApi(api = 28)
        @Override
        public void onClick(View v) {
            if (motionPhotoWidget.isPaused()) {
                motionPhotoWidget.play();
            } else {
                motionPhotoWidget.pause();
            }
        }
    }
}
