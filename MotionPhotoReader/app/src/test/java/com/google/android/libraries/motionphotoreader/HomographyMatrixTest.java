package com.google.android.libraries.motionphotoreader;

import org.junit.Before;
import org.junit.Test;

import static com.google.android.libraries.motionphotoreader.TestConstants.THETA_DEGREES_A;
import static com.google.android.libraries.motionphotoreader.TestConstants.THETA_DEGREES_B;
import static com.google.android.libraries.motionphotoreader.TestConstants.VIDEO_HEIGHT_PIXELS;
import static com.google.android.libraries.motionphotoreader.TestConstants.VIDEO_WIDTH_PIXELS;
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


        // Rotation around x-axis by 45 degrees
        A = HomographyMatrix.createRotationMatrixX(THETA_DEGREES_A);

        // Rotation around z-axis by 60 degrees
        B = HomographyMatrix.createRotationMatrixZ(THETA_DEGREES_B);

        // Rotation around z-axis by 45 degrees followed by rotation around x-axis by 60 degrees
        BA = new HomographyMatrix();
        BA.set(0, 0, cosThetaB);
        BA.set(0, 1, -cosThetaA * sinThetaB);
        BA.set(0, 2, sinThetaA * sinThetaB);
        BA.set(1, 0, sinThetaB);
        BA.set(1, 1, cosThetaA * cosThetaB);
        BA.set(1, 2, -sinThetaA * cosThetaB);
        BA.set(2, 0, 0.0f);
        BA.set(2, 1, sinThetaA);
        BA.set(2, 2, cosThetaA);

        // Rotation around x-axis by 60 degrees followed by rotation around z-axis by 45 degrees
        AB = new HomographyMatrix();
        AB.set(0, 0, cosThetaB);
        AB.set(0, 1, -sinThetaB);
        AB.set(0, 2, 0.0f);
        AB.set(1, 0, cosThetaA * sinThetaB);
        AB.set(1, 1, cosThetaA * cosThetaB);
        AB.set(1, 2, -sinThetaA);
        AB.set(2, 0, sinThetaA * sinThetaB);
        AB.set(2, 1, sinThetaA * cosThetaB);
        AB.set(2, 2, cosThetaA);
    }

    @Test
    public void equals_isEqual_isCorrect() {
        HomographyMatrix Aprime = new HomographyMatrix();
        Aprime.set(1, 1, (float) Math.cos(Math.PI / 4));
        Aprime.set(1, 2, (float) -Math.sin(Math.PI / 4));
        Aprime.set(2, 1, (float) Math.sin(Math.PI / 4));
        Aprime.set(2, 2, (float) Math.cos(Math.PI / 4));

        assertTrue("Matrices are not equal", A.equals(Aprime));
        assertTrue("Matrices are not equal", A.equals(A));
        assertTrue("Matrices are not equal", B.equals(B));
        assertTrue("Matrices are not equal", BA.equals(BA));
    }

    @Test
    public void equals_isNotEqual_isCorrect() {
        assertFalse("Matrices are equal: \n" + A + "\n" + B, A.equals(B));
        assertFalse("Matrices are equal: \n" + B + "\n" + BA, B.equals(BA));
        assertFalse("Matrices are equal: \n" + BA + "\n" + A, BA.equals(A));
    }

    @Test
    public void leftMultiplyBy_isCorrect() {
        HomographyMatrix BtimesA = A.leftMultiplyBy(B);
        HomographyMatrix AtimesB = B.leftMultiplyBy(B);
        assertTrue(
                "Expected matrix \n" + BA + "\nbut received matrix \n" + BtimesA,
                BA.equals(A.leftMultiplyBy(B))
        );
        assertTrue(
                "Expected matrix \n" + AB + "\nbut received matrix \n" + AtimesB,
                AB.equals(B.leftMultiplyBy(A))
        );
    }

    @Test
    public void rightMultiplyBy_isCorrect() {
        HomographyMatrix BtimesA = B.rightMultiplyBy(A);
        HomographyMatrix AtimesB = A.rightMultiplyBy(B);
        assertTrue(
                "Expected matrix \n" + BA + "\nbut received matrix \n" + BtimesA,
                BA.equals(B.rightMultiplyBy(A))
        );
        assertTrue(
                "Expected matrix \n" + AB + "\nbut received matrix \n" + AtimesB,
                AB.equals(A.rightMultiplyBy(B))
        );
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
