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

import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V1;
import static com.google.android.libraries.motionphotoreader.Constants.MOTION_PHOTO_V2;
import static com.google.android.libraries.motionphotoreader.TestConstants.FILENAME_V1;
import static com.google.android.libraries.motionphotoreader.TestConstants.FILENAME_V2;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_DURATION_V1;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_DURATION_V2;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_HEIGHT;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_MIME;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_ROTATION_V1;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_ROTATION_V2;
import static com.google.android.libraries.motionphotoreader.TestConstants.KEY_WIDTH;
import static com.google.android.libraries.motionphotoreader.TestConstants.VIDEO_OFFSET_V1;
import static com.google.android.libraries.motionphotoreader.TestConstants.VIDEO_OFFSET_V2;
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

    private XMPMeta metaV1;
    private XMPMeta metaV2;
    private MediaFormat videoFormatV1;
    private MediaFormat videoFormatV2;
    private MotionPhotoInfo motionPhotoInfoV1;
    private MotionPhotoInfo motionPhotoInfoV2;

    @Before
    public void setUp() throws IOException, XMPException {
        // set up v1 motion photo metadata
        File fileV1 = ResourceFetcher.fetchResourceFile(getClass().getClassLoader(), FILENAME_V1);
        metaV1 = XmpParser.getXmpMetadata(fileV1);
        assertNotNull(metaV1);

        // set up a media format to mimic a v1 motion photo format, and create a corresponding
        // media extractor
        videoFormatV1 = createFakeVideoFormat(
                KEY_WIDTH,
                KEY_HEIGHT,
                KEY_DURATION_V1,
                KEY_ROTATION_V1,
                KEY_MIME
        );

        // create motion photo info objects from a string filename and a file object
        motionPhotoInfoV1 = new MotionPhotoInfo(videoFormatV1, VIDEO_OFFSET_V1, MOTION_PHOTO_V1);
        assertNotNull(motionPhotoInfoV1);
        verify(videoFormatV1, times(2)).getInteger(anyString());
        verify(videoFormatV1, times(1)).getLong(anyString());
        verify(videoFormatV1, times(1)).containsKey(eq("rotation-degrees"));

        // set up v2 motion photo metadata
        File fileV2 = ResourceFetcher.fetchResourceFile(getClass().getClassLoader(), FILENAME_V2);
        metaV2 = XmpParser.getXmpMetadata(fileV2);
        assertNotNull(metaV2);

        // set up a media format to mimic a v2 motion photo format, and create a corresponding
        // media extractor
        videoFormatV2 = createFakeVideoFormat(
                KEY_WIDTH,
                KEY_HEIGHT,
                KEY_DURATION_V2,
                KEY_ROTATION_V2,
                KEY_MIME
        );

        // create motion photo info object
        motionPhotoInfoV2 = new MotionPhotoInfo(videoFormatV2, VIDEO_OFFSET_V2, MOTION_PHOTO_V2);
        assertNotNull(motionPhotoInfoV2);
        verify(videoFormatV2, times(2)).getInteger(anyString());
        verify(videoFormatV2, times(1)).getLong(anyString());
        verify(videoFormatV2, times(1)).containsKey(eq("rotation-degrees"));
    }

    /**
     * Creates a mock media format object. Used to represent video formats of motion photo files.
     * @param width The width of the motion photo video, in pixels.
     * @param height The height of the motion photo video, in pixels.
     * @param duration The duration of the motion photo video, in microseconds.
     * @param rotation The rotation applied to the motion photo, in degrees.
     * @param mime The mime type of the motion photo video track.
     * @return a mock MediaFormat object.
     */
    private MediaFormat createFakeVideoFormat(int width,
                                              int height,
                                              long duration,
                                              int rotation,
                                              String mime) {
        MediaFormat videoFormat = mock(MediaFormat.class);
        doAnswer((Answer<Integer>) invocation -> width)
                .when(videoFormat)
                .getInteger(eq(MediaFormat.KEY_WIDTH));
        doAnswer((Answer<Integer>) invocation -> height)
                .when(videoFormat)
                .getInteger(eq(MediaFormat.KEY_HEIGHT));
        doAnswer((Answer<Long>) invocation -> duration)
                .when(videoFormat)
                .getLong(eq(MediaFormat.KEY_DURATION));
        doAnswer((Answer<Integer>) invocation -> rotation)
                .when(videoFormat)
                .getInteger(eq(MediaFormat.KEY_ROTATION));
        doAnswer((Answer<String>) invocation -> mime)
                .when(videoFormat)
                .getString(eq(MediaFormat.KEY_MIME));

        return videoFormat;
    }

    /**
     * Creates a fake media extractor for a fake video format.
     * @param videoFormat The media format of the video track that the extractor should find.
     * @return a mock MediaExtractor object.
     */
    private MediaExtractor createFakeMediaExtractor(MediaFormat videoFormat) {
        MediaExtractor extractor = mock(MediaExtractor.class);
        when (extractor.getTrackCount()).thenReturn(1);
        doReturn(videoFormat).when(extractor).getTrackFormat(eq(0));

        return extractor;
    }

    @Test
    public void getVideoOffset_v1_isCorrect() {
        assertEquals(VIDEO_OFFSET_V1, motionPhotoInfoV1.getVideoOffset());
    }

    @Test
    public void getVideoOffset_v2_isCorrect() {
        assertEquals(VIDEO_OFFSET_V2, motionPhotoInfoV2.getVideoOffset());
    }

    @Test
    public void getWidth_v1_isCorrect() {
        assertEquals(KEY_WIDTH, motionPhotoInfoV1.getWidth());
    }

    @Test
    public void getWidth_v2_isCorrect() {
        assertEquals(KEY_WIDTH, motionPhotoInfoV2.getWidth());
    }

    @Test
    public void getHeight_v1_isCorrect() {
        assertEquals(KEY_HEIGHT, motionPhotoInfoV1.getHeight());
    }

    @Test
    public void getHeight_v2_isCorrect() {
        assertEquals(KEY_HEIGHT, motionPhotoInfoV2.getHeight());
    }

    @Test
    public void getDuration_v1_isCorrect() {
        assertEquals(KEY_DURATION_V1, motionPhotoInfoV1.getDurationUs());
    }

    @Test
    public void getDuration_v2_isCorrect() {
        assertEquals(KEY_DURATION_V2, motionPhotoInfoV2.getDurationUs());
    }

    @Test
    public void getRotation_whenKeyExists_v1_isCorrect() {
        // set up the media format so that it has a rotation
        doReturn(true).when(videoFormatV1).containsKey(eq(MediaFormat.KEY_ROTATION));
        motionPhotoInfoV1 = new MotionPhotoInfo(videoFormatV1, VIDEO_OFFSET_V1, MOTION_PHOTO_V1);
        // Number of invocations includes those in setUp()
        verify(videoFormatV1, times(5)).getInteger(anyString());
        verify(videoFormatV1, times(2)).getLong(anyString());
        assertEquals(KEY_ROTATION_V1, motionPhotoInfoV1.getRotation());
    }

    @Test
    public void getRotation_whenKeyExists_v2_isCorrect() {
        // set up the media format so that it has a rotation
        doReturn(true).when(videoFormatV2).containsKey(eq(MediaFormat.KEY_ROTATION));
        motionPhotoInfoV2 = new MotionPhotoInfo(videoFormatV2, VIDEO_OFFSET_V2, MOTION_PHOTO_V2);
        // Number of invocations includes those in setUp()
        verify(videoFormatV2, times(5)).getInteger(anyString());
        verify(videoFormatV2, times(2)).getLong(anyString());
        assertEquals(KEY_ROTATION_V2, motionPhotoInfoV2.getRotation());
    }

    @Test
    public void getRotation_whenKeyDoesNotExist_v1_isCorrect() {
        assertEquals(0, motionPhotoInfoV1.getRotation());
    }

    @Test
    public void getRotation_whenKeyDoesNotExist_v2_isCorrect() {
        assertEquals(0, motionPhotoInfoV2.getRotation());
    }

    @Test
    public void getVersion_v1_isCorrect() {
        assertEquals(MOTION_PHOTO_V1, motionPhotoInfoV1.getVersion());
    }

    @Test
    public void getVersion_v2_isCorrect() {
        assertEquals(MOTION_PHOTO_V2, motionPhotoInfoV2.getVersion());
    }
}