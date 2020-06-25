package com.example.motionphotoreader;

import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Holds data about the Motion Photo file.
 */
public class MotionPhotoInfo {

    private int width;
    private int height;
    private int videoOffset;

    /**
     * Returns the XMP metadata associated with a given file.
     */
    public MotionPhotoInfo(String filename) throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(filename);
        videoOffset = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "MicroVideoOffset");
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getVideoOffset() {
        return videoOffset;
    }
}
