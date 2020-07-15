package com.google.android.libraries.motionphotoreader;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Contains information relevant to extracting frames in a Motion Photo file.
 */
public class MotionPhotoInfo {

    private final static String TAG = "MotionPhotoInfo";

    private final static int MOTION_PHOTO_VERSION_V1 = 1;
    private final static int MOTION_PHOTO_VERSION_V2 = 2;

    private static final String CAMERA_XMP_NAMESPACE =
            "http://ns.google.com/photos/1.0/camera/";
    private static final String CONTAINER_XMP_NAMESPACE =
            "http://ns.google.com/photos/1.0/container/";

    private final int width;
    private final int height;
    private final long duration;
    private final int rotation;
    private final int videoOffset;

    /**
     * Creates a MotionPhotoInfo object associated with a given file.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private MotionPhotoInfo(MediaFormat mediaFormat, int videoOffset) {
        width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        rotation = mediaFormat.containsKey(MediaFormat.KEY_ROTATION)
                ? mediaFormat.getInteger(MediaFormat.KEY_ROTATION)
                : 0;
        this.videoOffset = videoOffset;
    }

    /**
     * Returns a new instance of MotionPhotoInfo for a specified file.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static MotionPhotoInfo newInstance(String filename) throws IOException, XMPException {
        return MotionPhotoInfo.newInstance(new File(filename));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static MotionPhotoInfo newInstance(File file) throws IOException, XMPException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            return MotionPhotoInfo.newInstance(file, new MediaExtractor());
        } finally {
            extractor.release();
        }
    }

    /**
     * Returns a new instance of MotionPhotoInfo with a specified file and MediaExtractor.
     * Used for testing.
     */
    @VisibleForTesting
    @RequiresApi(api = Build.VERSION_CODES.M)
    static MotionPhotoInfo newInstance(String filename, MediaExtractor extractor)
            throws IOException, XMPException {
        return MotionPhotoInfo.newInstance(new File(filename), extractor);
    }

    @VisibleForTesting
    @RequiresApi(api = Build.VERSION_CODES.M)
    static MotionPhotoInfo newInstance(File file, MediaExtractor extractor)
            throws IOException, XMPException {
        XMPMeta meta = getFileXmp(file);
        int videoOffset = getVideoOffset(meta);
        MediaFormat mediaFormat = getFileMediaFormat(file, extractor, videoOffset);
        return new MotionPhotoInfo(mediaFormat, videoOffset);
    }

    /**
     * Finds the video offset encoded in the motion photo XMP metadata.
     * @param meta The XMP metadata for the motion photo file.
     * @return the number of bytes from the end of the motion photo file to the beginning of the
     * video track.
     * @throws XMPException when parsing invalid XMP metadata.
     */
    private static int getVideoOffset(XMPMeta meta) throws XMPException {
        int version = getMotionPhotoVersion(meta);
        int videoOffset = 0;
        switch (version) {
            case MOTION_PHOTO_VERSION_V1:
                videoOffset = meta.getPropertyInteger(
                        CAMERA_XMP_NAMESPACE,
                        "MicroVideoOffset"
                );
                break;
            case MOTION_PHOTO_VERSION_V2:
                // Iterate through the nodes of the XMP metadata to find the desired item length
                // and padding properties. The items we are looking for belong in an array with name
                // "Directory" that is indexed starting at 1. We ignore the first item in the array
                // because it is the primary item, and the video track is always stored immediately
                // after the primary item (and any padding).
                XMPIterator itr = meta.iterator();
                int arrayItemIdx = 2;
                while (itr.hasNext()) {
                    XMPPropertyInfo property = (XMPPropertyInfo) itr.next();
                    String propertyPath = property.getPath();
                    String lengthProperty = "Container:Directory["
                            + arrayItemIdx
                            + "]/Container:Item/Item:Length";
                    String paddingProperty = "Container:Directory["
                            + arrayItemIdx
                            + "]/Container:Item/Item:Padding";
                    if (propertyPath != null) {
                        if (propertyPath.equalsIgnoreCase(lengthProperty)) {
                            videoOffset += Integer.parseInt(property.getValue());
                        } else if (propertyPath.equalsIgnoreCase(paddingProperty)) {
                            videoOffset += Integer.parseInt(property.getValue());
                        }
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid file format");
        }
        return videoOffset;
    }

    /**
     * Get the version of the motion photo file encoded by this XMP metadata. There are only two
     * possible versions, v1 or v2.
     * @param meta The XMPMeta corresponding to the motion photo file in question.
     * @return 1 if the version is v1, 2 if the version is v2, and 0 otherwise.
     * @throws XMPException when parsing invalid XMP metadata.
     */
    private static int getMotionPhotoVersion(XMPMeta meta) throws XMPException {
        if (meta.doesPropertyExist(CAMERA_XMP_NAMESPACE, "MicroVideo")) {
            // This is microvideo v1 file
            int microVideo = meta.getPropertyInteger(CAMERA_XMP_NAMESPACE, "MicroVideo");
            if (microVideo == 1) {
                return 1;
            } else {
                return 0;
            }
        } else if (meta.doesPropertyExist(CAMERA_XMP_NAMESPACE, "MotionPhoto")) {
            // This is a motion photo v2 file
            int motionPhoto = meta.getPropertyInteger(CAMERA_XMP_NAMESPACE, "MotionPhoto");
            if (motionPhoto == 1) {
                return 2;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    /**
     * Extract the JPEG XMP metadata from the Motion Photo.
     */
    private static XMPMeta getFileXmp(File file) throws IOException, XMPException {
        return XmpParser.getXmpMetadata(file);
    }

    /**
     * Get the MediaFormat associated with the video track of the Motion Photo MPEG4.
     */
    @Nullable
    private static MediaFormat getFileMediaFormat(File file,
                                                  MediaExtractor extractor,
                                                  int videoOffset) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            FileDescriptor fd = fileInputStream.getFD();
            extractor.setDataSource(fd, file.length() - videoOffset, videoOffset);

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
}
