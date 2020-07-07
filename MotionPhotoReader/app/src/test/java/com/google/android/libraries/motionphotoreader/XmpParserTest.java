package com.google.android.libraries.motionphotoreader;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.google.common.primitives.Bytes;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class XmpParserTest {
    private static final byte[] OPEN_ARR = "<x:xmpmeta".getBytes();  /* Start of XMP metadata tag */
    private static final byte[] CLOSE_ARR = "</x:xmpmeta>".getBytes();  /* End of XMP metadata tag */

    private static final int XMP_BYTE_ARR_LENGTH = 425;

    private String filename;

    @Before
    public void setUp() {
        this.filename = this.getClass().getClassLoader().getResource("test_photo.jpg").getFile();
    }

    @Test
    public void getXmpMetadata_isNotNull() throws IOException, XMPException {
        XMPMeta meta = XmpParser.getXmpMetadata(filename);
        assertNotNull(meta);
    }

    @Test
    public void getXmpByteArray_hasCorrectHeader() throws IOException {
        byte[] segArr = XmpParser.getXmpByteArray(filename);
        int index = Bytes.indexOf(segArr, OPEN_ARR);
        assertEquals(0, index);
    }

    @Test
    public void getXmpByteArray_hasCorrectFooter() throws IOException {
        byte[] segArr = XmpParser.getXmpByteArray(filename);
        int length = Bytes.indexOf(segArr, CLOSE_ARR) + CLOSE_ARR.length;
        assertEquals(XMP_BYTE_ARR_LENGTH, length);
    }
}