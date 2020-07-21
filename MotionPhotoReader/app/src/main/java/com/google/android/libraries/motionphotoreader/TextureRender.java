package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class TextureRender {

    private static final String TAG = "TextureRender";

    private static final int FLOAT_SIZE_BYTES = 4; // number of bytes for a float?
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES; // 5 due to five coordinate float values (x,y,z,u,v)?
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3; // texture coords begin 3 offset units from array start (skip the position coordinates)

    private static final String VERTEX_SHADER = "attribute vec4 a_Position\n"
            + "void main() {\n"
            + "  gl_Position = a_Position;\n"
            + "}\n";
    private static final String FRAGMENT_SHADER = "precision mediump float;\n"
            + "uniform vec4 u_Color;\n"
            + "void main() {\n"
            + "  gl_FragColor = u_Color;\n"
            + "}\n";

    // A single plane comprised of two triangles to hold the image texture
    private final float[] triangleVerticesData = {
            // positions (x,y,z)    // texture coordinates (u,v)
            -1.0f, -1.0f, 0.0f,     0.0f, 0.0f,  // bottom left
             1.0f, -1.0f, 0.0f,     1,0f, 0.0f,  // bottom right
            -1.0f,  1.0f, 0.0f,     0.0f, 1.0f,  // top left
             1.0f,  1.0f, 0.0f,     1.0f, 1.0f,  // top right
    };

    private float[] stMatrix;
    private int textureID;
    private int program;
    private int aPositionHandle;
    private int aTextureHandle;

    private FloatBuffer triangleVertices;

    public TextureRender() {
        triangleVertices = ByteBuffer
                .allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        triangleVertices.put(triangleVerticesData).position(/* newPosition = */ 0);
        Matrix.setIdentityM(stMatrix, /* smOffset = */ 0);
    }

    public int getTextureID() {
        return textureID;
    }

    public void initializeStatePostSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        // Load the shaders
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            return 0;
        }

        // Create the program
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, fragmentShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                String infoLog = GLES20.glGetProgramInfoLog(program);
                Log.e(TAG, "Could not link program: " + infoLog);
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    private int loadShader(int type, String shaderSrc) {
        // Create the shader object
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            return 0;
        }

        // Load the shader source, compile the shader
        GLES20.glShaderSource(shader, shaderSrc);
        GLES20.glCompileShader(shader);

        // check the compile status
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            // Retrieve the compiler messages when compilation fails
            int[] infoLen = new int[0];
            GLES20.glGetShaderiv(shader, GLES20.GL_INFO_LOG_LENGTH, infoLen, 0);
            if (infoLen[0] > 1) {
                String infoLog = GLES20.glGetShaderInfoLog(shader);
                Log.d(TAG, infoLog);
            }
        }
        return shader;
    }

    public void drawFrame(SurfaceTexture surfaceTexture) {
        checkGlError("onDrawFrame start");
        surfaceTexture.getTransformMatrix(stMatrix);

        GLES20.glClearColor(/* red = */ 0.0f, /* green = */ 0.0f, /* blue = */ 0.0f, /* alpha = */ 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);
        checkGlError("glUseProgram");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);

        // Process vertices
        triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(
                aPositionHandle,
                /* size = */ 3,
                /* type = */ GLES20.GL_FLOAT,
                /* normalized = */ false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        );
        checkGlError("glVertexAttribPointer aPosition");
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        checkGlError("glEnableVertexAttribArray aPositionHandle");

        // Process texture
        triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(
                aTextureHandle,
                /* size = */ 2,
                /* type = */ GLES20.GL_FLOAT,
                /* normalized = */ false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        );
        checkGlError("glVertexAttribPointer aTextureHandle");
        GLES20.glEnableVertexAttribArray(aTextureHandle);
        checkGlError("glEnableVertexAttribArray aTextureHandle");

        // Do matrix stuff here...

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4);
        checkGlError("glDrawArrays");
        GLES20.glFinish();
    }

    public void checkGlError(String tag) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(tag+ ": glError " + error);
        }
    }
}
