package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Holds state associated with a Surface used for MediaCodec decoder input.
 *
 * Creates an EGL surface from a given surface (obtained in OutputSurface.setSurface()) and gets a
 * SurfaceTexture from a TextureRender object to hold frames from the decoder. The TextureRender
 * draws frames to the EGL surface.
 */

@RequiresApi(api = 23)
public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "OutputSurface";

    private final Object frameSyncObject = new Object();

    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private EGLConfig eglConfig;

    private int surfaceTextureHandle;
    private SurfaceTexture surfaceTexture;
    private SettableFuture<Surface> decodeSurfaceFuture = SettableFuture.create();
    private TextureRender textureRender;
    private Handler renderHandler;
    private boolean frameAvailable;
    private MotionPhotoInfo motionPhotoInfo;

    /**
     * Creates a new output surface.
     * @param renderHandler The handler thread on which all calls from this instance will run.
     * @param motionPhotoInfo The motion photo info associated with the video being played to this
     *                        output surface.
     */
    public OutputSurface(Handler renderHandler, MotionPhotoInfo motionPhotoInfo) {
        this.renderHandler = renderHandler;
        if (motionPhotoInfo.getWidth() <= 0 || motionPhotoInfo.getHeight() <= 0) {
            throw new IllegalArgumentException("Invalid surface dimensions");
        }
        this.motionPhotoInfo = motionPhotoInfo;
        eglSetup();
    }

    /**
     * Sets up the texture render object for preprocessing video frames before rendering to the
     * final output surface.
     * @param surfaceWidth The width of the surface on which the video is displayed, in pixels.
     * @param surfaceHeight The height of the surface on which the video is displayed, in pixels.
     */
    private void setupTextureRender(int surfaceWidth, int surfaceHeight) {
        Log.d(TAG, "Setup output surface");
        renderHandler.post(() -> {
            textureRender = new TextureRender();
            textureRender.setVideoWidth(motionPhotoInfo.getWidth());
            textureRender.setVideoHeight(motionPhotoInfo.getHeight());
            textureRender.setVideoRotation(motionPhotoInfo.getRotation());
            textureRender.onSurfaceCreated(surfaceWidth, surfaceHeight);

            // Get the texture for motion photo outputs
            surfaceTextureHandle = textureRender.getTextureID();

            // After the motion photo texture has been created, the motion photo surface can be
            // initialized
            surfaceTexture = new SurfaceTexture(surfaceTextureHandle);
            surfaceTexture.setOnFrameAvailableListener(this);
            Surface decodeSurface = new Surface(surfaceTexture);
            decodeSurfaceFuture.set(decodeSurface);
        });
    }

    /**
     * Configures and initializes the EGL context.
     */
    private void eglSetup() {
        renderHandler.post(() -> {
            // Initialize EGL display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("Unable to get EGL14 display");
            }

            // Initialize EGL
            int[] version = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                eglDisplay = null;
                throw new RuntimeException("Unable to initialize EGL14");
            }

            // Configure EGL
            int[] numConfigs = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] attributes = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_STENCIL_SIZE, 0,
                    EGL14.EGL_NONE
            };
            if (!EGL14.eglChooseConfig(
                    eglDisplay,
                    attributes, /* attrib_listOffset = */0,
                    configs, /* configsOffset = */0,
                    configs.length, numConfigs, /* num_configOffset = */ 0
            )) {
                throw new RuntimeException("eglChooseConfig failed");
            } else if (numConfigs[0] == 0) {
                throw new IllegalArgumentException("Could not find suitable EGLConfig");
            }
            eglConfig = configs[0];

            // Initialize EGL context
            eglContext = EGL14.eglCreateContext(
                    eglDisplay,
                    eglConfig,
                    EGL14.EGL_NO_CONTEXT,
                    new int[] {
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                            EGL14.EGL_NONE
                    },
                    /* offset = */ 0
            );

            // Make context current (with EGL_NO_SURFACE)
            eglSurface = EGL14.EGL_NO_SURFACE;
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                Log.e(TAG, "Failed to make context current");
            }
        });
    }

    /**
     * Associate a Surface object with this OutputSurface on which the final video will be
     * displayed. This should be called immediately after the instance is constructed.
     * @param surface The Surface object on which the final video will be displayed.
     * @param surfaceWidth The width of the Surface object, in pixels.
     * @param surfaceHeight The height of the Surface object, in pixels.
     */
    public void setSurface(Surface surface, int surfaceWidth, int surfaceHeight) {
        // Set up the texture render
        setupTextureRender(surfaceWidth, surfaceHeight);

        renderHandler.post(() -> {
            // Abort if context is null
            if (eglContext == null) {
                Log.i(TAG, "EGL Context is null, can't set EGL surface");
                return;
            }

            // Destroy EGL surface if valid
            if (!Objects.equals(eglSurface, EGL14.EGL_NO_SURFACE)) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
            }

            // Initialize EGL surface
            if (surface == null) {
                Log.d(TAG, "EGL initialized to no surface");
                eglSurface = EGL14.EGL_NO_SURFACE;
            } else {
                Log.d(TAG, "Creating EGL surface");
                eglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay,
                        eglConfig,
                        surface,
                        new int[] { EGL14.EGL_NONE },
                        /* offset = */ 0
                );
            }

            // Make EGL surface current
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                Log.e(TAG, "Failed to make context current");
            }
        });
    }

    /**
     * Free up all resources associated with this OutputSurface.
     */
    public void release() {
        Log.d(TAG, "Releasing output surface");
        renderHandler.post(() -> {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
                EGL14.eglDestroyContext(eglDisplay, eglContext);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(eglDisplay);
            }

            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglContext = EGL14.EGL_NO_CONTEXT;
            eglSurface = EGL14.EGL_NO_SURFACE;

            textureRender = null;
            if (surfaceTexture != null) {
                surfaceTexture.release();
                surfaceTexture = null;
            }
            try {
                decodeSurfaceFuture.get().release();
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Could not get decode surface");
            }
            decodeSurfaceFuture.set(null);
        });
    }

    /**
     * Get the Surface object which decoded video frames are rendered to before processing. Note
     * that this Surface does not display the final rendered video, but instead holds the
     * SurfaceTexture that supply textures to the TextureRender.
     * @return the intermediate decoding Surface.
     */
    public Surface getDecodeSurface() {
        try {
            return decodeSurfaceFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Could not set surface texture", e);
            return null;
        }
    }

    /**
     * Wait for a new frame to be decoded to the decode Surface.
     */
    public void awaitNewImage() {
        renderHandler.post(() -> {
            final int TIMEOUT_MS = 500;

            synchronized(frameSyncObject) {
                while (!frameAvailable) {
                    try {
                        frameSyncObject.wait(TIMEOUT_MS);
                        if (!frameAvailable) {
                            throw new RuntimeException("Surface frame wait timed out");
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Failed to wait for available frame");
                    }
                }
                frameAvailable = false;
            }
            surfaceTexture.updateTexImage();
        });
    }

    /**
     * Draw the image to the final display Surface.
     */
    public void drawImage(List<HomographyMatrix> homographyList, long renderTimestampNs) {
        renderHandler.post(() -> {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, renderTimestampNs);
            textureRender.drawFrame(homographyList);
            EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        });
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "New frame available");
        synchronized (frameSyncObject) {
            if (frameAvailable) {
                throw new RuntimeException("Available frame already set, frame could be dropped");
            }
            frameAvailable = true;
            frameSyncObject.notifyAll();
        }
    }
}
