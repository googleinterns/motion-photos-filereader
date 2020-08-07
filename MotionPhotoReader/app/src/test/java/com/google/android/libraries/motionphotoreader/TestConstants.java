package com.google.android.libraries.motionphotoreader;

public class TestConstants {

    /**
     * MotionPhotoInfoTest.java
     */
    static final int KEY_WIDTH = 4032;
    static final int KEY_HEIGHT = 3024;
    static final int KEY_ROTATION = 90;
    static final String KEY_MIME = "video/avc";
    static final int VIDEO_OFFSET = 2592317;

    static final int VIDEO_TRACK_INDEX = 0;
    static final int MOTION_TRACK_INDEX = 2;

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
     * HomographyMatrix.java
     */
    static final double THETA_DEGREES_A = 45.0;
    static final double THETA_DEGREES_B = 60.0;
    static final int VIDEO_WIDTH_PIXELS = 4032;
    static final int VIDEO_HEIGHT_PIXELS = 3024;
}
