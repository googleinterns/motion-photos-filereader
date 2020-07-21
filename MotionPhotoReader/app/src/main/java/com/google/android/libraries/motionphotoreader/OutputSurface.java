package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.nio.IntBuffer;
import java.util.Objects;

public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "OutputSurface";
    private static final Object frameSyncObject = new Object();

    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private EGLConfig eglConfig;

    private int surfaceTextureHandle;
    private SurfaceTexture surfaceTexture;
    private TextureRender textureRender;
    private Surface surface;
    private boolean frameAvailable;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public OutputSurface(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid surface dimensions");
        }

        setup();
        eglSetup(width, height);
    }

    private void setup() {
        textureRender = new TextureRender();
        textureRender.initializeStatePostSurfaceCreated();
        createAndBindMotionPhotoTexture();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void eglSetup(int width, int height) {
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
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        if (!EGL14.eglChooseConfig(
                eglDisplay,
                attributes, /* attrib_listOffset = */0,
                configs, /* configsOffset = */0,
                configs.length, numConfigs, /* num_configOffset = */ 0
        )) {
            throw new RuntimeException("Unable to find RGB888+recordable ES2 EGL config");
        } else if (numConfigs[0] == 0) {
            throw new IllegalArgumentException("Could not find suitable EGLConfig");
        }
        eglConfig = configs[0];

        // Initialize EGL
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
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setSurface(Surface surface) {
        this.surface = surface;

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
            eglSurface = EGL14.EGL_NO_SURFACE;
        } else {
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
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void release() {
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
        surfaceTexture.release();
        surfaceTexture = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Failed to make EGL context and surface current");
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    private void createAndBindMotionPhotoTexture() {
        IntBuffer frameBufferIds = IntBuffer.allocate(1);
        GLES30.glGenFramebuffers(1, frameBufferIds);

        IntBuffer textureIds = IntBuffer.allocate(1);
        GLES30.glGenTextures(1, textureIds);

        // Texture for motion photo outputs
        surfaceTextureHandle = textureIds.get(0);

        // After the motion photo texture has been created, the motion photo surface can be
        // initialized
        surfaceTexture = new SurfaceTexture(surfaceTextureHandle);
    }

    public void awaitNewImage() {
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

        // Latch the data
        textureRender.checkGlError("before updateTexImage");
        surfaceTexture.updateTexImage();
    }

    public void drawImage() {
        textureRender.drawFrame(surfaceTexture);
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
