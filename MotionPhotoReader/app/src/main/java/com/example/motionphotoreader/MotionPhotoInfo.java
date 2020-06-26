package com.example.motionphotoreader;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

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
    private long duration;

    private int videoOffset;
    private long presentationTimestampUs;

    private final MediaExtractor extractor;

    /**
     * Returns the MotionPhotoInfo associated with a given file.
     */
    @VisibleForTesting
    public MotionPhotoInfo(String filename, MediaExtractor extractor) throws IOException, XMPException {
        this.extractor = extractor;

        XMPMeta meta = XmpParser.getXmpMetadata(filename);
//        int length = meta.getPropertyInteger("http://ns.google.com/photos/1.0/container/item/", "Length");
//        int padding = meta.getPropertyInteger("http://ns.google.com/photos/1.0/container/item/", "Padding");
//        videoOffset = length + padding;
        videoOffset = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "MicroVideoOffset");
        presentationTimestampUs = meta.getPropertyLong("http://ns.google.com/photos/1.0/camera/", "MicroVideoPresentationTimestampUs");

        File f = new File(filename);
        FileInputStream fileInputStream = new FileInputStream(f);
        FileDescriptor fd = fileInputStream.getFD();
        extractor.setDataSource(fd, f.length() - videoOffset, videoOffset);

        // Find the video track and create an appropriate media decoder
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            assert mime != null;
            if (mime.startsWith("video/")) {
                width = format.getInteger(MediaFormat.KEY_WIDTH);
                height = format.getInteger(MediaFormat.KEY_HEIGHT);
                duration = format.getLong(MediaFormat.KEY_DURATION);
                break;
            }
        }

        fileInputStream.close();
    }

    /**
     * Returns a new instance of MotionPhotoInfo for a specified file.
     */
    public static MotionPhotoInfo newInstance(String filename) throws IOException, XMPException {
        return new MotionPhotoInfo(filename, new MediaExtractor());
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

    public long getDuration() {
        return duration;
    }

    public long getPresentationTimestampUs() {
        return presentationTimestampUs;
    }
}
