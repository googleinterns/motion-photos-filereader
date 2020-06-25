package com.example.motionphotoreader;

import android.util.Log;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Holds data about the Motion Photo file.
 */
public class MotionPhotoInfo {

    private String filename;
    private int width;
    private int height;
    private int videoOffset;
    private int xResolution;
    private int yResolution;

    /**
     * Returns the XMP metadata associated with a given file.
     */
    public MotionPhotoInfo(String filename) throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(filename);
        this.filename = filename;
        width = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "ExifImageWidth");
        height = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "ExifImageHeight");
        videoOffset = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "MicroVideoOffset");
        xResolution = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "XResolution");
        yResolution = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "YResolution");
    }

    public int getWidth() {
        return -1;
    }

    public int getHeight() {
        return -1;
    }

    public int getVideoOffset() {
        return -1;
    }

    public int getXResolution() {
        return -1;
    }

    public int getyResolution() {
        return -1;
    }
}
