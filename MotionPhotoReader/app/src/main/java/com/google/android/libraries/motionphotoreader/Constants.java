package com.google.android.libraries.motionphotoreader;

/**
 * All internal constant values used within the MotionPhotoReader API.
 */
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

    static final int MOTION_PHOTO_V1 = 1;
    static final int MOTION_PHOTO_V2 = 2;
    static final String CAMERA_XMP_NAMESPACE = "http://ns.google.com/photos/1.0/camera/";
    static final String V2_XMP_PROP_PREFIX = "Container:Directory[";
    static final String V2_XMP_PROP_LENGTH_SUFFIX = "]/Container:Item/Item:Length";
    static final String V2_XMP_PROP_PADDING_SUFFIX = "]/Container:Item/Item:Length";

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

    /* *********************************************************************************************
     * Tests
     * ********************************************************************************************/

    /**
     * MotionPhotoReaderTest.java
     */
    static final int NUM_FRAMES = 43;
    static final long SEEK_AMOUNT_US = 10_000L;

    /**
     * MotionPhotoInfoTest.java
     */
    static final int KEY_WIDTH = 4032;
    static final int KEY_HEIGHT = 3024;
    static final String KEY_MIME = "video/avc";

    // Define a mock media format for motion photo v1 format
    static final String FILENAME_V1 = "MVIMG_20200621_200240.jpg";
    static final long KEY_DURATION_V1 = 1499400;
    static final int KEY_ROTATION_V1 = 90;
    static final int VIDEO_OFFSET_V1 = 2592317;

    // Define a mock media format for motion photo v2 format
    static final String FILENAME_V2 = "PXL_20200710_061629144.MP.jpg";
    static final long KEY_DURATION_V2 = 763422;
    static final int KEY_ROTATION_V2 = 0;
    static final int VIDEO_OFFSET_V2 = 1317283;

    /**
     * MotionPhotoUtilsTest.java
     */
    static final int INPUT_BUFFER_QUEUE_SIZE = 3;
    static final int OUTPUT_BUFFER_QUEUE_SIZE = 3;
    static final int SAMPLE_BUFFER_INDEX = 2;
    static final int EMPTY_SAMPLE_SIZE = -1;
    static final int SAMPLE_SIZE = 12;
    static final long SAMPLE_PRESENTATION_TIME_US = 1000L;


    /**
     * HomographyMatrix.java
     */
    static final double THETA_DEGREES_A = 45.0;
    static final double THETA_DEGREES_B = 60.0;
    static final int VIDEO_WIDTH_PIXELS = 4032;
    static final int VIDEO_HEIGHT_PIXELS = 3024;

    /* *********************************************************************************************
     * Directory names.
     * ********************************************************************************************/

    static final String MOTION_PHOTOS_DIR = "motionphotos/";
}
