package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaExtractor;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;

import java.io.IOException;

/**
 * An Android app widget to set up a video player for a motion photo file.
 */
public class MotionPhotoWidget extends TextureView {

    private static final String TAG = "MotionPhotoWidget";

    private SurfaceTexture surfaceTexture;
    private PlayerThread playerWorker;
    private String filename;

    private final boolean autoloop;
    private volatile SurfaceTexture savedSurfaceTexture;
    private volatile Bundle savedSurfaceState;

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
        this.setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "Surface texture available");
                if (savedSurfaceTexture == null) {
                    savedSurfaceTexture = surface;
                    if (savedSurfaceState == null) {
                        Log.d(TAG, "Surface texture created with new state");
                        playerWorker = new PlayerThread(new Surface(surface));
                    } else {
                        Log.d(TAG, "Surface texture created with saved state");
                        playerWorker = new PlayerThread(new Surface(surface), savedSurfaceState);
                    }
                }
                Log.d(TAG, "Player thread is null: " + (playerWorker == null));
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "Surface texture size changed");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(TAG, "Surface texture destroyed");
                if (playerWorker != null) {
                    Log.d(TAG, "Player thread updated");
                    savedSurfaceState = new Bundle();
                    savedSurfaceState.putBoolean("IS_PAUSED", playerWorker.isPaused());
                    savedSurfaceState.putLong("CURRENT_TIMESTAMP_US", getCurrentTimestampUs());
                }
                return (savedSurfaceTexture == null);
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        surfaceTexture = this.getSurfaceTexture();

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
     * Get the timestamp of the motion photo being played.
     * @return the current timestamp of tthe motion photo reader, in microseconds.
     */
    public long getCurrentTimestampUs() {
        return playerWorker.reader().getCurrentTimestamp();
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
        private Bundle initState;

        public PlayerThread(Surface surface) {
            Log.d(TAG, "PlayerThread created");
            this.surface = surface;
            initState = new Bundle();
            initState.putBoolean("IS_PAUSED", true);
            initState.putLong("CURRENT_TIMESTAMP_US", 0L);
            start();
        }

        public PlayerThread(Surface surface, Bundle initState) {
            Log.d(TAG, "PlayerThread created");
            this.surface = surface;
            this.initState = initState;
            start();
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        private void prepare(String filename) throws IOException, XMPException {
            Log.d(TAG, "Preparing thread");
            pause();
            if (reader != null) {
                reader.close();
            }
            reader = MotionPhotoReader.open(filename, surface);
            motionPhotoInfo = reader.getMotionPhotoInfo();
            reader.seekTo(
                    /* timeUs = */ initState.getLong("CURRENT_TIMESTAMP_US"),
                    /* mode = */ MediaExtractor.SEEK_TO_PREVIOUS_SYNC
            );
            this.paused = initState.getBoolean("IS_PAUSED");
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void run() {
            try {
                prepare(filename);
            } catch (IOException | XMPException e) {
                e.printStackTrace();
            }
            while (reader() != null) {
                if (!isPaused()) {
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
