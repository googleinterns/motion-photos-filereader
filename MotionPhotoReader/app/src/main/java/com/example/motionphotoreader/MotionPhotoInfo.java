package com.example.motionphotoreader;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Holds data about the Motion Photo file.
 */
public class MotionPhotoInfo {

    private final int width;
    private final int height;
    private final long duration;

    private final int videoOffset;
    private final long presentationTimestampUs;

    private MediaExtractor extractor;

    /**
     * Creates a MotionPhotoInfo object associated with a given file.
     */
    private MotionPhotoInfo(MediaFormat mediaFormat, int videoOffset, long presentationTimestampUs) {
        width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);

        this.videoOffset = videoOffset;
        this.presentationTimestampUs = presentationTimestampUs;
    }

    /**
     * Returns a new instance of MotionPhotoInfo for a specified file.
     */
    public static MotionPhotoInfo newInstance(String filename) throws IOException, XMPException {
        return MotionPhotoInfo.newInstance(filename, new MediaExtractor());
    }

    /**
     * Returns a new instance of MotionPhotoInfo with a specified file and MediaExtractor. Used for testing.
     */
    public static MotionPhotoInfo newInstance(String filename, MediaExtractor extractor) throws IOException, XMPException {
        XMPMeta meta = getFileXMP(filename);
        int videoOffset = meta.getPropertyInteger("http://ns.google.com/photos/1.0/camera/", "MicroVideoOffset");
        long presentationTimestampUs = meta.getPropertyLong("http://ns.google.com/photos/1.0/camera/", "MicroVideoPresentationTimestampUs");

        MediaFormat mediaFormat = getFileMediaFormat(filename, extractor, videoOffset);
        MotionPhotoInfo mpi = new MotionPhotoInfo(mediaFormat, videoOffset, presentationTimestampUs);
        return mpi;
    }

    /**
     * Extract the JPEG XMP metadata from the Motion Photo.
     */
    private static XMPMeta getFileXMP(String filename) throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(filename);
        return meta;
    }

    /**
     * Get the MediaFormat associated with the video track of the Motion Photo MPEG4.
     */
    private static MediaFormat getFileMediaFormat(String filename, MediaExtractor extractor, int videoOffset) throws IOException {
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
                fileInputStream.close();
                return format;
            }
        }
        return null;
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
