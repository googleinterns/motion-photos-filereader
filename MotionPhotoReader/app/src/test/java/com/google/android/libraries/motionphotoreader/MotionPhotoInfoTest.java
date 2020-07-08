package com.google.android.libraries.motionphotoreader;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
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
    private MotionPhotoInfo motionPhotoInfoString;
    private MotionPhotoInfo motionPhotoInfoFile;
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

        motionPhotoInfoString = MotionPhotoInfo.newInstance(filename, extractor);
        assertNotNull(motionPhotoInfoString);
        motionPhotoInfoFile = MotionPhotoInfo.newInstance(new File(filename), extractor);
        assertNotNull(motionPhotoInfoFile);
        verify(videoFormat, times(4)).getInteger(anyString());
        verify(videoFormat, times(2)).getLong(anyString());
        verify(videoFormat, times(2)).getString(anyString());
    }

    @Test
    public void getVideoOffset_isCorrect() {
        assertEquals(VIDEO_OFFSET, motionPhotoInfoString.getVideoOffset());
        assertEquals(VIDEO_OFFSET, motionPhotoInfoFile.getVideoOffset());
    }

    @Test
    public void getPresentationTimestampUs_isCorrect() {
        assertEquals(PRESENTATION_TIMESTAMP_US, motionPhotoInfoString.getPresentationTimestampUs());
        assertEquals(PRESENTATION_TIMESTAMP_US, motionPhotoInfoFile.getPresentationTimestampUs());
    }

    @Test
    public void getWidth_isCorrect() {
        assertEquals(KEY_WIDTH, motionPhotoInfoString.getWidth());
        assertEquals(KEY_WIDTH, motionPhotoInfoFile.getWidth());
    }

    @Test
    public void getHeight_isCorrect() {
        assertEquals(KEY_HEIGHT, motionPhotoInfoString.getHeight());
        assertEquals(KEY_HEIGHT, motionPhotoInfoFile.getHeight());
    }

    @Test
    public void getDuration_isCorrect() {
        assertEquals(KEY_DURATION, motionPhotoInfoString.getDuration());
        assertEquals(KEY_DURATION, motionPhotoInfoFile.getDuration());
    }

    @Test
    public void getRotation_whenKeyExists_isCorrect() throws IOException, XMPException {
        // set up the media format so that it has a rotation
        doReturn(true).when(videoFormat).containsKey(eq(MediaFormat.KEY_ROTATION));
        motionPhotoInfoString = MotionPhotoInfo.newInstance(filename, extractor);
        motionPhotoInfoFile = MotionPhotoInfo.newInstance(new File(filename), extractor);
        assertEquals(KEY_ROTATION, motionPhotoInfoString.getRotation());
        assertEquals(KEY_ROTATION, motionPhotoInfoFile.getRotation());
    }

    @Test
    public void getRotation_whenKeyDoesNotExist_isCorrect() {
        assertEquals(0, motionPhotoInfoString.getRotation());
        assertEquals(0, motionPhotoInfoFile.getRotation());
    }
}