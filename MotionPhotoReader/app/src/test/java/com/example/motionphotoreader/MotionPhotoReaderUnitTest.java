package com.example.motionphotoreader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.view.Surface;

import com.adobe.internal.xmp.XMPException;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(sdk = 28)
@RunWith(RobolectricTestRunner.class)
public class MotionPhotoReaderUnitTest {

    private final static int KEY_WIDTH = 4032;
    private final static int KEY_HEIGHT = 3024;
    private final static long KEY_DURATION = 297168;
    private final static String KEY_MIME = "video/avc";

    private static final int MSG_NEXT_FRAME = 0x0001;
    private static final int MSG_SEEK_TO_FRAME = 0x0010;
    private static final long TIMEOUT_US = 1000L;
    private static final long US_TO_NS = 1000L;

    private String filename;
    private Surface surface;

    private MotionPhotoReader reader;
    private MediaExtractor lowResExtractor;
    private MediaCodec lowResDecoder;
    private MediaFormat videoFormat;
    private MotionPhotoInfo mpi;
    private MediaCodec.Callback callback;
    private HandlerThread mBufferWorker;
    private Handler bufferHandler;

    private final BlockingQueue<Integer> availableInputBuffers = new LinkedBlockingQueue<>();
    private final BlockingQueue<Bundle> availableOutputBuffers = new LinkedBlockingQueue<>();
    private final Queue<Message> messageQueue = new ArrayDeque<>();

    @Before
    public void setUp() throws IOException, XMPException {
        this.filename = this.getClass().getClassLoader().getResource("test_photo.jpg").getFile();

        // set up a surface to mimic the actual surface view
        surface = mock(Surface.class);

        setUpMPI();
        setUpThreads();
        setUpMessages();
    }

    private void setUpMPI() throws IOException, XMPException {
        // set up a media format to mimic a motion photo
        videoFormat = mock(MediaFormat.class);
        doAnswer((Answer<Integer>) invocation -> KEY_WIDTH).when(videoFormat).getInteger(eq(MediaFormat.KEY_WIDTH));
        doAnswer((Answer<Integer>) invocation -> KEY_HEIGHT).when(videoFormat).getInteger(eq(MediaFormat.KEY_HEIGHT));
        doAnswer((Answer<Long>) invocation -> KEY_DURATION).when(videoFormat).getLong(eq(MediaFormat.KEY_DURATION));
        doAnswer((Answer<String>) invocation -> KEY_MIME).when(videoFormat).getString(eq(MediaFormat.KEY_MIME));

        // return a single video track
        lowResExtractor = mock(MediaExtractor.class);
        when (lowResExtractor.getTrackCount()).thenReturn(1);
        doAnswer((Answer<MediaFormat>) invocation -> videoFormat).when(lowResExtractor).getTrackFormat(eq(0));

        // Get mimicked MotionPhotoInfo
        mpi = MotionPhotoInfo.newInstance(filename, lowResExtractor);
    }

    private void setUpThreads() throws IOException, XMPException {
        // set up decoder for reader
        lowResDecoder = mock(MediaCodec.class);
        mBufferWorker = mock(HandlerThread.class);
        doReturn(mock(Looper.class)).when(mBufferWorker).getLooper();
        bufferHandler = spy(new Handler(mBufferWorker.getLooper()));
        callback = mock(MediaCodec.Callback.class);

        reader = spy(new MotionPhotoReader(filename, surface, lowResExtractor, lowResDecoder, mBufferWorker, bufferHandler, callback));
        doReturn(mpi).when(reader).getMotionPhotoInfo();

        // set up media thread
        ArgumentCaptor<MediaCodec.Callback> acMediaCodecCallback = ArgumentCaptor.forClass(MediaCodec.Callback.class);
        verify(lowResDecoder).setCallback(acMediaCodecCallback.capture(), any(Handler.class));

        doAnswer((Answer<Void>) invocation -> {
            MediaCodec codec = invocation.getArgument(0);
            int index = invocation.getArgument(1);
            boolean result = availableInputBuffers.offer(index);
            return null;
        }).when(callback).onInputBufferAvailable(any(MediaCodec.class), any(Integer.class));

        doAnswer((Answer<Void>) invocation -> {
            MediaCodec codec = invocation.getArgument(0);
            int index = invocation.getArgument(1);
            MediaCodec.BufferInfo info = invocation.getArgument(2);

            Bundle bufferData = new Bundle();
            bufferData.putInt("BUFFER_INDEX", index);
            bufferData.putLong("TIMESTAMP_US", info.presentationTimeUs);
            boolean result = availableOutputBuffers.offer(bufferData);

            return null;
        }).when(callback).onOutputBufferAvailable(any(MediaCodec.class), any(Integer.class), any(MediaCodec.BufferInfo.class));

        // set up buffer thread
        doAnswer((Answer<Void>) invocation -> {
            Message inputMessage = invocation.getArgument(0);
            Bundle messageData = inputMessage.getData();
            int key = messageData.getInt("MESSAGE_KEY");
            Bundle bufferData;
            int bufferIndex;
            long timestamp;
            switch (key) {
                case MSG_NEXT_FRAME:
                    bufferIndex = availableInputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
                    ByteBuffer inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                    int sampleSize = lowResExtractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        lowResDecoder.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                    else {
                        lowResDecoder.queueInputBuffer(bufferIndex, 0, sampleSize, lowResExtractor.getSampleTime(), 0);
                        lowResExtractor.advance();
                    }
                    bufferData = availableOutputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
                    timestamp = bufferData.getLong("TIMESTAMP_US");
                    bufferIndex = bufferData.getInt("BUFFER_INDEX");
                    lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp * US_TO_NS);
                    break;
                case MSG_SEEK_TO_FRAME:
                    bufferIndex = availableInputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
                    inputBuffer = lowResDecoder.getInputBuffer(bufferIndex);
                    lowResExtractor.seekTo(messageData.getLong("TIME_US"), messageData.getInt("MODE"));
                    sampleSize = lowResExtractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        lowResDecoder.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                    else {
                        lowResDecoder.queueInputBuffer(bufferIndex, 0, sampleSize, lowResExtractor.getSampleTime(), 0);
                        lowResExtractor.advance();
                    }
                    bufferData = availableOutputBuffers.poll(TIMEOUT_US, TimeUnit.MILLISECONDS);
                    timestamp = bufferData.getLong("TIMESTAMP_US");
                    bufferIndex = bufferData.getInt("BUFFER_INDEX");
                    lowResDecoder.releaseOutputBuffer(bufferIndex, timestamp * US_TO_NS);
                    break;
                default:
                    doThrow(mock(IllegalStateException.class));
                    break;
            }
            return null;
        }).when(bufferHandler).handleMessage(any(Message.class));
    }

    private void setUpMessages() {
        doAnswer((Answer<Void>) invocation -> {
            Message message = Message.obtain(bufferHandler);

            Bundle messageData = new Bundle();
            messageData.putInt("MESSAGE_KEY", MSG_NEXT_FRAME);
            message.setData(messageData);

            messageQueue.offer(message);
            message.sendToTarget();
            return null;
        }).when(reader).nextFrame();

        doAnswer((Answer<Void>) invocation -> {
            Long timeUs = invocation.getArgument(0);
            int mode = invocation.getArgument(1);

            Message message = Message.obtain(bufferHandler);

            Bundle messageData = new Bundle();
            messageData.putInt("MESSAGE_KEY", MSG_SEEK_TO_FRAME);
            messageData.putLong("TIME_US", timeUs);
            messageData.putInt("MODE", mode);
            message.setData(messageData);

            messageQueue.offer(message);
            message.sendToTarget();
            return null;
        }).when(reader).seekTo(any(Long.class), anyInt());

        doAnswer((Answer<SettableFuture<Boolean>>) invocation -> {
            SettableFuture<Boolean> result = SettableFuture.create();

            // Send hasNextFrame task to handler
            bufferHandler.post(() -> {

                // Read the next packet and check if it shows a full frame
                long sampleSize = lowResExtractor.getSampleSize();
                if (sampleSize < 0) {
                    result.set(false);
                }
                else {
                    result.set(true);
                }
            });
            return result;
        }).when(reader).hasNextFrame();

        doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message.getCallback() != null) {
                message.getCallback().run();
            }
            else {
                bufferHandler.handleMessage(message);
            }
            return true;
        }).when(bufferHandler).sendMessageAtTime(any(Message.class), any(Long.class));
    }

    @Test
    public void sendAvailableInputBuffer_isEmpty() {
        boolean flag = (availableInputBuffers.size() == 0);
        assertTrue("Input buffer queue is not empty", flag);
    }

    @Test
    public void sendAvailableOutputBuffer_isEmpty() {
        boolean flag = (availableOutputBuffers.size() == 0);
        assertTrue("Output buffer queue is not empty", flag);
    }

    @Test
    public void sendAvailableInputBuffer_isNotEmpty() {
        // give fake available input buffers
        callback.onInputBufferAvailable(lowResDecoder, 0);
        callback.onInputBufferAvailable(lowResDecoder, 1);
        callback.onInputBufferAvailable(lowResDecoder, 2);

        boolean flag = (availableInputBuffers.size() > 0);
        assertTrue("Input buffer queue is empty", flag);
    }

    @Test
    public void sendAvailableOutputBuffer_isNotEmpty() {
        // give fake available output buffers
        callback.onOutputBufferAvailable(lowResDecoder, 0, new MediaCodec.BufferInfo());
        callback.onOutputBufferAvailable(lowResDecoder, 1, new MediaCodec.BufferInfo());
        callback.onOutputBufferAvailable(lowResDecoder, 2, new MediaCodec.BufferInfo());

        boolean flag = (availableOutputBuffers.size() > 0);
        assertTrue("Output buffer queue is empty", flag);
    }

    @Test
    public void sendNextFrameMessage_hasCorrectKey() {
        callback.onInputBufferAvailable(lowResDecoder, 0);
        callback.onOutputBufferAvailable(lowResDecoder, 0, new MediaCodec.BufferInfo());
        reader.nextFrame();
        Message message = messageQueue.poll();
        int key = message.getData().getInt("MESSAGE_KEY");
        assertEquals(MSG_NEXT_FRAME, key);
    }

    @Test
    public void sendSeekToMessage_hasCorrectKey() {
        callback.onInputBufferAvailable(lowResDecoder, 0);
        callback.onOutputBufferAvailable(lowResDecoder, 0, new MediaCodec.BufferInfo());
        reader.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        Message message = messageQueue.poll();
        int key = message.getData().getInt("MESSAGE_KEY");
        assertEquals(MSG_SEEK_TO_FRAME, key);
    }

    @Test
    public void hasNextFrame_onFirstFrame_returnsTrue() throws InterruptedException, ExecutionException, TimeoutException {
        callback.onInputBufferAvailable(lowResDecoder, 0);
        callback.onOutputBufferAvailable(lowResDecoder, 0, new MediaCodec.BufferInfo());
        boolean flag = reader.hasNextFrame().get(TIMEOUT_US, TimeUnit.MICROSECONDS);
        assertTrue("No next frame found", flag);
    }

    @Test

    public void hasNextFrame_onLastFrame_returnsFalse() throws InterruptedException, ExecutionException, TimeoutException {
        callback.onInputBufferAvailable(lowResDecoder, 0);
        callback.onOutputBufferAvailable(lowResDecoder, 0, new MediaCodec.BufferInfo());
        reader.seekTo(mpi.getDuration(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        boolean flag = reader.hasNextFrame().get(TIMEOUT_US, TimeUnit.MICROSECONDS);
        assertTrue("Returned true on last frame", flag);
    }
}
