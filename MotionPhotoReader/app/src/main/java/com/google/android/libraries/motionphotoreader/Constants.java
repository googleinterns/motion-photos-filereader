package com.google.android.libraries.motionphotoreader;

class Constants {

    /* *********************************************************************************************
     * BufferProcessor.java
     * ********************************************************************************************/

    static final long TIMEOUT_US = 1000L;
    static final long US_TO_NS = 1000L;
    static final long FALLBACK_FRAME_DELTA_NS = 1_000_000_000L / 30;
    static final int NUM_OF_STRIPS = 12;

    /* *********************************************************************************************
     * HomographyMatrix.java
     * ********************************************************************************************/

    static final float[] IDENTITY = {
            1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 1.0f
    };

    static final float EPS = 1E-3f;

    /* *********************************************************************************************
     * MotionPhotoInfo.java
     * ********************************************************************************************/

    static final int MOTION_PHOTO_VERSION_V1 = 1;
    static final int MOTION_PHOTO_VERSION_V2 = 2;
    static final String CAMERA_XMP_NAMESPACE = "http://ns.google.com/photos/1.0/camera/";

    /* *********************************************************************************************
     * MotionPhotoReader.java
     * ********************************************************************************************/

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

    /* *********************************************************************************************
     * TextureRender.java
     * ********************************************************************************************/

    static final int FLOAT_SIZE_BYTES = 4;
    static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 3 * FLOAT_SIZE_BYTES;

    /* *********************************************************************************************
     * XmpParser.java
     * ********************************************************************************************/

    static final byte[] OPEN_ARR = "<x:xmpmeta".getBytes();          /* Start of XMP metadata tag */
    static final byte[] CLOSE_ARR = "</x:xmpmeta>".getBytes();       /* End of XMP metadata tag   */
}
