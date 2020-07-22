package com.google.android.libraries.motionphotoreader;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WidgetTestActivity extends Activity {
    private static final String TAG = "WidgetTestActivity";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_test);

        MotionPhotoWidget motionPhotoWidget = findViewById(R.id.motion_photo_widget);
        Log.d(TAG, "Motion photo widget set up");
        motionPhotoWidget.setFile(fetchRawFile(R.raw.v1, "motionPhotoV1", "jpg"));

        // Add play/pause functionality on tap
        motionPhotoWidget.setOnClickListener((View v) -> {
            if (motionPhotoWidget.isPaused()) {
                motionPhotoWidget.play();
            } else {
                motionPhotoWidget.pause();
            }
        });
    }

    private File fetchRawFile(int id, String prefix, String suffix) {
        InputStream input = getResources().openRawResource(id);
        File file = null;
        try {
            file = File.createTempFile(prefix, suffix);
        } catch (IOException e) {
            Log.e(TAG, "Error fetching raw file", e);
        }
        writeBytesToFile(input, file);
        return file;
    }

    private static void writeBytesToFile(InputStream input, File file) {
        try (OutputStream output = new FileOutputStream(file)) {
            ByteStreams.copy(input, output);
        } catch (IOException e) {
            Log.e(TAG, "Error writing file", e);
        }
    }
}
