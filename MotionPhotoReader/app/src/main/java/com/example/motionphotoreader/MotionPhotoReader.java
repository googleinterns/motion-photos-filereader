package com.example.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Trace;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.adobe.internal.xmp.XMPException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * The MotionPhotoReader API allows developers to read through the video portion of Motion Photos in
 * a frame-by-frame manner.
 */

public class MotionPhotoReader {

    private final String filename;
    private final Surface surface;

    private MediaExtractor lowResExtractor;
    private MediaCodec lowResDecoder;
    private MediaFormat mediaFormat;
    private FileInputStream fileInputStream;

    // message keys
    private static final int MSG_NEXT_FRAME = 0x0001;
    private static final int MSG_SEEK_TO_FRAME = 0x0010;
    private static final long TIMEOUT_US = 1000L;

    private static final long US_TO_NS = 1000L;

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
    private final BlockingQueue<Integer> availableInputBuffers = new LinkedBlockingQueue<>();
    private final BlockingQueue<Bundle> availableOutputBuffers = new LinkedBlockingQueue<>();


    /**
     * Standard MotionPhotoReader constructor.
     */
    private MotionPhotoReader(String filename, Surface surface) {
        this.filename = filename;
        this.surface = surface;
        this.lowResExtractor = new MediaExtractor();
    }

    /**
     * Constructor for testing.
     */
    @RequiresApi(api = LOLLIPOP)
    @VisibleForTesting
    MotionPhotoReader(String filename, Surface surface,
                             MediaExtractor lowResExtractor, MediaCodec lowResDecoder) throws IOException, XMPException {
        this.filename = filename;
        this.surface = surface;
        this.lowResExtractor = lowResExtractor;
        this.lowResDecoder = lowResDecoder;

        startBufferThread();
        startMediaThread(this.lowResDecoder);
    }

    /**
     * Opens and prepares a new MotionPhotoReader for a particular file.
     */
    @RequiresApi(api = LOLLIPOP)
    public static MotionPhotoReader open(String filename, Surface surface) throws IOException, XMPException {
        MotionPhotoReader reader = new MotionPhotoReader(filename, surface);
        reader.startBufferThread();
        reader.startMediaThread();

        return reader;
    }

    /**
     * Sets up and starts a new handler thread for MediaCodec objects (decoder and extractor).
     */
    @RequiresApi(api = LOLLIPOP)
    private void startMediaThread() throws IOException, XMPException {
        mMediaWorker = new HandlerThread("mediaHandler");
        mMediaWorker.start();
        mediaHandler = new Handler(mMediaWorker.getLooper());

        MotionPhotoInfo mpi = getMotionPhotoInfo();
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
                mediaFormat = format;
                lowResDecoder = MediaCodec.createDecoderByType(mime);
                break;
            }
        }

        // Make sure the Android version is capable of supporting MediaCodec callbacks
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
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

        lowResDecoder.configure(mediaFormat, surface, null, 0);
        lowResDecoder.start();
    }

    @RequiresApi(api = LOLLIPOP)
    private void startMediaThread(MediaCodec lowResDecoder) throws IOException, XMPException {
        mMediaWorker = new HandlerThread("mediaHandler");
        mMediaWorker.start();
        mediaHandler = new Handler(mMediaWorker.getLooper());

        MotionPhotoInfo mpi = getMotionPhotoInfo();
        int videoOffset = mpi.getVideoOffset();

        // Set up input stream from Motion Photo file for media extractor
        final File f = new File(filename);
        fileInputStream = new FileInputStream(f);
        FileDescriptor fd = fileInputStream.getFD();

        lowResExtractor.setDataSource(fd, f.length() - videoOffset, videoOffset);
        this.lowResDecoder = lowResDecoder;

        // Make sure the Android version is capable of supporting MediaCodec callbacks
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
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

        lowResDecoder.configure(mediaFormat, surface, null, 0);
        lowResDecoder.start();
    }

    /**
     * Sets up and starts a new handler thread for managing frame advancing calls and available buffers.
     * TODO: Refactor for better modularity.
     */
    private void startBufferThread() {
        mBufferWorker = new HandlerThread("bufferHandler");
        mBufferWorker.start();
        bufferHandler = new Handler(mBufferWorker.getLooper()) {

            @RequiresApi(api = LOLLIPOP)
            public int getAvailableInputBufferIndex() {
                int bufferIndex = -1;
                try {
                    bufferIndex = availableInputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return bufferIndex;
            }

            public void readFromExtractor(ByteBuffer inputBuffer, int bufferIndex) {
                int sampleSize = lowResExtractor.readSampleData(inputBuffer, 0);
                if (sampleSize < 0) {
                    Log.d("NextFrame", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                    lowResDecoder.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
                else {
                    Log.d("NextFrame", "Queue InputBuffer for time " + lowResExtractor.getSampleTime());
                    lowResDecoder.queueInputBuffer(bufferIndex, 0, sampleSize, lowResExtractor.getSampleTime(), 0);
                    lowResExtractor.advance();
                }
            }

            public Bundle getAvailableOutputBufferData() {
                Bundle bufferData = null;
                try {
                    bufferData = availableOutputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return bufferData;
            }

            @RequiresApi(api = LOLLIPOP)
            @Override
            public void handleMessage(@NonNull Message inputMessage) {
                Bundle messageData = inputMessage.getData();
                int key = messageData.getInt("MESSAGE_KEY");
                Bundle bufferData;
                int bufferIndex;
                long timestamp;
                switch (key) {
                    case MSG_NEXT_FRAME:
                        Trace.beginSection("msg-next-frame");
                        // Get the next available input buffer and read frame data
                        bufferIndex = getAvailableInputBufferIndex();
                        ByteBuffer inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                        readFromExtractor(inputBuffer, bufferIndex);

                        // Get the next available output buffer and release frame data
                        bufferData = getAvailableOutputBufferData();
                        timestamp = bufferData.getLong("TIMESTAMP_US");
                        bufferIndex = bufferData.getInt("BUFFER_INDEX");
                        lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp * US_TO_NS);
                        Log.d("NextFrame", "Releasing to output buffer " + bufferIndex);

                        Trace.endSection();
                        break;

                    case MSG_SEEK_TO_FRAME:
                        Trace.beginSection("msg-seek-to-frame");
                        // Get the next available input buffer and read frame data
                        lowResExtractor.seekTo(messageData.getLong("TIME_US"), messageData.getInt("MODE"));

                        bufferIndex = getAvailableInputBufferIndex();
                        inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                        readFromExtractor(inputBuffer, bufferIndex);

                        // Get the next available output buffer and release frame data
                        bufferData = getAvailableOutputBufferData();
                        Log.d("SeekToFrame", bufferData.toString());
                        timestamp = bufferData.getLong("TIMESTAMP_US");
                        bufferIndex = bufferData.getInt("BUFFER_INDEX");
                        lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp * US_TO_NS);
                        Log.d("SeekToFrame", "Releasing to output buffer " + bufferIndex);

                        Trace.endSection();
                        break;

                    default:
                        Log.e("HandlerActivity", "Unexpected message!");
                }
            }
        };
    }

    /**
     * Shut down all resources allocated to the MotionPhotoReader instance.
     */
    public void close() {
        lowResDecoder.release();
        lowResExtractor.release();
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
        mBufferWorker.interrupt();
        mMediaWorker.interrupt();
        surface.release();
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
    public MotionPhotoInfo getMotionPhotoInfo() throws IOException, XMPException {
        MotionPhotoInfo mpi = MotionPhotoInfo.newInstance(filename);
        return mpi;
    }

    public int getNumAvailableInputBuffers() {
        return availableInputBuffers.size();
    }

    public int getNumAvailableOutputBuffers() {
        return availableOutputBuffers.size();
    }
}
