package com.google.android.libraries.motionphotoreader;

import android.media.MediaExtractor;
import android.media.MediaFormat;

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

import static com.google.android.libraries.motionphotoreader.Constants.CAMERA_XMP_NAMESPACE;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V1;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V2;

/**
 * Contains information relevant to extracting frames in a Motion Photo file.
 *
 * The fields stored are:
 *   width: The width of the video (before camera orientation rotations are applied) in pixels.
 *   height: The height of the video (before camera orientation rotations are applied) in pixels.
 *   durationUs: The duration of the video in microseconds.
 *   rotation: The camera orientation while the motion photo was taken, as a rotation about the
 *   z-axis, measured in degrees. Holding the phone horizontally with the top of the phone on the
 *   left corresponds to 0 degrees, and all rotations are measured counterclockwise from this
 *   orientation.
 *   videoOffset: The byte offset from the end of the file at which the video track begins.
 *   version: The version of this motion photo (either v1 or v2).
 */
@RequiresApi(api = 23)
public class MotionPhotoInfo {

    private final static String TAG = "MotionPhotoInfo";

    private static final String V2_XMP_PROP_PREFIX = "Container:Directory[";
    private static final String V2_XMP_PROP_LENGTH_SUFFIX = "]/Container:Item/Item:Length";
    private static final String V2_XMP_PROP_PADDING_SUFFIX = "]/Container:Item/Item:Length";

    private final int width;
    private final int height;
    private final long durationUs;
    private final int rotation;
    private final int videoOffset;
    private final int version;

    /**
     * Creates a MotionPhotoInfo object associated with a given file.
     */
    @VisibleForTesting
    MotionPhotoInfo(MediaFormat mediaFormat, int videoOffset, int version) {
        width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        durationUs = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        rotation = mediaFormat.containsKey(MediaFormat.KEY_ROTATION)
                ? mediaFormat.getInteger(MediaFormat.KEY_ROTATION)
                : 0;
        this.videoOffset = videoOffset;
        this.version = version;
    }

    /**
     * Returns a new instance of MotionPhotoInfo for a specified file.
     */
    public static MotionPhotoInfo newInstance(File file) throws IOException, XMPException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            XMPMeta meta = getFileXmp(file);
            int version = getMotionPhotoVersion(meta);
            int videoOffset = getVideoOffset(meta, version);
            MediaFormat mediaFormat = getFileMediaFormat(file, new MediaExtractor(), videoOffset);
            return new MotionPhotoInfo(mediaFormat, videoOffset, version);
        } finally {
            extractor.release();
        }
    }

    public static MotionPhotoInfo newInstance(String filename) throws IOException, XMPException {
        return MotionPhotoInfo.newInstance(new File(filename));
    }

    /**
     * Finds the video offset encoded in the motion photo XMP metadata.
     * @param meta The XMP metadata for the motion photo file.
     * @param version The version of this motion photo file.
     * @return the number of bytes from the end of the motion photo file to the beginning of the
     * video track.
     * @throws XMPException when parsing invalid XMP metadata.
     */
    private static int getVideoOffset(XMPMeta meta, int version) throws XMPException {
        int videoOffset = 0;
        switch (version) {
            case MOTION_PHOTO_V1:
                videoOffset = meta.getPropertyInteger(CAMERA_XMP_NAMESPACE, "MicroVideoOffset");
                break;
            case MOTION_PHOTO_V2:
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
                    String lengthProperty = V2_XMP_PROP_PREFIX
                            + arrayItemIdx
                            + V2_XMP_PROP_LENGTH_SUFFIX;
                    String paddingProperty = V2_XMP_PROP_PREFIX
                            + arrayItemIdx
                            + V2_XMP_PROP_PADDING_SUFFIX;
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
            // This is a motion photo v1 file
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

    public long getDurationUs() {
        return durationUs;
    }

    public int getRotation() {
        return rotation;
    }

    public int getVersion() { return version; }
}
