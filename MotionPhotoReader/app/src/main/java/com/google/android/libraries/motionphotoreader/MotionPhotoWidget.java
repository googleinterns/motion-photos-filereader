package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaExtractor;
import android.os.Build;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.adobe.internal.xmp.XMPException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * An Android app widget to set up a video player for a motion photo file.
 */
public class MotionPhotoWidget extends TextureView {

    private static final String TAG = "MotionPhotoWidget";

    private final boolean autoloop;
    private ExecutorService executor;
    private MotionPhotoReader reader;
    private boolean isPaused = true;
    private String filename;
    private SurfaceTexture savedSurfaceTexture;
    private PlayProcess playProcess;

    /** Fields that are saved for the view state. */
    private long savedTimestampUs;
    private int videoWidth;
    private int videoHeight;
    private int videoRotation;
    private int viewWidth;
    private int viewHeight;

    public MotionPhotoWidget(Context context) {
        super(context);
        autoloop = true;
        setup();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public MotionPhotoWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MotionPhotoWidget,
                /* defStyleAttr = */ 0,
                /* defStyleRes = */ 0);

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

    /**
     * Sets up the executor, the play/pause process to be executed, and the surface texture
     * listener. This should only be called in a constructor, and should be called in every
     * constructor.
     */
    private void setup() {
        // Set up the executor and play/pause process to facilitate stopping and starting the video
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
        this.setSurfaceTextureListener(new WidgetSurfaceTextureListener());
    }

    @Override
    public Parcelable onSaveInstanceState() {
        // Obtain any state that the super class wants to save
        Parcelable superState = super.onSaveInstanceState();

        // Wrap our super class's state with our own
        SavedState myState = new SavedState(superState);
        myState.savedTimestampUs = reader.getCurrentTimestamp();
        myState.isPaused = this.isPaused;

        return myState;
    }


    @Override
    public void onRestoreInstanceState(Parcelable state) {
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
        return reader.getCurrentTimestamp();
    }

    /**
     * Set the motion photo file to a specified file.
     * @param filename is a string pointing to the motion photo file to play.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setFile(String filename) throws IOException, XMPException {
        this.filename = filename;
        MotionPhotoInfo motionPhotoInfo = MotionPhotoInfo.newInstance(filename);
        videoWidth = motionPhotoInfo.getWidth();
        videoHeight = motionPhotoInfo.getHeight();
        videoRotation = motionPhotoInfo.getRotation();
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

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private class WidgetSurfaceTextureListener implements SurfaceTextureListener {

        /**
         * Scales the video to fit inside the view.
         * @param viewWidth The width of the surface texture, in pixels.
         * @param viewHeight The height of the surface texture, in pixels.
         */
        private void adjustAspectRation(int viewWidth, int viewHeight) {
            double aspectRatio = (videoRotation > 0) ?
                    (double) videoWidth / videoHeight : (double) videoHeight / videoWidth;
            Log.d(TAG, "asepct ratio: " + aspectRatio);
            int newVideoWidth, newVideoHeight;
            if (viewHeight > (int) (viewWidth * aspectRatio)) {
                // limited by narrow width, sp restrict height
                newVideoWidth = viewWidth;
                newVideoHeight = (int) (viewWidth * aspectRatio);
            } else {
                // limited by short height, so restrict width
                newVideoWidth = (int) (viewHeight / aspectRatio);
                newVideoHeight = viewHeight;
            }
            int xOffset = (viewWidth - newVideoWidth) / 2;
            int yOffset = (viewHeight - newVideoHeight) / 2;

            // set transformation matrix to apply to videos played to the surface texture
            Matrix txform = new Matrix();
            MotionPhotoWidget.this.getTransform(txform);
            txform.setScale(
                    (float) newVideoWidth / viewWidth,
                    (float) newVideoHeight / viewHeight
            );
            txform.postTranslate(xOffset, yOffset);
            MotionPhotoWidget.this.setTransform(txform);
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "Surface texture available");
            if (savedSurfaceTexture == null) {
                savedSurfaceTexture = surface;

                // scale the video to fit in the texture view
                viewWidth = width;
                viewHeight = height;
                adjustAspectRation(viewWidth, viewHeight);

                // create a new motion photo reader
                try {
                    reader = MotionPhotoReader.open(filename, new Surface(surface));
                } catch (IOException | XMPException e) {
                    e.printStackTrace();
                }

                // restore the previous state
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
            return (savedSurfaceTexture == null);
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    }
}
