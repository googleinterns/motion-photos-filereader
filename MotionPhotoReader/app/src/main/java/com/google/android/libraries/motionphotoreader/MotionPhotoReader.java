package com.google.android.libraries.motionphotoreader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.adobe.internal.xmp.XMPException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.os.Build.VERSION_CODES.M;

/**
 * The MotionPhotoReader API allows developers to read through the video portion of Motion Photos in
 * a frame-by-frame manner.
 */

public class MotionPhotoReader {

    private final String filename;
    private final Surface surface;
    private MotionPhotoInfo mpi;

    private MediaExtractor lowResExtractor;
    private MediaCodec lowResDecoder;
    private MediaFormat videoFormat;
    private FileInputStream fileInputStream;

    // message keys
    static final int MSG_NEXT_FRAME = 0x0001;
    static final int MSG_SEEK_TO_FRAME = 0x0010;

    /**
     * Two handlers manage the calls to play through the video. The media worker thread posts
     * available buffers to the buffer queue. The buffer worker receives messages to process frames
     * and uses the available buffers posted by the media worker thread.
     */
    private HandlerThread mMediaWorker;
    private Handler mediaHandler;
    private HandlerThread mBufferWorker;
    private Handler bufferHandler;

    /** Available buffer queues **/
    private final BlockingQueue<Integer> availableInputBuffers;
    private final BlockingQueue<Bundle> availableOutputBuffers;


    /**
     * Standard MotionPhotoReader constructor.
     */
    private MotionPhotoReader(String filename, Surface surface,
                              BlockingQueue<Integer> availableInputBuffers, BlockingQueue<Bundle> availableOutputBuffers) {
        this.filename = filename;
        this.surface = surface;

        this.lowResExtractor = new MediaExtractor();
        this.availableInputBuffers = availableInputBuffers;
        this.availableOutputBuffers = availableOutputBuffers;
    }

    /**
     * Opens and prepares a new MotionPhotoReader for a particular file.
     */
    @RequiresApi(api = M)
    public static MotionPhotoReader open(String filename, Surface surface) throws IOException, XMPException {
        MotionPhotoReader reader = new MotionPhotoReader(filename, surface,
                new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>());
        reader.startMediaThread();
        reader.startBufferThread();
        return reader;
    }

    /**
     * Opens and prepares a new MotionPhotoReader for testing.
     */
    @RequiresApi(api = M)
    @VisibleForTesting
    static MotionPhotoReader open(String filename, Surface surface,
                                  BlockingQueue<Integer> availableInputBuffers, BlockingQueue<Bundle> availableOutputBuffers)
            throws IOException, XMPException {
        MotionPhotoReader reader = new MotionPhotoReader(filename, surface, availableInputBuffers, availableOutputBuffers);
        reader.startMediaThread();
        reader.startBufferThread();
        return reader;
    }

    /**
     * Sets up and starts a new handler thread for MediaCodec objects (decoder and extractor).
     */
    @RequiresApi(api = 23)
    private void startMediaThread() throws IOException, XMPException {
        mMediaWorker = new HandlerThread("mediaHandler");
        mMediaWorker.start();
        mediaHandler = new Handler(mMediaWorker.getLooper());

        mpi = getMotionPhotoInfo();
        int videoOffset = mpi.getVideoOffset();

        // Set up input stream from Motion Photo file for media extractor
        final File f = new File(filename);
        fileInputStream = new FileInputStream(f);
        FileDescriptor fd = fileInputStream.getFD();

        lowResExtractor.setDataSource(fd, f.length() - videoOffset, videoOffset);

        // Find the video track and create an appropriate media decoder
        for (int i = 0; i < lowResExtractor.getTrackCount(); i++) {
            MediaFormat format = lowResExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            assert mime != null;
            if (mime.startsWith("video/")) {
                lowResExtractor.selectTrack(i);
                videoFormat = format;
                lowResDecoder = MediaCodec.createDecoderByType(mime);
                break;
            }
        }

        // Make sure the Android version is capable of supporting MediaCodec callbacks
        if (Build.VERSION.SDK_INT < M) {
            Log.e("MotionPhotoReader", "Insufficient Android build version");
            return;
        }

        lowResDecoder.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                boolean result = availableInputBuffers.offer(index);
                Log.d("DecodeActivity", "Input buffers: " + availableInputBuffers.toString());
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                Bundle bufferData = new Bundle();
                bufferData.putInt("BUFFER_INDEX", index);
                bufferData.putLong("TIMESTAMP_US", info.presentationTimeUs);
                boolean result = availableOutputBuffers.offer(bufferData);
                Log.d("DecodeActivity", "Output buffers: " + availableOutputBuffers.toString());
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

            }
        }, mediaHandler);

        lowResDecoder.configure(videoFormat, surface, null, 0);
        lowResDecoder.start();
    }

    /**
     * Sets up and starts a new handler thread for managing frame advancing calls and available buffers.
     */
    private void startBufferThread() {
        mBufferWorker = new HandlerThread("bufferHandler");
        mBufferWorker.start();
        bufferHandler = new BufferHandler(mBufferWorker.getLooper(), lowResExtractor, lowResDecoder,
                availableInputBuffers, availableOutputBuffers);
    }

    /**
     * Shut down all resources allocated to the MotionPhotoReader instance.
     */
    public void close() {
        Log.d("ReaderActivity", "Closed decoder and extractor");
        try {
            fileInputStream.close();
            Log.d("ReaderActivity", "Close file input stream");

        } catch (IOException e) {
            e.printStackTrace();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bufferHandler.getLooper().quitSafely();
            mediaHandler.getLooper().quitSafely();
            Log.d("ReaderActivity", "Safely quit looper");
        }
        else {
            bufferHandler.getLooper().quit();
            mediaHandler.getLooper().quit();
            Log.d("ReaderActivity", "Quit looper");
        }
        lowResDecoder.release();
        lowResExtractor.release();
        mBufferWorker.interrupt();
        mMediaWorker.interrupt();
//        if (surface != null) {
//            surface.release();
//        }
    }

    /**
     * Checks whether the Motion Photo video has a succeeding frame.
     * @return 1 if there is no frame, 0 if the next frame exists, and -1 if no buffers are available.
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    public ListenableFuture<Boolean> hasNextFrame() {
        Log.d("HasNextFrame", "Running");

        SettableFuture<Boolean> result = SettableFuture.create();

        // Send hasNextFrame task to handler
        bufferHandler.post(() -> {

            // Read the next packet and check if it shows a full frame
            long sampleSize = lowResExtractor.getSampleSize();
            if (sampleSize < 0) {
                result.set(false);
            }
            else {
                result.set(true);
            }
        });
        return result;
    }

    /**
     * Advances the decoder and extractor by one frame.
     */
    public void nextFrame() {
        Message message = Message.obtain(bufferHandler);

        Bundle messageData = new Bundle();
        messageData.putInt("MESSAGE_KEY", MSG_NEXT_FRAME);
        message.setData(messageData);

        message.sendToTarget();
    }

    /**
     * Sets the decoder and extractor to the frame specified by the given timestamp.
     * @param timeUs The desired timestamp of the video.
     * @param mode The sync mode of the extractor.
     *
     * TODO: resolve possible jank.
     */
    public void seekTo(long timeUs, int mode) {
        Message message = Message.obtain(bufferHandler);

        Bundle messageData = new Bundle();
        messageData.putInt("MESSAGE_KEY", MSG_SEEK_TO_FRAME);
        messageData.putLong("TIME_US", timeUs);
        messageData.putInt("MODE", mode);
        message.setData(messageData);

        message.sendToTarget();
    }

    /**
     * Gets the current video timestamp at which the extractor is set (in microseconds).
     */
    public long getCurrentTimestamp() {
        return lowResExtractor.getSampleTime();
    }

    /**
     * Retrieves information about the motion photo and returns a MotionPhotoInfo object.
     */
    @RequiresApi(api = M)
    public MotionPhotoInfo getMotionPhotoInfo() throws IOException, XMPException {
        return MotionPhotoInfo.newInstance(filename);
    }


    public Bitmap getMotionPhotoImageBitmap() {
        Bitmap bmp = BitmapFactory.decodeFile(filename);
        return bmp;
    }
}
