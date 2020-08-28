package com.google.android.libraries.motionphotoreader;

import androidx.annotation.NonNull;

/**
 * A BoundingBox object represents the bounding box for a quadrilateral, i.e. the box enclosing the
 * quadrilateral with the smallest measure.
 */
class BoundingBox {

    private static final String TAG = "BoundingBox";

    /**
     * The extreme coordinates of the bounding box.
     */
    public final float xMin;
    public final float yMin;
    public final float xMax;
    public final float yMax;

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
    }

    /**
     * Constructor for a bounding box using a given quadrilateral.
     * The arguments a, b, c, d are all 2D coordinates (x, y) of the vertices of the quadrilateral.
     */
    public BoundingBox(float[] a, float[] b, float[] c, float[] d) {
        xMin = Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0]));
        yMin = Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1]));
        xMax = Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0]));
        yMax = Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]));
    }

    /**
     * Computes the bounding box that is the intersection of this bounding box and another
     * bounding box.
     * @param other The other bounding box to intersect with this bounding box.
     * @return A BoundingBox object representing the intersection.
     */
    public BoundingBox intersect(BoundingBox other) {
        return new BoundingBox(
                Math.max(this.xMin, other.xMin),
                Math.max(this.yMin, other.yMin),
                Math.min(this.xMax, other.xMax),
                Math.min(this.yMax, other.yMax)
        );
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

    @NonNull
    public String toString() {
        return "(" + xMin + ", " + yMin + ") to " + "(" + xMax + ", " + yMax + ")";
    }
}
