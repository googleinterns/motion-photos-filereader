package com.google.android.libraries.motionphotoreader;

import org.junit.Before;
import org.junit.Test;

import static com.google.android.libraries.motionphotoreader.Constants.THETA_DEGREES_A;
import static com.google.android.libraries.motionphotoreader.Constants.THETA_DEGREES_B;
import static com.google.android.libraries.motionphotoreader.Constants.VIDEO_HEIGHT_PIXELS;
import static com.google.android.libraries.motionphotoreader.Constants.VIDEO_WIDTH_PIXELS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomographyMatrixTest {

    private static final HomographyMatrix I = new HomographyMatrix();
    
    private HomographyMatrix A;
    private HomographyMatrix B;
    private HomographyMatrix BA;
    private HomographyMatrix AB;

    @Before
    public void setUp() {
        float cosThetaA = (float) Math.cos(Math.toRadians(THETA_DEGREES_A));
        float sinThetaA = (float) Math.sin(Math.toRadians(THETA_DEGREES_A));
        float cosThetaB = (float) Math.cos(Math.toRadians(THETA_DEGREES_B));
        float sinThetaB = (float) Math.sin(Math.toRadians(THETA_DEGREES_B));

        // Rotation around z-axis by 45 degrees
        A = new HomographyMatrix();
        A.set(0, 0, cosThetaA);
        A.set(0, 1, -sinThetaA);
        A.set(1, 0, sinThetaA);
        A.set(1, 1, cosThetaA);

        // Rotation around x-axis by 60 degrees
        B = new HomographyMatrix();
        B.set(1, 1, cosThetaB);
        B.set(1, 2, -sinThetaB);
        B.set(2, 1, sinThetaB);
        B.set(2, 2, cosThetaB);

        // Rotation around z-axis by 45 degrees followed by rotation around x-axis by 60 degrees
        BA = new HomographyMatrix();
        BA.set(0, 0, cosThetaA);
        BA.set(0, 1, -sinThetaA);
        BA.set(0, 2, 0.0f);
        BA.set(1, 0, cosThetaB * sinThetaA);
        BA.set(1, 1, cosThetaB * cosThetaA);
        BA.set(1, 2, -sinThetaB);
        BA.set(2, 0, sinThetaB * sinThetaA);
        BA.set(2, 1, sinThetaB * cosThetaA);
        BA.set(2, 2, cosThetaB);

        // Rotation around x-axis by 60 degrees followed by rotation around z-axis by 45 degrees
        AB = new HomographyMatrix();
        AB.set(0, 0, cosThetaA);
        AB.set(0, 1, -sinThetaA * cosThetaB);
        AB.set(0, 2, sinThetaA * sinThetaB);
        AB.set(1, 0, sinThetaA);
        AB.set(1, 1, cosThetaA * cosThetaB);
        AB.set(1, 2, -cosThetaA * sinThetaB);
        AB.set(2, 0, 0.0f);
        AB.set(2, 1, sinThetaB);
        AB.set(2, 2, cosThetaB);
    }

    @Test
    public void equals_isEqual_isCorrect() {
        HomographyMatrix Aprime = new HomographyMatrix();
        Aprime.set(0, 0, (float) Math.cos(Math.PI / 4));
        Aprime.set(0, 1, (float) -Math.sin(Math.PI / 4));
        Aprime.set(1, 0, (float) Math.sin(Math.PI / 4));
        Aprime.set(1, 1, (float) Math.cos(Math.PI / 4));

        assertTrue("Matrices are not equal", A.equals(Aprime));
        assertTrue("Matrices are not equal", A.equals(A));
        assertTrue("Matrices are not equal", B.equals(B));
        assertTrue("Matrices are not equal", BA.equals(BA));
    }

    @Test
    public void equals_isNotEqual_isCorrect() {
        assertFalse("Matrices are equal", A.equals(B));
        assertFalse("Matrices are equal", B.equals(BA));
        assertFalse("Matrices are equal", BA.equals(A));
    }

    @Test
    public void leftMultiplyBy_isCorrect() {
        assertTrue("Matrices are not equal", BA.equals(A.leftMultiplyBy(B)));
        assertTrue("Matrices are not equal", AB.equals(B.leftMultiplyBy(A)));
    }

    @Test
    public void rightMultiplyBy_isCorrect() {
        assertTrue("Matrices are not equal", AB.equals(A.rightMultiplyBy(B)));
        assertTrue("Matrices are not equal", BA.equals(B.rightMultiplyBy(A)));
    }

    @Test
    public void convertFromImageToGL_isCorrect() {
        HomographyMatrix imageCoords = new HomographyMatrix();
        imageCoords.set(0, 0, VIDEO_WIDTH_PIXELS);
        imageCoords.set(1, 1, VIDEO_HEIGHT_PIXELS);
        HomographyMatrix glCoords = imageCoords.convertFromImageToGL(
                VIDEO_WIDTH_PIXELS,
                VIDEO_HEIGHT_PIXELS
        );

        HomographyMatrix glTrueCoords = new HomographyMatrix();
        glTrueCoords.set(0, 0, VIDEO_WIDTH_PIXELS);
        glTrueCoords.set(0, 2, VIDEO_WIDTH_PIXELS - 1);
        glTrueCoords.set(1, 1, VIDEO_HEIGHT_PIXELS);
        glTrueCoords.set(1, 2, 1 - VIDEO_HEIGHT_PIXELS);
        assertTrue(
                "Expected matrix \n" + glTrueCoords + "\nbut received matrix \n" + glCoords,
                glCoords.equals(glTrueCoords)
        );
    }
}
