package com.example.motionphotoreader;

import android.view.Surface;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * The MotionPhotoReader API allows developers to read through the video portion of Motion Photos in
 * a frame-by-frame manner.
 */

public class MotionPhotoReader {

    private final String filename;
    private final Surface surface;

    private MotionPhotoReader(String filename, Surface surface) {
        this.filename = filename;
        this.surface = surface;
    }

    /**
     * Opens and prepares a new MotionPhotoReader for a particular file.
     */
    public static MotionPhotoReader open(String filename, Surface surface) {
        return null;
    }

    /**
     * Parses the XMP in the file to find the microvideo offset, and sets up the MediaCodec
     * extractors and decoders.
     */
    private void prepare() {

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

        private static final byte[] OPEN_ARR = "<x:xmpmeta".getBytes(); /** Start of XMP metadata tag */
        private static final byte[] CLOSE_ARR = "</x:xmpmeta>".getBytes(); /** End of XMP metadata tag */

        /**
         * Copies the input stream from a file to an output stream.
         */
        private static void copy(String filename, InputStream in, OutputStream out) {

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
            return -1;
        }

        /**
         * Returns the video offset of the microvideo in the Motion Photo file, in bytes from the end
         * of the file.
         */
        public static int getVideoOffset(String filename) {
            return -1;
        }

    }
    
    private class MotionPhotoInfo {
        
    }
    
    private class MotionPhotoImage {
        
    }
}
