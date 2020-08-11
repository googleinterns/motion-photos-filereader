package com.google.android.libraries.motionphotoreader;

/**
 * All internal constant values used within the MotionPhotoReader API.
 */
class Constants {

    /**
     * Time to wait for a blocking queue to offer buffer information when polled, in microseconds.
     */
    static final long TIMEOUT_US = 1000L;

    /**
     * Conversion factor from microseconds to nanoseconds.
     */
    static final long US_TO_NS = 1000L;

    /**
     * Use this time difference (approx. 30 FPS) between consecutive frames if a reasonable frame
     * delta cannot be computed from the frame presentation timestamps.
     */
    static final long FALLBACK_FRAME_DELTA_NS = 1_000_000_000L / 30;

    /**
     * The number of strips that each frame is divided into for stabilization. A separate
     * stabilization homography is applied to each strip. The default number of strips is 12, but
     * a single strip suffices in terms of visual results.
     */
    static final int NUM_OF_STRIPS = 12;

    /**
     * Float array for the identity matrix.
     */
    static final Float[] IDENTITY = {
            1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 1.0f
    };

    /**
     * Error threshold for two floats in a homography matrix to be considered equal.
     */
    static final float EPS = 1E-3f;

    static final int MOTION_PHOTO_V1 = 1;
    static final int MOTION_PHOTO_V2 = 2;
    static final String CAMERA_XMP_NAMESPACE = "http://ns.google.com/photos/1.0/camera/";

    /**
     * String representing the MIME type for the track that contains information about the video
     * portion of the video.
     */
    static final String MICROVIDEO_META_MIMETYPE =
            "application/microvideo-meta-stream";
    static final String MOTION_PHOTO_IMAGE_META_MIMETYPE =
            "application/motionphoto-image-meta";

    /**
     * Prefix for MIME type that represents video.
     */
    static final String VIDEO_MIME_PREFIX = "video/";

    /**
     * Offsets and constants for OpenGL rendering.
     */
    static final int FLOAT_SIZE_BYTES = 4;
    static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 3 * FLOAT_SIZE_BYTES;

    /**
     * Strings demarcating start and end of XMP metadata tag.
     */
    static final byte[] OPEN_ARR = "<x:xmpmeta".getBytes();
    static final byte[] CLOSE_ARR = "</x:xmpmeta>".getBytes();
}
