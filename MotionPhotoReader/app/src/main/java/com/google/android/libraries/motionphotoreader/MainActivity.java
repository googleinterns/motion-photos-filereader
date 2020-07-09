package com.google.android.libraries.motionphotoreader;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;

import java.io.IOException;

public class MainActivity extends Activity {

    private final static String[] FILENAMES = { // replace these with appropriate names from sdcard
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
        motionPhotoWidget.setOnClickListener(new MyOnWidgetClickListener(motionPhotoWidget));
//        try {
//            motionPhotoWidget.setFile(filename);
//            Log.d("MainActivity", "Set file to " + filename);
//        } catch (IOException | XMPException e) {
//            Log.e("MainActivity", "Could not set file", e);
//        }
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
