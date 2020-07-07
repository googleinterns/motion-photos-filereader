package com.google.android.libraries.motionphotoreader;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.IOException;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Local unit test for MotionPhotoInfo class.
 */
public class MotionPhotoInfoTest {
    // define a mock media format
    private final static int KEY_WIDTH = 4032;
    private final static int KEY_HEIGHT = 3024;
    private final static long KEY_DURATION = 297168;
    private final static int KEY_ROTATION = 90;
    private final static String KEY_MIME = "video/avc";

    private final static int VIDEO_OFFSET = 2648203;
    private final static int PRESENTATION_TIMESTAMP_US = 0;

    private XMPMeta meta;
    private MediaExtractor extractor;
    private MediaFormat videoFormat;
    private MotionPhotoInfo motionPhotoInfo;
    private String filename;

    @Before
    public void setUp() throws IOException, XMPException {
        this.filename = this.getClass().getClassLoader().getResource("test_photo.jpg").getFile();
        meta = XmpParser.getXmpMetadata(filename);
        assertNotNull(meta);

        // set up a media format to mimic a motion photo
        videoFormat = mock(MediaFormat.class);
        doAnswer((Answer<Integer>) invocation -> KEY_WIDTH).when(videoFormat).getInteger(eq(MediaFormat.KEY_WIDTH));
        doAnswer((Answer<Integer>) invocation -> KEY_HEIGHT).when(videoFormat).getInteger(eq(MediaFormat.KEY_HEIGHT));
        doAnswer((Answer<Long>) invocation -> KEY_DURATION).when(videoFormat).getLong(eq(MediaFormat.KEY_DURATION));
        doAnswer((Answer<Integer>) invocation -> KEY_ROTATION).when(videoFormat).getInteger(eq(MediaFormat.KEY_ROTATION));
        doAnswer((Answer<String>) invocation -> KEY_MIME).when(videoFormat).getString(eq(MediaFormat.KEY_MIME));

        // return a single video track
        extractor = mock(MediaExtractor.class);
        when (extractor.getTrackCount()).thenReturn(1);
        doReturn(videoFormat).when(extractor).getTrackFormat(eq(0));

        motionPhotoInfo = MotionPhotoInfo.newInstance(filename, extractor);
        assertNotNull(motionPhotoInfo);
        verify(videoFormat, times(2)).getInteger(anyString());
        verify(videoFormat, times(1)).getLong(anyString());
        verify(videoFormat, times(1)).getString(anyString());
    }

    @Test
    public void getVideoOffset_isCorrect() {
        assertEquals(VIDEO_OFFSET, motionPhotoInfo.getVideoOffset());
    }

    @Test
    public void getPresentationTimestampUs_isCorrect() {
        assertEquals(PRESENTATION_TIMESTAMP_US, motionPhotoInfo.getPresentationTimestampUs());
    }

    @Test
    public void getWidth_isCorrect() {
        assertEquals(KEY_WIDTH, motionPhotoInfo.getWidth());
    }

    @Test
    public void getHeight_isCorrect() {
        assertEquals(KEY_HEIGHT, motionPhotoInfo.getHeight());
    }

    @Test
    public void getDuration_isCorrect() {
        assertEquals(KEY_DURATION, motionPhotoInfo.getDuration());
    }

    @Test
    public void getRotation_whenKeyExists_isCorrect() throws IOException, XMPException {
        // set up the media format so that it has a rotation
        doReturn(true).when(videoFormat).containsKey(eq(MediaFormat.KEY_ROTATION));
        motionPhotoInfo = MotionPhotoInfo.newInstance(filename, extractor);
        assertEquals(KEY_ROTATION, motionPhotoInfo.getRotation());
    }

    @Test
    public void getRotation_whenKeyDoesNotExist_isCorrect() {
        assertEquals(0, motionPhotoInfo.getRotation());
    }
}