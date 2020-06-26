package com.example.motionphotoreader;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Example local unit test, which will execute on the development machine (host).
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class MotionPhotoInfoUnitTest {
    private final static int KEY_WIDTH = 4032;
    private final static int KEY_HEIGHT = 3024;
    private final static long KEY_DURATION = 297168;
    private final static String KEY_MIME = "video/avc";

    private final static int VIDEO_OFFSET = 2648203;
    private final static int PRESENTATION_TIMESTAMP_US = 0;

    private XMPMeta meta;
    private MediaExtractor extractor;
    private MotionPhotoInfo mpi;
    private String filename;

    @Before
    public void setUp() throws IOException, XMPException {
        this.filename = this.getClass().getClassLoader().getResource("test_photo.jpg").getFile();
        meta = XmpParser.getXmpMetadata(filename);

        // set up a media format to mimic a motion photo
        MediaFormat videoFormat = mock(MediaFormat.class);
        doAnswer((Answer<Integer>) invocation -> KEY_WIDTH).when(videoFormat).getInteger(eq(MediaFormat.KEY_WIDTH));
        doAnswer((Answer<Integer>) invocation -> KEY_HEIGHT).when(videoFormat).getInteger(eq(MediaFormat.KEY_HEIGHT));
        doAnswer((Answer<Long>) invocation -> KEY_DURATION).when(videoFormat).getLong(eq(MediaFormat.KEY_DURATION));
        doAnswer((Answer<String>) invocation -> KEY_MIME).when(videoFormat).getString(eq(MediaFormat.KEY_MIME));

        // return a single video track
        extractor = mock(MediaExtractor.class);
        when (extractor.getTrackCount()).thenReturn(1);
        doAnswer((Answer<MediaFormat>) invocation -> videoFormat).when(extractor).getTrackFormat(eq(0));

        mpi = new MotionPhotoInfo(filename, extractor);
    }

    @Test
    public void metadata_isValid() {
        assertNotNull(meta);
    }

    @Test
    public void motionPhotoInfo_isValid() {
        assertNotNull(mpi);
    }

    @Test
    public void getVideoOffset_isCorrect() {
        int videoOffset = mpi.getVideoOffset();
        assertEquals(VIDEO_OFFSET, videoOffset);
    }

    @Test
    public void getPresentationTimestampUs_isCorrect() {
        long presentationTimestampUs = mpi.getPresentationTimestampUs();
        assertEquals(PRESENTATION_TIMESTAMP_US, presentationTimestampUs);
    }

    @Test
    public void getWidth_isCorrect() {
        assertEquals(mpi.getWidth(), KEY_WIDTH);
    }

    @Test
    public void getHeight_isCorrect() {
        assertEquals(mpi.getHeight(), KEY_HEIGHT);
    }

    @Test
    public void getDuration_isCorrect() {
        assertEquals(mpi.getDuration(), KEY_DURATION);
    }
}