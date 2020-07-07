package com.google.android.libraries.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Contains information relevant to extracting frames in a Motion Photo file.
 */
public class MotionPhotoInfo {

    private final int width;
    private final int height;
    private final long duration;
    private final int rotation;

    private final int videoOffset;
    private final long presentationTimestampUs;

    /**
     * Creates a MotionPhotoInfo object associated with a given file.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private MotionPhotoInfo(MediaFormat mediaFormat, int videoOffset, long presentationTimestampUs) {
        width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        rotation = mediaFormat.containsKey(MediaFormat.KEY_ROTATION)
                ? mediaFormat.getInteger(MediaFormat.KEY_ROTATION)
                : 0;

        this.videoOffset = videoOffset;
        this.presentationTimestampUs = presentationTimestampUs;
    }

    /**
     * Returns a new instance of MotionPhotoInfo for a specified file.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static MotionPhotoInfo newInstance(String filename) throws IOException, XMPException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            return MotionPhotoInfo.newInstance(filename, new MediaExtractor());
        }
        finally {
            extractor.release();
        }
    }

    /**
     * Returns a new instance of MotionPhotoInfo with a specified file and MediaExtractor. Used for testing.
     */
    @VisibleForTesting
    @RequiresApi(api = Build.VERSION_CODES.M)
    static MotionPhotoInfo newInstance(String filename, MediaExtractor extractor)
            throws IOException, XMPException {
        XMPMeta meta = getFileXmp(filename);
        int videoOffset = meta.getPropertyInteger(
                "http://ns.google.com/photos/1.0/camera/",
                "MicroVideoOffset"
        );
        long presentationTimestampUs = meta.getPropertyLong(
                "http://ns.google.com/photos/1.0/camera/",
                "MicroVideoPresentationTimestampUs"
        );
        MediaFormat mediaFormat = getFileMediaFormat(filename, extractor, videoOffset);
        return new MotionPhotoInfo(mediaFormat, videoOffset, presentationTimestampUs);
    }

    /**
     * Extract the JPEG XMP metadata from the Motion Photo.
     */
    private static XMPMeta getFileXmp(String filename) throws IOException, XMPException {
        return XmpParser.getXmpMetadata(filename);
    }

    /**
     * Get the MediaFormat associated with the video track of the Motion Photo MPEG4.
     */
    @Nullable
    private static MediaFormat getFileMediaFormat(String filename, MediaExtractor extractor, int videoOffset) throws IOException {
        File f = new File(filename);
        try (FileInputStream fileInputStream = new FileInputStream(f)) {
            FileDescriptor fd = fileInputStream.getFD();
            extractor.setDataSource(fd, f.length() - videoOffset, videoOffset);
            // Find the video track and create an appropriate media decoder
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                assert mime != null;
                if (mime.startsWith("video/")) {
                    return format;
                }
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

    public int getRotation() {
        return rotation;
    }

    public long getPresentationTimestampUs() {
        return presentationTimestampUs;
    }
}
