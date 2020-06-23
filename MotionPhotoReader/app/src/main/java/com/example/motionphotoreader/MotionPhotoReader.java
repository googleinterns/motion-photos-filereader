package com.example.motionphotoreader;

import android.view.Surface;

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

    public static MotionPhotoReader open(String filename, Surface surface) {
        return null;
    }

    private void prepare() {

    }

    public void close() {

    }

    public int hasNextFrame() {
        return -1;
    }

    public void nextFrame() {

    }

    private static class XmpParser {

    }
    
    private class MotionPhotoInfo {
        
    }
    
    private class MotionPhotoImage {
        
    }
}
