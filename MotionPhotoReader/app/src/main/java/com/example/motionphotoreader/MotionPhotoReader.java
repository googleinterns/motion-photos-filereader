package com.example.motionphotoreader;

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

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
    private static final long TIMEOUT_MS = 1000L;

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


    private MotionPhotoReader(String filename, Surface surface) {
        this.filename = filename;
        this.surface = surface;
    }

    /**
     * Opens and prepares a new MotionPhotoReader for a particular file.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static MotionPhotoReader open(String filename, Surface surface) throws IOException, XMPException {
        MotionPhotoReader reader = new MotionPhotoReader(filename, surface);
        reader.prepare();
        Log.d("ReaderActivity", "Prepared motion photo reader");
        return reader;
    }

    /**
     * Parses the XMP in the file to find the microvideo offset, and sets up the MediaCodec
     * extractors and decoders.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void prepare() throws IOException, XMPException {
        startBufferThread();
        startMediaThread();
    }

    /**
     * Sets up and starts a new handler thread for MediaCodec objects (decoder and extractor).
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startMediaThread() throws IOException, XMPException {
        mMediaWorker = new HandlerThread("mediaHandler");
        mMediaWorker.start();
        mediaHandler = new Handler(mMediaWorker.getLooper());

        int videoOffset = XmpParser.getVideoOffset(filename);

        // Set up input stream from Motion Photo file for media extractor
        final File f = new File(filename);
        fileInputStream = new FileInputStream(f);
        FileDescriptor fd = fileInputStream.getFD();

        lowResExtractor = new MediaExtractor();
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
                bufferData.putLong("TIMESTAMP_NS", info.presentationTimeUs);
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

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void handleMessage(Message inputMessage) {
                Bundle messageData = inputMessage.getData();
                int key = messageData.getInt("MESSAGE_KEY");
                Bundle bufferData = null;
                Integer bufferIndex = -1;
                long timestamp;
                switch (key) {
                    case MSG_NEXT_FRAME:
                        // Get index of the next available input buffer
                        try {
                            bufferIndex = availableInputBuffers.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        ByteBuffer inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);

                        // Read next packet from media extractor and update state according to settings
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

                        // Get the next available output buffer and release frame data
                        try {
                            bufferData = availableOutputBuffers.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        bufferIndex = bufferData.getInt("BUFFER_INDEX");
                        timestamp = bufferData.getLong("TIMESTAMP");
                        lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp);
                        Log.d("NextFrame", "Releasing to output buffer " + bufferIndex);
                        break;

                    case MSG_SEEK_TO_FRAME:
                        // Get index of the next available input buffer
                        try {
                            bufferIndex = availableInputBuffers.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                        if (inputBuffer == null) {
                            Log.e("SeekToFrame", "Input buffer is null");
                            break;
                        }
                        Log.d("SeekToFrame", "Received input buffer " + bufferIndex);

                        // Set media extractor to specified timestamp
                        lowResExtractor.seekTo(messageData.getLong("TIME_US"), messageData.getInt("MODE"));
                        sampleSize = lowResExtractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            Log.d("SeekToFrame", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            lowResDecoder.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                        else {
                            Log.d("SeekToFrame", "Queue InputBuffer " + lowResExtractor.getSampleTime());
                            lowResDecoder.queueInputBuffer(bufferIndex, 0, sampleSize, 1000 * lowResExtractor.getSampleTime(), 0);
                            lowResExtractor.advance();
                        }

                        // Get the next available output buffer and release frame data
                        try {
                            bufferData = availableOutputBuffers.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        bufferIndex = bufferData.getInt("BUFFER_INDEX");
                        timestamp = bufferData.getLong("TIMESTAMP");
                        lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp);
                        Log.d("SeekToFrame", "Releasing to output buffer " + bufferIndex);
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
     * The XmpParser class is a child class intended to help extract the microvideo offset information
     * from the XMP metadata of the given Motion Photo. This class is for internal use only.
     */
    private static class XmpParser {

        private static final byte[] OPEN_ARR = "<x:xmpmeta".getBytes();  /* Start of XMP metadata tag */
        private static final byte[] CLOSE_ARR = "</x:xmpmeta>".getBytes();  /* End of XMP metadata tag */

        /**
         * Copies the input stream from a file to an output stream.
         */
        private static void copy(String filename, InputStream in, OutputStream out) throws IOException {
            int len;
            byte[] buf = new byte[1024];
            while((len = in.read(buf)) >= 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }

        /**
         * Returns the index of the first appearance of a given subsequence in a byte array.
         * @param arr The full byte array
         * @param seq The subsequence of bytes to find
         * @param start The index at which to start searching
         * @return The index of the first appearance of the beginning of the subsequence in the
         * byte array. If the sequence is not found, return -1.
         */
        private static int indexOf(byte[] arr, byte[] seq, int start) {
            int subIdx = 0;
            for (int x = start; x < arr.length; x++) {
                if (arr[x] == seq[subIdx]) {
                    if (subIdx == seq.length - 1) {
                        return x - subIdx;
                    }
                    subIdx++;
                }
                else {
                    subIdx = 0;
                }
            }
            return -1;
        }

        /**
         * Returns the video offset of the microvideo in the Motion Photo file, in bytes from the end
         * of the file.
         */
        public static int getVideoOffset(String filename) throws XMPException, IOException {
            FileInputStream in = new FileInputStream(filename);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            copy(filename, in, out);
            byte[] fileData = out.toByteArray();

            int openIdx = indexOf(fileData, OPEN_ARR, 0);
            if (openIdx >= 0) {
                int closeIdx = indexOf(fileData, CLOSE_ARR, openIdx + 1) + CLOSE_ARR.length;

                byte[] segArr = Arrays.copyOfRange(fileData, openIdx, closeIdx);
                XMPMeta meta = XMPMetaFactory.parseFromBuffer(segArr);

                int videoOffset = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "MicroVideoOffset");
                Log.d("XmlParserActivity", "Micro video offset: " + videoOffset);
                return videoOffset;
            }
            return 0;

        }

    }
    
    private class MotionPhotoInfo {
        
    }
    
    private class MotionPhotoImage {
        
    }
}
