package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaExtractor;
import android.opengl.GLSurfaceView;
import android.os.Build;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An Android app widget to set up a video player for a motion photo file.
 */
public class MotionPhotoWidget extends GLSurfaceView {

    private static final String TAG = "MotionPhotoWidget";

    private final boolean autoloop;
    private final boolean fill;

    private ExecutorService executor;
    private MotionPhotoReader reader;
    private String filename;
    private SurfaceHolder surfaceHolder;
    private PlayProcess playProcess;

    /** Fields that are saved for the view state. */
    private long savedTimestampUs;
    private boolean isPaused = true;

    /** OpenGL fields. */
    private boolean rendererSet;

    @RequiresApi(api = Build.VERSION_CODES.O)
    public MotionPhotoWidget(Context context) {
        super(context);
        autoloop = true;
        fill = false;
        initialize();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public MotionPhotoWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MotionPhotoWidget,
                /* defStyleAttr = */ 0,
                /* defStyleRes = */ 0);

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
        // Fetch value of “custom:autoloop”
        fill = ta.getBoolean(R.styleable.MotionPhotoWidget_fill, false);
        ta.recycle();
        initialize();
    }

    /**
     * Sets up the executor, the play/pause process to be executed, and the surface texture
     * listener. This should only be called in a constructor, and should be called in every
     * constructor.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initialize() {
        // Set up the executor and play/pause process to facilitate stopping and starting the video
        playProcess = new PlayProcess();

        surfaceHolder = this.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "Surface created");
            }

            @RequiresApi(api = 23)
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "Surface changed");
                Log.d(TAG, "Surface changed");

                // create a new motion photo reader
                try {
                    reader = MotionPhotoReader.open(filename, holder.getSurface());
                    Log.d(TAG, "New motion photo reader created");
                } catch (IOException | XMPException e) {
                    Log.e(TAG, "Exception occurred while opening file", e);
                }

                // continue playing the video if not in paused state
                reader.seekTo(savedTimestampUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                if (!isPaused) {
                    play();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "Surface destroyed");
                playProcess.cancel();
            }
        });
    }

    private void eglSetup() {
        // Request an OpenGL ES 2.0 compatible context
        this.setEGLContextClientVersion(3);

        // Assign the renderer
        this.setRenderer(new WidgetOpenGLRenderer());
        rendererSet = true;
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        switch (visibility) {
            case VISIBLE:
                Log.d(TAG, "View is visible");
                break;
            case INVISIBLE:
            case GONE:
                Log.d(TAG, "View is invisible or gone");
                reader.close();
                break;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.d(TAG, "View attached");
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d(TAG, "View detached");
        executor.shutdown();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Log.d(TAG, "Saving instance state");
        // Obtain any state that the super class wants to save
        Parcelable superState = super.onSaveInstanceState();

        // Wrap our super class's state with our own
        SavedState myState = new SavedState(superState);
        myState.savedTimestampUs = reader.getCurrentTimestampUs();
        myState.isPaused = this.isPaused;

        return myState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        Log.d(TAG, "Restoring instance state");
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        // Grab properties out of the SavedState
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
        return reader.getCurrentTimestampUs();
    }

    /**
     * Set the motion photo file to a specified file.
     * @param filename is a string pointing to the motion photo file to play.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setFile(String filename) throws IOException, XMPException {
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
                if (reader.hasNextFrame()) {
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

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
