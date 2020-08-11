package com.google.android.libraries.motionphotoreader;

import android.util.Log;

import static com.google.android.libraries.motionphotoreader.Constants.EPS;

public class BoundingBox {

    private static final String TAG = "BoundingBox";

    public float xMin;
    public float yMin;
    public float xMax;
    public float yMax;

    private float error;

    public BoundingBox() {
        xMin = -1.0f;
        yMin = -1.0f;
        xMax = 1.0f;
        yMax = 1.0f;
        error = 0.0f;
    }

    public BoundingBox(float[] a, float[] b, float[] c, float[] d) {
        xMin = Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0]));
        yMin = Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1]));
        xMax = Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0]));
        yMax = Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]));

        float errorA = Math.min(Math.abs(a[0] - xMin), Math.abs(a[0] - xMax));
        if (errorA < EPS) {
            errorA = Math.min(Math.abs(a[1] - yMin), Math.abs(a[1] - yMax));
        }

        float errorB = Math.min(Math.abs(b[0] - xMin), Math.abs(b[0] - xMax));
        if (errorB < EPS) {
            errorB = Math.min(Math.abs(b[1] - yMin), Math.abs(b[1] - yMax));
        }

        float errorC = Math.min(Math.abs(c[0] - xMin), Math.abs(c[0] - xMax));
        if (errorC < EPS) {
            errorC = Math.min(Math.abs(c[1] - yMin), Math.abs(c[1] - yMax));
        }

        float errorD = Math.min(Math.abs(d[0] - xMin), Math.abs(d[0] - xMax));
        if (errorD < EPS) {
            errorD = Math.min(Math.abs(d[1] - yMin), Math.abs(d[1] - yMax));
        }

        error = (float) Math.sqrt(2.0) * Math.max(Math.max(errorA, errorB), Math.max(errorC, errorD));
    }

    public BoundingBox intersect(BoundingBox other) {
        BoundingBox intersection = new BoundingBox();
        intersection.xMin = Math.max(this.xMin, other.xMin);
        intersection.yMin = Math.max(this.yMin, other.yMin);
        intersection.xMax = Math.min(this.xMax, other.xMax);
        intersection.yMax = Math.min(this.yMax, other.yMax);
        return intersection;
    }

    public float width() {
        return xMax - xMin;
    }

    public float height() {
        return yMax - yMin;
    }

    public float error() {
        return error;
    }

    public String toString() {
        return "(" + xMin + ", " + yMin + ") to " + "(" + xMax + ", " + yMax + ")";
    }
}
