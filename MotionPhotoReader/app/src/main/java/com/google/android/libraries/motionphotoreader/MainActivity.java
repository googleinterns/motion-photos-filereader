package com.google.android.libraries.motionphotoreader;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ToggleButton;

import com.adobe.internal.xmp.XMPException;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    // motion photo player settings
    private final boolean LOOP = false; /** Loop the video **/
    private final long SEEK_AMOUNT_US = 100_000L;

    private final static String[] FILENAMES = { // replace these with appropriate names from sdcard
            "20200621_200240",
            "20200616_124008",
            "20200621_184700"
    };

    // TODO: remove 'm' prefix
    private String filename;
    private SurfaceView videoSurfaceView;
    private SurfaceHolder surfaceHolder;
    private PlayerThread mPlayerWorker;
    private ToggleButton buttonPlayPause;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        filename = "/sdcard/MVIMG_" + FILENAMES[0] + ".jpg";

        // Hide action bar
        try {
            this.getSupportActionBar().hide();
        } catch (NullPointerException e)  {
            e.printStackTrace();
        }

        // Add play/pause button
        buttonPlayPause = findViewById(R.id.button_play_pause);
        buttonPlayPause.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // The toggle is enabled
                mPlayerWorker.play();
            } else {
                // The toggle is disabled
                mPlayerWorker.pause();
            }
        });

        // Add replay button
        ImageButton buttonReplay = findViewById(R.id.button_replay);
        buttonReplay.setOnClickListener(v -> mPlayerWorker.reader().seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC));

        // Add seek backwards button
        ImageButton buttonSeekBackwards = findViewById(R.id.button_seek_backwards);
        buttonSeekBackwards.setOnClickListener(v -> {
            long timestamp = Math.max(0L, mPlayerWorker.reader().getCurrentTimestamp() - SEEK_AMOUNT_US);
            mPlayerWorker.reader().seekTo(timestamp, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        });

        // Add seek forwards button
        ImageButton buttonSeekForwards = findViewById(R.id.button_seek_forwards);
        buttonSeekForwards.setOnClickListener(v -> {
            long timestamp = mPlayerWorker.reader().getCurrentTimestamp() +  SEEK_AMOUNT_US;
            if (timestamp < mPlayerWorker.mpi().getDuration()) {
                mPlayerWorker.reader().seekTo(timestamp, MediaExtractor.SEEK_TO_NEXT_SYNC);
            }
        });

        videoSurfaceView = findViewById(R.id.mainView);
        surfaceHolder = videoSurfaceView.getHolder();
        surfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("SurfaceActivity", "Surface changed");
        if (mPlayerWorker == null) {
            // Initialize new player thread
            mPlayerWorker = new PlayerThread(filename, holder.getSurface());
            try {
                mPlayerWorker.prepare();
            } catch (IOException | XMPException e) {
                e.printStackTrace();
            }
            Log.d("ReaderActivity", "MotionPhotoReader initialized");

            mPlayerWorker.start();
        }

        for (int i = 0; i < FILENAMES.length; i++) {
            String otherFilename = "/sdcard/MVIMG_" + FILENAMES[i] + ".jpg";
            MotionPhotoReader smallReader = null;
            MotionPhotoInfo smallMpi = null;
            Bitmap bmp = null;
            try {
                smallReader = MotionPhotoReader.open(otherFilename, null);
                smallMpi = smallReader.getMotionPhotoInfo();
                bmp = smallReader.getMotionPhotoImageBitmap();
            } catch (IOException | XMPException e) {
                e.printStackTrace();
            }
            ImageView imageView;
            switch (i) {
                case 1:
                    imageView = findViewById(R.id.image_view_b);
                    break;
                case 2:
                    imageView = findViewById(R.id.image_view_c);
                    break;
                default:
                    imageView = findViewById(R.id.image_view_a);
            }
            // Apply a rotation to the image view if necessary
            imageView.setRotation(smallMpi.getRotation());
            imageView.setImageBitmap(bmp);
            imageView.setOnClickListener(new MyOnImageClickListener(otherFilename, mPlayerWorker, surfaceHolder.getSurface()));
            smallReader.close();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPlayerWorker != null) {
            mPlayerWorker.interrupt();
        }
        holder.getSurface().release();
    }

    private class PlayerThread extends Thread {
        private MotionPhotoReader reader;
        private MotionPhotoInfo mpi;
        private String filename;
        private Surface surface;
        private boolean paused = true;

        public PlayerThread(String filename, Surface surface) {
            this.filename = filename;
            this.surface = surface;
        }

        @RequiresApi(api = 23)
        public void prepare() throws IOException, XMPException {
            this.reader = MotionPhotoReader.open(filename, surface);
            this.mpi = reader.getMotionPhotoInfo();

            // Set video display dimensions
            android.view.ViewGroup.LayoutParams lp = videoSurfaceView.getLayoutParams();
            if (mpi.getRotation() > 0) {
                lp.width = mpi.getHeight();
                lp.height = mpi.getWidth();
            }
            else {
                lp.width = mpi.getWidth();
                lp.height = mpi.getHeight();
            }
            videoSurfaceView.setLayoutParams(lp);
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void run() {
            try {
                Log.d("CurrentTimestamp", reader.getCurrentTimestamp() + "L");
                showFirstFrame();
                Log.d("CurrentTimestamp", reader.getCurrentTimestamp() + "L");
                while (surface.isValid()) {
                    // render remaining video if appropriate
                    if (!isPaused()) {
                        boolean hasNextFrame = reader.hasNextFrame().get(1000L, TimeUnit.MILLISECONDS);
                        if (hasNextFrame) {
                            reader.nextFrame();
                            Log.d("CurrentTimestamp", reader.getCurrentTimestamp() + "L");
                        }
                        else {
                            if (LOOP) {
                                Log.d("ReaderActivity", "Looped!");
                                reader.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void interrupt() {
            reader.close();
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        private void showFirstFrame() throws InterruptedException, ExecutionException, TimeoutException {
            // render first frame to screen
            reader.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            boolean hasNextFrame = reader.hasNextFrame().get(1000L, TimeUnit.MILLISECONDS);
            if (hasNextFrame) {
                reader.nextFrame();
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        public void changeReader(String filename) throws InterruptedException, ExecutionException, TimeoutException, IOException, XMPException {
            this.filename = filename;
            prepare();
            showFirstFrame();
        }

        public MotionPhotoReader reader() {
            return reader;
        }

        public MotionPhotoInfo mpi() {
            return mpi;
        }

        public boolean isPaused() {
            return paused;
        }

        public void pause() {
            paused = true;
        }

        public void play() {
            paused = false;
        }
    }

    private class MyOnImageClickListener implements View.OnClickListener {
        private final Surface surface;

        private String filename;
        private PlayerThread mPlayerWorker;

        public MyOnImageClickListener(String filename, PlayerThread mPlayerWorker, Surface surface) {
            this.filename = filename;
            this.mPlayerWorker = mPlayerWorker;
            this.surface = surface;
        }

        @RequiresApi(api = 28)
        @Override
        public void onClick(View v) {
            mPlayerWorker.pause();
            mPlayerWorker.reader().close();
            buttonPlayPause.setChecked(false);
            try {
                mPlayerWorker.changeReader(filename);
            } catch (InterruptedException | ExecutionException | TimeoutException | XMPException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}