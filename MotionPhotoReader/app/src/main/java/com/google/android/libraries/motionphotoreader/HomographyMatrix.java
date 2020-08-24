package com.google.android.libraries.motionphotoreader;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.android.libraries.motionphotoreader.Constants.EPS;
import static com.google.android.libraries.motionphotoreader.Constants.IDENTITY;

/**
 * Represents a 3x3 homography transformation in row-major matrix form.
 */
class HomographyMatrix {

    // A list representation of the matrix, stored in row-major order.
    private final List<Float> matrix;

    /**
     * The default constructor sets the matrix to an identity matrix.
     */
    public HomographyMatrix() {
        matrix = new ArrayList<>();
        matrix.addAll(Arrays.asList(IDENTITY));
    }

    /**
     * Creates a matrix out of a list of floats, stored in row-major order. The list must contain
     * exactly nine elements.
     */
    public HomographyMatrix(List<Float> matrix) {
        Preconditions.checkArgument(matrix.size() == 9,
                "Provided matrix must have exactly 9 elements");
        this.matrix = matrix;
    }

    /**
     * Creates a matrix out of an array of floats, stored in row-major order. The array must contain
     * exactly nine elements.
     */
    public HomographyMatrix(float[] matrix) {
        Preconditions.checkArgument(matrix.length == 9,
                "Provided matrix must have exactly 9 elements");
        this.matrix = new ArrayList<>();
        for (float f : matrix) {
            this.matrix.add(f);
        }
    }

    /**
     * Multiply this matrix on the left by another homography matrix.
     * @param otherMatrix The homography matrix on the left of the product.
     * @return a HomographyMatrix containing the product of the two matrices.
     */
    public HomographyMatrix leftMultiplyBy(HomographyMatrix otherMatrix) {
        List<Float> product = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float matrixElement = otherMatrix.get(r, 0) * this.get(0, c) +
                        otherMatrix.get(r, 1) * this.get(1, c) +
                        otherMatrix.get(r, 2) * this.get(2, c);
                product.add(matrixElement);
            }
        }
        return new HomographyMatrix(product);
    }

    /**
     * Multiply this matrix on the right by another homography matrix.
     * @param otherMatrix The homography matrix on the right of the product.
     * @return a HomographyMatrix containing the product of the two matrices.
     */
    public HomographyMatrix rightMultiplyBy(HomographyMatrix otherMatrix) {
        List<Float> product = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float matrixElement = this.get(r, 0) * otherMatrix.get(0, c) +
                        this.get(r, 1) * otherMatrix.get(1, c) +
                        this.get(r, 2) * otherMatrix.get(2, c);
                product.add(matrixElement);
            }
        }
        return new HomographyMatrix(product);
    }

    public float[] leftMultiplyBy(float[] vector) {
        float[] product = new float[3];
        for (int i = 0; i < 3; i++) {
            float vectorElement = vector[0] * this.get(0, i) +
                    vector[1] * this.get(1, i) +
                    vector[2] * this.get(2, i);
            product[i] = vectorElement;
        }
        return product;
    }

    public float[] rightMultiplyBy(float[] vector) {
        float[] product = new float[3];
        for (int i = 0; i < 3; i++) {
            float vectorElement = this.get(i, 0) * vector[0] +
                    this.get(i, 1) * vector[1] +
                    this.get(i, 2) * vector[2];
            product[i] = vectorElement;
        }
        return product;
    }

    public static float distanceBetween(float[] a, float[] b) {
        return (float) Math.sqrt(Math.pow(a[0] - b[0], 2) +
                Math.pow(a[1] - b[1], 2) +
                Math.pow(a[2] - b[2], 2));
    }

    /**
     * Get the entry of this matrix at row r, column c (both zero-indexed).
     */
    public float get(int r, int c) {
        return matrix.get(3 * r + c);
    }

    /**
     * Set the entry of this matrix at row r, column c (both zero-indexed) to the value val.
     */
    public void set(int r, int c, float val) {
        matrix.set(3 * r + c, val);
    }

    /**
     * Add two matrices together.
     * @param otherMatrix The matrix to add to this instance.
     * @return a HomographyMatrix containing the sum of this matrix and the other matrix.
     */
    public HomographyMatrix add(HomographyMatrix otherMatrix) {
        List<Float> sum = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float matrixElement = otherMatrix.get(r, c) + this.get(r, c);
                sum.add(matrixElement);
            }
        }
        return new HomographyMatrix(sum);
    }

    /**
     * Multiply this matrix by a scalar s.
     * @return a HomographyMatrix object containing the scaled matrix.
     */
    public HomographyMatrix multiplyScalar(float s) {
        List<Float> scaled = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float matrixElement = s * this.get(r, c);
                scaled.add(matrixElement);
            }
        }
        return new HomographyMatrix(scaled);
    }

    public static HomographyMatrix createRotationMatrixX(float degrees) {
        float cosTheta = (float) Math.cos(Math.toRadians(degrees));
        float sinTheta = (float) Math.sin(Math.toRadians(degrees));

        HomographyMatrix rotationMatrix = new HomographyMatrix();
        rotationMatrix.set(1, 1, cosTheta);
        rotationMatrix.set(1, 2, -sinTheta);
        rotationMatrix.set(2, 1, sinTheta);
        rotationMatrix.set(2, 2, cosTheta);

        return rotationMatrix;
    }

    public static HomographyMatrix createRotationMatrixY(float degrees) {
        float cosTheta = (float) Math.cos(Math.toRadians(degrees));
        float sinTheta = (float) Math.sin(Math.toRadians(degrees));

        HomographyMatrix rotationMatrix = new HomographyMatrix();
        rotationMatrix.set(0, 0, cosTheta);
        rotationMatrix.set(0, 2, sinTheta);
        rotationMatrix.set(2, 0, -sinTheta);
        rotationMatrix.set(2, 2, cosTheta);

        return rotationMatrix;
    }

    public static HomographyMatrix createRotationMatrixZ(float degrees) {
        float cosTheta = (float) Math.cos(Math.toRadians(degrees));
        float sinTheta = (float) Math.sin(Math.toRadians(degrees));

        HomographyMatrix rotationMatrix = new HomographyMatrix();
        rotationMatrix.set(0, 0, cosTheta);
        rotationMatrix.set(0, 1, -sinTheta);
        rotationMatrix.set(1, 0, sinTheta);
        rotationMatrix.set(1, 1, cosTheta);

        return rotationMatrix;
    }

    public static HomographyMatrix createScaleMatrix(float xScale, float yScale) {
        HomographyMatrix scaleMatrix = new HomographyMatrix();
        scaleMatrix.set(0,  0, xScale);
        scaleMatrix.set(1,  1, yScale);
        return scaleMatrix;
    }

    /**
     * Convert this homography transform from the pixel coordinate system basis to the OpenGL
     * frame coordinate system basis ([-1, 1] x [-1, 1]).
     * @param imageWidth The width of the pixel coordinate system (i.e. width of the image).
     * @param imageHeight The height of the pixel coordinate system (i.e. height of the image).
     * @return a HommographyMatrix representing the homoggraphy transform in the OpenGL frame
     * coordinate system.
     */
    public HomographyMatrix convertFromImageToGL(int imageWidth, int imageHeight) {
        float halfW = imageWidth * 1.0f / 2.0f;
        float halfH = imageHeight * 1.0f / 2.0f;

        // Change of basis matrices
        float[] t1 = {
                halfW,  0.0f, halfW,
                0.0f, -halfH, halfH,
                0.0f,   0.0f, 1.0f
        };
        float[] t2 = {
                1.0f / halfW,  0.0f, -1.0f,
                0.0f, -1.0f / halfH,  1.0f,
                0.0f,          0.0f,  1.0f
        };

        List<Float> l1 = new ArrayList<>();
        List<Float> l2 = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            l1.add(t1[i]);
            l2.add(t2[i]);
        }

        HomographyMatrix h1 = new HomographyMatrix(l1);
        HomographyMatrix h2 = new HomographyMatrix(l2);

        // P^-1 * M * P
        return this.rightMultiplyBy(h1).leftMultiplyBy(h2);
    }

    /**
     * Check if this matrix equals another matrix, element-wise.
     * @return true if the two matrices are equal (up to a small error), otherwise return false.
     */
    public boolean equals(HomographyMatrix otherMatrix) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (Math.abs(this.get(r, c) - otherMatrix.get(r, c)) > EPS) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns a string containing the elements in this homography listed in row-major order.
     */
    @NonNull
    public String toString() {
        return Arrays.toString(matrix.toArray());
    }
}
