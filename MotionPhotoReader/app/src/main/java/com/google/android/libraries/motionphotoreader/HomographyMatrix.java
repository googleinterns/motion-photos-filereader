package com.google.android.libraries.motionphotoreader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.android.libraries.motionphotoreader.Constants.EPS;
import static com.google.android.libraries.motionphotoreader.Constants.IDENTITY;

/**
 * Represents a 3x3 homography transformation in row-major matrix form.
 */
class HomographyMatrix {

    private List<Float> matrix = new ArrayList<>();

    public HomographyMatrix() {
        for (float f : IDENTITY) {
            matrix.add(f);
        }
    }

    public HomographyMatrix(List<Float> matrix) {
        if (matrix.size() != 9) {
            throw new RuntimeException("List has incorrect number of elements: " + matrix.size());
        } else {
            this.matrix = matrix;
        }
    }

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

    public float get(int r, int c) {
        return matrix.get(3 * r + c);
    }

    public void set(int r, int c, float val) {
        matrix.set(3 * r + c, val);
    }

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

    public HomographyMatrix convertFromImageToGL(int imageWidth, int imageHeight) {
        float halfW = imageWidth * 1.0f / 2.0f;
        float halfH = imageHeight * 1.0f / 2.0f;

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

        return this.rightMultiplyBy(h1).leftMultiplyBy(h2);
    }

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

    public String toString() {
        return Arrays.toString(matrix.toArray());
    }
}
