package com.example.motionphotoreader;

import android.util.Log;

import androidx.annotation.Nullable;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * The XmpParser class is a package-private class intended to help extract the microvideo offset
 * information from the XMP metadata of the given Motion Photo. This class is for internal use only.
 */
class XmpParser {

    private static final byte[] OPEN_ARR = "<x:xmpmeta".getBytes();  /* Start of XMP metadata tag */
    private static final byte[] CLOSE_ARR = "</x:xmpmeta>".getBytes();  /* End of XMP metadata tag */

//    /**
//     * Copies the input stream from a file to an output stream.
//     */
//    private static void copy(String filename, InputStream in, OutputStream out) throws IOException {
//        int len;
//        byte[] buf = new byte[1024];
//        while((len = in.read(buf)) >= 0) {
//            out.write(buf, 0, len);
//        }
//    }

    /**
     * Returns the index of the first appearance of a given subsequence in a byte array.
     * @param arr The full byte array
     * @param seq The subsequence of bytes to find
     * @param start The index at which to start searching
     * @return The index of the first appearance of the beginning of the subsequence in the
     * byte array. If the sequence is not found, return -1.
     */
    private static int indexOf(byte[] arr, byte[] seq, int start) {
        int subIdx = 0;
        for (int x = start; x < arr.length; x++) {
            if (arr[x] == seq[subIdx]) {
                if (subIdx == seq.length - 1) {
                    return x - subIdx;
                }
                subIdx++;
            }
            else {
                subIdx = 0;
            }
        }
        return -1;
    }

    /**
     * Returns the metadata of the Motion Photo file.
     */
    @Nullable
    public static XMPMeta getXmpMetadata(String filename) throws IOException, XMPException {
        try (FileInputStream in = new FileInputStream(filename)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteStreams.copy(in, out);

            byte[] fileData = out.toByteArray();

            int openIdx = indexOf(fileData, OPEN_ARR, 0);
            if (openIdx >= 0) {
                int closeIdx = indexOf(fileData, CLOSE_ARR, openIdx + 1) + CLOSE_ARR.length;

                byte[] segArr = Arrays.copyOfRange(fileData, openIdx, closeIdx);
                return XMPMetaFactory.parseFromBuffer(segArr);
            }
        }
        return null;
    }
}