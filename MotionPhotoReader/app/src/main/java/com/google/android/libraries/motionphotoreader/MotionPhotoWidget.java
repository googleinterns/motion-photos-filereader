package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An Android app widget to set up a video player for a motion photo file.
 */
public class MotionPhotoWidget extends SurfaceView {

    private static final String TAG = "MotionPhotoWidget";
    private static final String filename = "/sdcard/MVIMG_20200621_200240.jpg";

    SettableFuture<Surface> surfaceFuture;
    private SurfaceHolder surfaceHolder;
    private PlayerThread playerWorker;
    private MotionPhotoReader reader;

    private Object lock = new Object();

    private final boolean autoloop;

    public MotionPhotoWidget(Context context) {
        super(context);
        autoloop = false;

        setup();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public MotionPhotoWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MotionPhotoWidget, 0, 0);

        // Fetch value of “custom:background_color”
        Color backgroundColor = Color.valueOf(ta.getColor(R.styleable.MotionPhotoWidget_background_color, Color.RED));

        // Fetch value of “custom:timeline_base_color”
        Color timelineBaseColor = Color.valueOf(ta.getColor(R.styleable.MotionPhotoWidget_timeline_base_color, Color.RED));

        // Fetch value of “custom:timeline_fill_color”
        Color timelineFillColor = Color.valueOf(ta.getColor(R.styleable.MotionPhotoWidget_timeline_fill_color, Color.RED));

        // Fetch value of “custom:autoloop”
        autoloop = ta.getBoolean(R.styleable.MotionPhotoWidget_autoloop, true);
        ta.recycle();

        setup();
    }

    private void setup() {
        surfaceFuture = SettableFuture.create();
        surfaceHolder = this.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "Surface created");
                surfaceFuture.set(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "Surface changed");
                surfaceFuture.addListener(() -> {
                    if (playerWorker == null) {
                        try {
                            playerWorker = new PlayerThread(surfaceFuture.get(1000L, TimeUnit.MILLISECONDS));
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            e.printStackTrace();
                        }
                        playerWorker.start();
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d("PlayerThreadActivity", "Surface destroyed");
                if (playerWorker != null) {
                    playerWorker.interrupt();
                }
                holder.getSurface().release();
            }
        });

    }

    public void play() {
        playerWorker.play();
    }

    public void pause() {
        playerWorker.pause();
    }

    /**
     * Reset the motion photo video to beginning.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void restart() {
        playerWorker.reader().seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
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
    public void setFile(String filename) throws IOException, XMPException, ExecutionException, InterruptedException {
        playerWorker.prepare(filename);
    }

    public boolean isPaused() {
        return playerWorker.isPaused();
    }

    /**
     * A thread reserved for running the Motion Photo Reader.
     */
    private class PlayerThread extends Thread {
        private MotionPhotoReader reader;
        private MotionPhotoInfo motionPhotoInfo;
        private Surface surface;
        private volatile boolean paused;

        public PlayerThread(Surface surface) {
            Log.d(TAG, "PlayerThread created");
            this.surface = surface;
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        private void prepare(String filename) throws IOException, XMPException {
            pause();
            if (reader != null) {
                reader.close();
            }
            reader = MotionPhotoReader.open(filename, surface);
            motionPhotoInfo = reader.getMotionPhotoInfo();
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void run() {
            while (reader() != null) {
                if (!isPaused()) {
                    boolean hasNextFrame = reader.hasNextFrame();
                    if (hasNextFrame) {
                        reader.nextFrame();
                    }
                }
            }
        }

        @Override
        public void interrupt() {
            reader.close();
        }

        public boolean isPaused() {
            return paused;
        }

        public MotionPhotoReader reader() {
            return reader;
        }

        public void pause() {
            paused = true;
        }

        public void play() {
            paused = false;
        }
    }
}
