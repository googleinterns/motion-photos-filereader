package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaExtractor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * An Android app widget to set up a video player for a motion photo file.
 */
public class MotionPhotoWidget extends TextureView {

    private static final String TAG = "MotionPhotoWidget";
    private static final Object lock = new Object();

    private ExecutorService executor;

    private MotionPhotoReader reader;
    private boolean isPaused = true;
    private SurfaceTexture surfaceTexture;
    private String filename;

    private final boolean autoloop;
    private SurfaceTexture savedSurfaceTexture;
    private long savedTimestampUs;
    private PlayProcess playProcess;

    public MotionPhotoWidget(Context context) {
        super(context);
        autoloop = true;

        setup();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public MotionPhotoWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MotionPhotoWidget, 0, 0);

        // Fetch value of “custom:background_color”
        Color backgroundColor = Color.valueOf(
                ta.getColor(R.styleable.MotionPhotoWidget_background_color, Color.RED)
        );

        // Fetch value of “custom:timeline_base_color”
        Color timelineBaseColor = Color.valueOf(
                ta.getColor(R.styleable.MotionPhotoWidget_timeline_base_color, Color.RED)
        );

        // Fetch value of “custom:timeline_fill_color”
        Color timelineFillColor = Color.valueOf(
                ta.getColor(R.styleable.MotionPhotoWidget_timeline_fill_color, Color.RED)
        );

        // Fetch value of “custom:autoloop”
        autoloop = ta.getBoolean(R.styleable.MotionPhotoWidget_autoloop, true);
        ta.recycle();
        setup();
    }

    private void setup() {
        playProcess = new PlayProcess();
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r) {
                    @Override
                    public void interrupt() {
                        super.interrupt();
                        playProcess.cancel();
                    }
                };
            }
        });
        
        this.setSurfaceTextureListener(new SurfaceTextureListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (savedSurfaceTexture == null) {
                    savedSurfaceTexture = surface;
                    if (reader == null) {
                        Log.d(TAG, "Opening new motion photo reader");
                        try {
                            reader = MotionPhotoReader.open(filename, new Surface(surface));
                        } catch (IOException | XMPException e) {
                            e.printStackTrace();
                        }
                    }
                    // set reader to last saved state
                    Log.d(TAG, "Seeking to: " + savedTimestampUs + " us");
                    reader.seekTo(savedTimestampUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    if (!isPaused) {
                        executor.submit(playProcess);
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "Surface texture size changed");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.d(TAG, "Surface texture destroyed");
                savedTimestampUs = getCurrentTimestampUs();
                return (savedSurfaceTexture == null);
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        surfaceTexture = this.getSurfaceTexture();

    }

    public void play() {
        executor.submit(playProcess);
        isPaused = false;
    }

    public void pause() {
        playProcess.cancel();
        isPaused = true;
    }

    /**
     * Reset the motion photo video to beginning.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void restart() {
        reader.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
    }

    /**
     * Get the timestamp of the motion photo being played.
     * @return the current timestamp of tthe motion photo reader, in microseconds.
     */
    public long getCurrentTimestampUs() {
        return reader.getCurrentTimestamp();
    }

    /**
     * Show the motion photo JPEG image on the widget surface.
     * TODO: specify behavior when called while video is playing.
     */
    public void showPreview() {

    }

    /**
     * Set the motion photo file to a specified file.
     * @param filename is a string pointing to the motion photo file to play.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setFile(String filename) {
        this.filename = filename;
    }

    public boolean isPaused() {
        return isPaused;
    }

    /**
     * A Runnable for starting up the player.
     */

    private class PlayProcess implements Runnable {
        private volatile boolean exit;

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void run() {
            // start playing video
            while (!exit) {
                boolean hasNextFrame = reader.hasNextFrame();
                if (hasNextFrame) {
                    reader.nextFrame();
                } else {
                    if (autoloop) {
                        reader.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    }
                }
            }
        }

        public void cancel() {
            exit = true;
        }
    }
}
