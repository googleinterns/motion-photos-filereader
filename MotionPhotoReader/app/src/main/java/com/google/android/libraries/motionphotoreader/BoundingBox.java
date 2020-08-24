package com.google.android.libraries.motionphotoreader;

import android.util.Log;

import androidx.annotation.NonNull;

import static com.google.android.libraries.motionphotoreader.Constants.EPS;

/**
 * A BoundingBox object represents the bounding box for a quadrilateral, i.e. the box enclosing the
 * quadrilateral with the smallest measure.
 */
public class BoundingBox {

    private static final String TAG = "BoundingBox";

    /**
     * The extreme coordinates of the bounding box.
     */
    public final float xMin;
    public final float yMin;
    public final float xMax;
    public final float yMax;

    /**
     * A pseudo-measurement of how well the bounding box approximates the quadrilateral. This is
     * computed by taking the maximum distance from a vertex of the quadrilateral to a neighboring
     * vertex of the bounding box. Thus, the error lies in the range [0, max(W, H) / 2], where
     * W is the width of the bounding box and H is the height of the bounding box.
     */
    private final float error;

    /**
     * Constructor for a bounding box using the bounding box coordinates.
     * @param xMin The x-coordinate of the lower-left vertex of the bounding box.
     * @param yMin The y-coordinate of the lower=left vertex of the bounding box.
     * @param xMax The x-coordinate of the upper-right vertex of the bounding box.
     * @param yMax The y-coordinate of the upper-right vertex of the bounding box.
     */
    public BoundingBox(float xMin, float yMin, float xMax, float yMax) {
        this.xMin = xMin;
        this.yMin = yMin;
        this.xMax = xMax;
        this.yMax = yMax;
        error = 0.0f;
    }

    /**
     * Constructor for a bounding box using a given quadrilateral.
     * The arguments a, b, c, d are all homogeneous 2D coordinates (x, y, 1) of the vertices of the
     * quadrilateral.
     */
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

        error = (float) Math.sqrt(2.0) *
                Math.max(Math.max(errorA, errorB), Math.max(errorC, errorD));
    }

    /**
     * Computes the bounding box that is the intersection of this bounding box and another
     * bounding box.
     * @param other The other bounding box to intersect with this bounding box.
     * @return A BoundingBox object representing the intersection.
     */
    public BoundingBox intersect(BoundingBox other) {
        BoundingBox intersection = new BoundingBox(
                Math.max(this.xMin, other.xMin),
                Math.max(this.yMin, other.yMin),
                Math.min(this.xMax, other.xMax),
                Math.min(this.yMax, other.yMax)
        );
        return intersection;
    }

    /**
     * Returns the width of the bounding box.
     */
    public float width() {
        return xMax - xMin;
    }

    /**
     * Returns the height of the bounding box.
     */
    public float height() {
        return yMax - yMin;
    }

    /**
     * Returns the error of this bounding box.
     */
    public float error() {
        return error;
    }

    @NonNull
    public String toString() {
        return "(" + xMin + ", " + yMin + ") to " + "(" + xMax + ", " + yMax + ")";
    }
}
