package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES30.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES30.GL_COMPILE_STATUS;
import static android.opengl.GLES30.GL_FLOAT;
import static android.opengl.GLES30.GL_FRAGMENT_SHADER;
import static android.opengl.GLES30.GL_LINK_STATUS;
import static android.opengl.GLES30.GL_TEXTURE0;
import static android.opengl.GLES30.GL_TRIANGLE_STRIP;
import static android.opengl.GLES30.GL_VALIDATE_STATUS;
import static android.opengl.GLES30.GL_VERTEX_SHADER;
import static android.opengl.GLES30.glActiveTexture;
import static android.opengl.GLES30.glAttachShader;
import static android.opengl.GLES30.glBindTexture;
import static android.opengl.GLES30.glClear;
import static android.opengl.GLES30.glClearColor;
import static android.opengl.GLES30.glCompileShader;
import static android.opengl.GLES30.glCreateProgram;
import static android.opengl.GLES30.glCreateShader;
import static android.opengl.GLES30.glDeleteProgram;
import static android.opengl.GLES30.glDeleteShader;
import static android.opengl.GLES30.glDrawArrays;
import static android.opengl.GLES30.glEnableVertexAttribArray;
import static android.opengl.GLES30.glGenTextures;
import static android.opengl.GLES30.glGetAttribLocation;
import static android.opengl.GLES30.glGetError;
import static android.opengl.GLES30.glGetProgramInfoLog;
import static android.opengl.GLES30.glGetProgramiv;
import static android.opengl.GLES30.glGetShaderiv;
import static android.opengl.GLES30.glGetUniformLocation;
import static android.opengl.GLES30.glLinkProgram;
import static android.opengl.GLES30.glShaderSource;
import static android.opengl.GLES30.glUniform1i;
import static android.opengl.GLES30.glUniformMatrix4fv;
import static android.opengl.GLES30.glUseProgram;
import static android.opengl.GLES30.glValidateProgram;
import static android.opengl.GLES30.glVertexAttribPointer;
import static android.opengl.GLES30.glViewport;

/**
 * Renders frames from a MediaCodec decoder onto an EGL surface.
 *
 * Video frames are decoded onto a Surface wrapped around a Surface Texture attached to this GL
 * context. Stabilization is applied, and the texture is then rendered to a flat image plane
 * covering the viewport.
 */
class TextureRender {

    private static final String TAG = "TextureRender";

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 2 * FLOAT_SIZE_BYTES;

    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
            "uniform mat4 uMatrix;\n" +
            "in vec2 aPosition;\n" +
            "out vec2 TexCoord;\n" +
            "void main() {\n" +
            "  TexCoord = 0.5 * (vec2(1.0, 1.0) + aPosition);\n" +
            "  gl_Position = uMatrix * vec4(aPosition, 0.0, 1.0);\n" +
            "}";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTexUnit;\n" +
            "in vec2 TexCoord;\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "  FragColor = texture(uTexUnit, TexCoord);\n" +
            "}";

    // A single plane comprised of two triangles to hold the image texture
    private final float[] triangleVerticesData = {
            // positions (x,y)
            -1.0f, -1.0f,   // bottom left
             1.0f, -1.0f,   // bottom right
            -1.0f,  1.0f,   // top left
             1.0f,  1.0f    // top right
    };

    private float[] uMatrix = new float[16];

    private int textureID;
    private int program;
    private int aPositionHandle;
    private int uMatrixHandle;
    private int uTextureUnitHandle;

    private int videoWidth = 0;
    private int videoHeight = 0;
    private int videoRotation = 0;
    int surfaceWidth = 0;
    int surfaceHeight = 0;

    private FloatBuffer triangleVertices;

    /**
     * Create a TextureRender instance and allocate memory for image data.
     */
    public TextureRender() {
        triangleVertices = ByteBuffer
                .allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        triangleVertices.put(triangleVerticesData).position(/* newPosition = */ 0);
        Matrix.setIdentityM(uMatrix, /* smOffset = */ 0);
    }

    /**
     * Specify the original width of the video being rendered, in pixels. This should be called
     * after constructing an instance.
     */
    public void setVideoWidth(int videoWidth) {
        this.videoWidth = videoWidth;
    }

    /**
     * Specify the original width of the video being rendered, in pixels. This should be called
     * after constructing an instance.
     */
    public void setVideoHeight(int videoHeight) {
        this.videoHeight = videoHeight;
    }

    /**
     * Specify the camera orientation (as a rotation) of the video being rendered, in degrees.
     */
    public void setVideoRotation(int videoRotation) {
        this.videoRotation = videoRotation;
    }

    /**
     * Sets up the GL program to render the video frames. Should be called immediately after
     * constructing an instance of the TextureRender.
     * @param surfaceWidth The width of the display Surface that the GL viewport covers, in pixels.
     * @param surfaceHeight The height of the display Surface that the GL viewport covers, in pixels.
     */
    public void onSurfaceCreated(int surfaceWidth, int surfaceHeight) {
        Log.d(TAG, "Initializing state");
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = linkProgram(vertexShader, fragmentShader);

        // Validate the program
        glValidateProgram(program);
        final int[] validateStatus = new int[1];
        glGetProgramiv(program, GL_VALIDATE_STATUS, validateStatus, /* offset = */ 0);
        Log.v(TAG, "Program validation results: " + validateStatus[0]
                + "\nLog: " + glGetProgramInfoLog(program));

        // Set up viewport, and make sure to account for rotated video orientation
        // TODO: scale video to fit entire surface view, depending on app:fill in widget attributes
        setViewport();

        glUseProgram(program);

        // Create and bind textures
        int[] textureIds = new int[1];
        glGenTextures(/* n = */ 1, textureIds, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID);
        textureID = textureIds[0];

        if (glGetError() != 0) {
            throw new RuntimeException("Failed to set up textures");
        }

        // TODO: Switch to VBOs and VAOs
        aPositionHandle = glGetAttribLocation(program, "aPosition");
        glEnableVertexAttribArray(aPositionHandle);
        glVertexAttribPointer(
                aPositionHandle,
                /* size = */ 2,
                /* type = */ GL_FLOAT,
                /* normalized = */ false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        );
        if (glGetError() != 0) {
            throw new RuntimeException("Failed to get vertex position");
        }

        uMatrixHandle = glGetUniformLocation(program, "uMatrix");
        glUniformMatrix4fv(
                uMatrixHandle,
                /* count = */ 1,
                /* transpose = */ false,
                uMatrix,
                /* offset = */ 0
        );

        if (glGetError() != 0) {
            throw new RuntimeException("Failed to get matrix");
        }

        uTextureUnitHandle = glGetUniformLocation(program, "uTexUnit");
        glUniform1i(uTextureUnitHandle, /* x = */0);

        if (glGetError() != 0) {
            throw new RuntimeException("Failed to get texture unit");
        }
    }

    private void setViewport() {
        // Rotate video dimensions dimensions if necessary
        int newVideoWidth = videoWidth;
        int newVideoHeight = videoHeight;
        if (videoRotation > 0) {
            newVideoWidth = videoHeight;
            newVideoHeight = videoWidth;
        }

        // Create matrix to scale the video based on fill mode
        double aspectRatio = (float) newVideoWidth / newVideoHeight;
        int viewportWidth, viewportHeight;
        int translateOffsetX = 0;
        int translateOffsetY = 0;
        if (surfaceWidth / aspectRatio > surfaceHeight) {
            // Video is "narrower" than display surface (limited by height)
            viewportWidth = (int) (surfaceHeight * aspectRatio);
            viewportHeight = surfaceHeight;
            translateOffsetX = (surfaceWidth - viewportWidth) / 2;
        } else {
            // Video is "wider" than display surface (limited by width)
            viewportWidth = surfaceWidth;
            viewportHeight = (int) (surfaceWidth / aspectRatio);
            translateOffsetY = (surfaceHeight - viewportHeight) / 2;
        }

        glViewport(translateOffsetX, translateOffsetY, viewportWidth, viewportHeight);
        if (glGetError() != 0) {
            throw new RuntimeException("Failed to set up viewport");
        }

        // Set up rotation matrix if the camera orientation is greater than 0 degrees
        if (videoRotation > 0) {
            Log.d(TAG, "rotation: " + videoRotation);
            Matrix.rotateM(
                    uMatrix,
                    /* rmOffset = */ 0,
                    /* a = */ -videoRotation,  // Original video rotation is stored clockwise
                    /* x = */ 0,
                    /* y = */ 0,
                    /* z = */ 1
            );
        }
    }

    private static int linkProgram(int vertexShader, int fragmentShader) {
        Log.d(TAG, "Linking program");

        // Create the program
        final int program = glCreateProgram();
        if (program == 0) {
            Log.w(TAG, "Could not create new program");
            return 0;
        }

        // Attach shaders to program and link them together
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);

        // Verify linking status
        final int[] linkStatus = new int[1];
        glGetProgramiv(program, GL_LINK_STATUS, linkStatus, /* offset = */ 0);
        if (linkStatus[0] == 0) {
            // Delete the program if linking failed
            glDeleteProgram(program);
            throw new RuntimeException("Failed to link program");
        }

        return program;
    }

    private int compileShader(int type, String shaderSrc) {
        Log.d(TAG, "Compiling shader: " + type);

        // Create new shader object
        final int shader = glCreateShader(type);
        if (shader == 0) {
            Log.w(TAG, "Could not create new shader");
            return 0;
        }

        // Pass in the shader source
        glShaderSource(shader, shaderSrc);

        // Compile the shader
        glCompileShader(shader);

        // Verify the compile status
        final int[] compiled = new int[1];
        glGetShaderiv(shader, GL_COMPILE_STATUS, compiled, /* offset = */ 0);
        if (compiled[0] == 0) {
            // Delete the shader if compilation failed
            glDeleteShader(shader);
            Log.w(TAG, "Shader failed to compile");
            return 0;
        }

        return shader;
    }

    /**
     * Render the curremt frame.
     * @param surfaceTexture The surface texture containing the texture of the current frame.
     */
    public void drawFrame(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "Drawing frame");

        // Set the transform matrix of the surface texture
        surfaceTexture.getTransformMatrix(uMatrix);

        glClear(/* mask = */ GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4);
        if (glGetError() != 0) {
            throw new RuntimeException("Failed to draw to frame");
        }

    }

    /**
     * Gets the ID of the texture bound to the GL program.
     */
    public int getTextureID() {
        return textureID;
    }
}
