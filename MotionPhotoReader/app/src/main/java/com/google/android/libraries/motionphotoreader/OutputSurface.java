package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.google.common.util.concurrent.SettableFuture;

import java.nio.IntBuffer;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "OutputSurface";
    private static final Object frameSyncObject = new Object();

    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private EGLConfig eglConfig;

    private int surfaceTextureHandle;
    private SurfaceTexture surfaceTexture;
    private Surface renderSurface;
    private TextureRender textureRender;
    private Handler renderHandler;
    private boolean frameAvailable;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public OutputSurface(Handler renderHandler, int width, int height) {
        this.renderHandler = renderHandler;
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid surface dimensions");
        }
        eglSetup(width, height);
        setup();
    }

    private void setup() {
        Log.d(TAG, "Setup output surface");
        renderHandler.post(() -> {
            textureRender = new TextureRender();
            textureRender.onSurfaceCreated();

            // Texture for motion photo outputs
            surfaceTextureHandle = textureRender.getTextureID();

            // After the motion photo texture has been created, the motion photo surface can be
            // initialized
            surfaceTexture = new SurfaceTexture(surfaceTextureHandle);
            renderSurface = new Surface(surfaceTexture);
            surfaceTexture.setOnFrameAvailableListener(this);

        });
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void eglSetup(int width, int height) {
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

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setSurface(Surface surface) {
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
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void release() {
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
            surfaceTexture.release();
            surfaceTexture = null;
            renderSurface.release();
            renderSurface = null;
        });
    }

    public Surface getRenderSurface() {
        SettableFuture<Surface> surfaceFuture = SettableFuture.create();
        renderHandler.post(() -> {
            surfaceFuture.set(renderSurface);
        });
        try {
            return surfaceFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Could not set surface texture", e);
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
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

            // Latch the data
            surfaceTexture.updateTexImage();
        });
    }

    public void drawImage() {
        renderHandler.post(() -> {
            textureRender.drawFrame(surfaceTexture);
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
