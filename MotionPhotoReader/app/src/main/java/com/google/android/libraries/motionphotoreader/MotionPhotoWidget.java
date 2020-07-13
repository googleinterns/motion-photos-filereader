package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.media.MediaExtractor;
import android.os.Build;
import android.os.Bundle;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;

import androidx.annotation.GuardedBy;
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

    private Color backgroundColor;
    private final boolean autoloop;

    private static final String TAG = "MotionPhotoWidget";
    private static final Object lock = new Object();

    private ExecutorService executor;

    @GuardedBy("lock")
    private MotionPhotoReader reader;
    private boolean isPaused = true;
    private SurfaceTexture surfaceTexture;
    private String filename;

    private SurfaceTexture savedSurfaceTexture;
    private long savedTimestampUs;
    private PlayProcess playProcess;

    public MotionPhotoWidget(Context context) {
        super(context);
        setSaveEnabled(true);
        autoloop = true;

        setup();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public MotionPhotoWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setSaveEnabled(true);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MotionPhotoWidget,
                /* defStyleAttr = */ 0,
                /* defStyleRes = */ 0);

        // Fetch value of “custom:background_color”
        backgroundColor = Color.valueOf(
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
                Log.d(TAG, "Surface texture available");
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
                reader.close();
                return savedSurfaceTexture == null;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        surfaceTexture = this.getSurfaceTexture();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        // Obtain any state that our super class wants to save.
        Parcelable superState = super.onSaveInstanceState();

        // Wrap our super class's state with our own.
        SavedState myState = new SavedState(superState);
        myState.savedTimestampUs = reader.getCurrentTimestamp();
        myState.isPaused = this.isPaused;

        // Return our state along with our super class's state.
        return myState;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        // Cast the incoming Parcelable to our custom SavedState. We produced
        // this Parcelable before, so we know what type it is.
        SavedState savedState = (SavedState) state;

        // Let our super class process state before we do because we should
        // depend on our super class, we shouldn't imply that our super class
        // might need to depend on us.
        super.onRestoreInstanceState(savedState.getSuperState());

        // Grab our properties out of our SavedState.
        this.savedTimestampUs = savedState.savedTimestampUs;
        this.isPaused = savedState.isPaused;
    }

    public void play() {
        playProcess = new PlayProcess();
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

    private static class SavedState extends BaseSavedState {
        long savedTimestampUs;
        boolean isPaused;

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        SavedState(Parcelable superState) {
            super(superState);
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        private SavedState(Parcel in) {
            super(in);
            savedTimestampUs = in.readLong();
            isPaused = in.readBoolean();
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeLong(savedTimestampUs);
            out.writeBoolean(isPaused);
        }
    }
}
