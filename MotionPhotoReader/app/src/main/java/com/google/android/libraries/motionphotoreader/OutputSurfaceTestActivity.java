package com.google.android.libraries.motionphotoreader;

import android.app.Activity;
import android.media.MediaExtractor;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class OutputSurfaceTestActivity extends Activity {

    private static final String TAG = "OutputSurfaceTestActiv";

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_output_surface_test);
    }
}
