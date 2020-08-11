package com.google.android.libraries.motionphotoreader;

public class BoundingBox {

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

        error = 0.0f;
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

    public String toString() {
        return "(" + xMin + ", " + yMin + ") to " + "(" + xMax + ", " + yMax + ")";
    }
}
