package com.google.android.libraries.motionphotoreader;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WidgetTestActivity extends Activity {

    private static final String TAG = "MainActivity";

    private boolean isV1;

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_test);

        // Get the widget and set the default file to v1 motion photo
        MotionPhotoWidget motionPhotoWidget = findViewById(R.id.motion_photo_widget);
        Log.d(TAG, "Motion photo widget set up");
        try {
            motionPhotoWidget.setFile(fetchRawFile(R.raw.v1, "motionPhotoV1", "jpg"));
            isV1 = true;
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
            if (isV1) {
                Log.d(TAG, "Is V1");
                try {
                    motionPhotoWidget.setFile(fetchRawFile(
                            R.raw.v2,
                            /* prefix = */ "motionPhotoV2",
                            /* suffix = */ ".jpg")
                    );
                    isV1 = false;
                    return true;
                } catch (IOException | XMPException e) {
                    Log.e(TAG, "Unable to set widget file", e);
                }
            } else {
                try {
                    motionPhotoWidget.setFile(fetchRawFile(
                            R.raw.v1,
                            /* prefix = */ "motionPhotoV1",
                            /* suffix = */ ".jpg")
                    );
                    isV1 = true;
                    return true;
                } catch (IOException | XMPException e) {
                    Log.e(TAG, "Unable to set widget file", e);
                }
            }
            return false;
        });
    }

    /**
     * Fetches a resource from the res/raw folder and creates a temporary file holding the resource.
     * Used to fetch motion photo files.
     * @param id The id of the file.
     * @param prefix The name of the temporary file to create from the raw resource.
     * @param suffix The extension of the temporary file to create from the raw resource.
     * @return a File object containing the raw resource file.
     */
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