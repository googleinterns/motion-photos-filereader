package com.example.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
    private final BlockingQueue<Integer> availableOutputBuffers = new LinkedBlockingQueue<>();


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
                Log.d("DecodeActivity", "Sent available input buffer " + index + ": " + result);
                Log.d("DecodeActivity", "Input buffers: " + availableInputBuffers.toString());
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                boolean result = availableOutputBuffers.offer(index);
                Log.d("DecodeActivity", "Sent available output buffer " + index + ": " + result);
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
     */
    private void startBufferThread() {
    }

    /**
     * Shut down all resources allocated to the MotionPhotoReader instance.
     */
    public void close() {

    }

    /**
     * Checks whether the Motion Photo video has a succeeding frame.
     * @return 1 if there is no frame, 0 if the next frame exists, and -1 if no buffers are available.
     */
    public int hasNextFrame() {
        return -1;
    }

    /**
     * Advances the decoder and extractor by one frame.
     */
    public void nextFrame() {

    }

    /**
     * Sets the decoder and extractor to the frame specified by the given timestamp.
     * @param timeUs The desired timestamp of the video.
     * @param mode The sync mode of the extractor.
     */
    public void seekTo(long timeUs, int mode) {

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
