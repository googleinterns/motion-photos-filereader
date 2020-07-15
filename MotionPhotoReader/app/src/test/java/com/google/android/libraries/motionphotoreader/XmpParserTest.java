package com.google.android.libraries.motionphotoreader;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.google.common.primitives.Bytes;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class XmpParserTest {
    private static final byte[] OPEN_ARR = "<x:xmpmeta".getBytes(); /* Start of XMP metadata tag */
    private static final byte[] CLOSE_ARR = "</x:xmpmeta>".getBytes(); /* End of XMP metadata tag */

    private static final int XMP_BYTE_ARR_LENGTH_V1 = 430;
    private static final int XMP_BYTE_ARR_LENGTH_V2 = 1106;

    private String filenameV1; // v1 motion photo file
    private String filenameV2; // v2 motion photo file

    @Before
    public void setUp() {
        this.filenameV1 = this
                .getClass()
                .getClassLoader()
                .getResource("MVIMG_20200621_200240.jpg")
                .getFile();

        this.filenameV2 = this
                .getClass()
                .getClassLoader()
                .getResource("PXL_20200710_061629144.MP.jpg")
                .getFile();
    }

    @Test
    public void getXmpMetadata_stringV1_isNotNull() throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(filenameV1);
        assertNotNull(meta);
    }

    @Test
    public void getXmpMetadata_stringV2_isNotNull() throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(filenameV2);
        assertNotNull(meta);
    }

    @Test
    public void geXmpMetadata_fileV1_isNotNull() throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(new File(filenameV1));
        assertNotNull(meta);
    }

    @Test
    public void geXmpMetadata_fileV2_isNotNull() throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(new File(filenameV2));
        assertNotNull(meta);
    }

    private static boolean startsWith(byte[] array, byte[] prefix) {
        return Bytes.indexOf(array, prefix) == 0;
    }

    private static boolean endsWith(byte[] array, byte[] suffix, int byteArrLength) {
        int correctSuffixIndex = byteArrLength - CLOSE_ARR.length;
        return Bytes.indexOf(array, suffix) == correctSuffixIndex;
    }

    @Test
    public void getXmpByteArray_v1_hasCorrectHeader() throws IOException {
        byte[] segArr = XmpParser.getXmpByteArray(new File(filenameV1));
        assertTrue(startsWith(segArr, OPEN_ARR));
    }

    @Test
    public void getXmpByteArray_v2_hasCorrectHeader() throws IOException {
        byte[] segArr = XmpParser.getXmpByteArray(new File(filenameV2));
        assertTrue(startsWith(segArr, OPEN_ARR));
    }

    @Test
    public void getXmpByteArray_v1_hasCorrectFooter() throws IOException {
        byte[] segArr = XmpParser.getXmpByteArray(new File(filenameV1));
        assertTrue(endsWith(segArr, CLOSE_ARR, XMP_BYTE_ARR_LENGTH_V1));
    }

    @Test
    public void getXmpByteArray_v2_hasCorrectFooter() throws IOException {
        byte[] segArr = XmpParser.getXmpByteArray(new File(filenameV2));
        assertTrue(endsWith(segArr, CLOSE_ARR, XMP_BYTE_ARR_LENGTH_V2));
    }
}