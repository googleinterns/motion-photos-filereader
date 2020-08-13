package com.google.android.libraries.motionphotoreader;

class TestConstants {

    /**
     * Attributes for a mock video media format.
     */
    public static final int KEY_WIDTH = 4032;
    public static final int KEY_HEIGHT = 3024;
    public static final int KEY_ROTATION = 90;
    public static final String KEY_MIME = "video/avc";
    public static final int VIDEO_OFFSET = 2592317;

    /**
     * Track indices for a mock media extractor.
     */
    public static final int VIDEO_TRACK_INDEX = 0;
    public static final int MOTION_TRACK_INDEX = 2;

    /**
     * Attributes for a mock v1 motion photo video format.
     */
    public static final String FILENAME_V1 = "MVIMG_20200621_200240.jpg";
    public static final long KEY_DURATION_V1 = 1499400;
    public static final int KEY_ROTATION_V1 = 90;
    public static final int VIDEO_OFFSET_V1 = 2592317;

    /**
     * Attributes for a mock v2 motion photo video format.
     */
    public static final String FILENAME_V2 = "PXL_20200710_061629144.MP.jpg";
    public static final long KEY_DURATION_V2 = 763422;
    public static final int KEY_ROTATION_V2 = 0;
    public static final int VIDEO_OFFSET_V2 = 1317283;

    /**
     * Degrees for creating rotation matrices to test the HomographyMatrix class.
     */
    public static final double THETA_DEGREES_A = 45.0;
    public static final double THETA_DEGREES_B = 60.0;
    public static final int VIDEO_WIDTH_PIXELS = 4032;
    public static final int VIDEO_HEIGHT_PIXELS = 3024;
}
