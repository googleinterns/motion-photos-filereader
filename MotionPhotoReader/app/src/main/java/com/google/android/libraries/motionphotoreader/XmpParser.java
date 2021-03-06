package com.google.android.libraries.motionphotoreader;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import static com.google.android.libraries.motionphotoreader.Constants.CLOSE_ARR;
import static com.google.android.libraries.motionphotoreader.Constants.OPEN_ARR;

/**
 * The XmpParser class is a package-private class intended to help extract the microvideo offset
 * information from the XMP metadata of the given Motion Photo.
 */
class XmpParser {

    /**
     * Returns the metadata of the Motion Photo file.
     * @param filename a string containing the path of the motion photo file to extract.
     * @return an XMPMeta object containing the xmp metadata of the file.
     * @throws IOException if an error occurs while trying to read the file.
     * @throws XMPException if invalid XMP syntax is parsed.
     */
    @Nullable
    public static XMPMeta getXmpMetadata(String filename) throws IOException, XMPException {
        return getXmpMetadata(new File(filename));
    }

    @Nullable
    public static XMPMeta getXmpMetadata(File file) throws IOException, XMPException {
        byte[] segArr = getXmpByteArray(file);
        return XMPMetaFactory.parseFromBuffer(segArr);
    }

    /**
     * Returns the byte array containing the xmp metadata.
     * @param file a file containing the path of the motion photo file to extract.
     * @return a byte array containing the information of the xmp metadata for the file.
     * @throws IOException if an error occurs while trying to read the file.
     */
    @VisibleForTesting
    static byte[] getXmpByteArray(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteStreams.copy(in, out);

            byte[] fileData = out.toByteArray();

            int openIdx = Bytes.indexOf(fileData, OPEN_ARR);
            if (openIdx >= 0) {
                int closeIdx = Bytes.indexOf(Arrays.copyOfRange(fileData, openIdx, fileData.length),
                        CLOSE_ARR) + openIdx + CLOSE_ARR.length;

                return Arrays.copyOfRange(fileData, openIdx, closeIdx);
            }
        }
        return new byte[0];
    }
}