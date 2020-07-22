package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL;
import javax.microedition.khronos.opengles.GL;

/**
 * Renders frames from a MediaCodec decoder (obtained from a surface texture attached to an OpenGL
 * texture) onto an EGL surface.
 */
public class TextureRender {

    private static final String TAG = "TextureRender";

    private static final int FLOAT_SIZE_BYTES = 4; // number of bytes for a float
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES; // 5 due to five coordinate float values (x,y,z,u,v)
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3; // texture coords begin 3 offset units from array start (skip the position coordinates)

    private static final String VERTEX_SHADER =
            "uniform mat4 u_Matrix;\n" +
            "attribute vec4 a_Position;\n" +
            "attribute vec2 a_TextureCoordinates;\n" +
            "varying vec2 v_TextureCoordinates;\n" +
            "void main() {\n" +
            "  v_TextureCoordinates = a_TextureCoordinates;\n" +
            "  gl_Position = u_Matrix * a_Position;\n" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform sampler2D u_TextureUnit;\n" +
            "varying vec2 v_TextureCoordinates;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(u_TextureUnit, v_TextureCoordinates);\n" +
            "}";

    // A single plane comprised of two triangles to hold the image texture
    private final float[] triangleVerticesData = {
            // positions (x,y,z)    // texture coordinates (u,v)
            -1.0f, -1.0f, 0.0f,     0.0f, 0.0f,  // bottom left
             1.0f, -1.0f, 0.0f,     1,0f, 0.0f,  // bottom right
            -1.0f,  1.0f, 0.0f,     0.0f, 1.0f,  // top left
             1.0f,  1.0f, 0.0f,     1.0f, 1.0f,  // top right
    };

    private float[] uMatrix = new float[16];

    private int textureID;
    private int program;
    private int aPositionHandle;
    private int aTextureHandle;
    private int uMatrixHandle;
    private int uTextureUnitHandle;

    private FloatBuffer triangleVertices;

    public TextureRender() {
        triangleVertices = ByteBuffer
                .allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        triangleVertices.put(triangleVerticesData).position(/* newPosition = */ 0);

        Matrix.setIdentityM(/* sm = */ uMatrix, /* smOffset = */ 0);
    }

    public int getTextureID() {
        return textureID;
    }

    public void onSurfaceCreated() {
        Log.d(TAG, "Initializing state");

        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);

        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = linkProgram(vertexShader, fragmentShader);

        // Get vertex and texture handles
        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position");
        aTextureHandle = GLES20.glGetAttribLocation(program, "a_TextureCoordinates");
        uMatrixHandle = GLES20.glGetUniformLocation(program, "u_Matrix");
        uTextureUnitHandle = GLES20.glGetUniformLocation(program, "u_TextureUnit");

        // Validate the program
        GLES20.glValidateProgram(program);
        final int[] validateStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, validateStatus, /* offset = */ 0);
        Log.v(TAG, "Program validation results: " + validateStatus[0]
                + "\nLog: " + GLES20.glGetProgramInfoLog(program));

        GLES20.glUseProgram(program);

        // Create and bind textures
        int[] textureIds = new int[1];
        GLES20.glGenTextures(/* n = */ 1, textureIds, 0);
        textureID = textureIds[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);

        // Set texture parameters
        GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST
        );
        GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );
    }

    private static int linkProgram(int vertexShader, int fragmentShader) {
        Log.d(TAG, "Linking program");

        // Create the program
        final int program = GLES20.glCreateProgram();
        if (program == 0) {
            Log.w(TAG, "Could not create new program");
            return 0;
        }

        // Attach shaders to program and link them together
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        // Verify linking status
        final int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, /* offset = */ 0);
        if (linkStatus[0] == 0) {
            // Delete the program if linking failed
            GLES20.glDeleteProgram(program);
            Log.w(TAG, "Failed to link program");
            return 0;
        }

        return program;
    }

    private int compileShader(int type, String shaderSrc) {
        Log.d(TAG, "Compiling shader: " + type);

        // Create new shader object
        final int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            Log.w(TAG, "Could not create new shader");
            return 0;
        }

        // Pass in the shader source
        GLES20.glShaderSource(shader, shaderSrc);

        // Compile the shader
        GLES20.glCompileShader(shader);

        // Verify the compile status
        final int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, /* offset = */ 0);
        if (compiled[0] == 0) {
            // Delete the shader if compilation failed
            GLES20.glDeleteShader(shader);
            Log.w(TAG, "Shader failed to compile");
            return 0;
        }

        return shader;
    }

    public void drawFrame(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "Drawing frame");

        // Set the transform matrix of the surface texture
        surfaceTexture.getTransformMatrix(uMatrix);

        GLES20.glClearColor(/* red = */ 0.0f, /* green = */ 0.0f, /* blue = */ 0.0f, /* alpha = */ 1.0f);
        GLES20.glClear(/* mask = */ GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);

        // Process vertices
        triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(
                aPositionHandle,
                /* size = */ 3,
                /* type = */ GLES20.GL_FLOAT,
                /* normalized = */ false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                triangleVertices
        );
        GLES20.glEnableVertexAttribArray(aPositionHandle);

        // Process texture
        triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(
                aTextureHandle,
                /* size = */ 2,
                /* type = */ GLES20.GL_FLOAT,
                /* normalized = */ false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                triangleVertices
        );
        GLES20.glEnableVertexAttribArray(aTextureHandle);

        // Apply matrix transforms
        GLES20.glUniformMatrix4fv(
                uMatrixHandle,
                /* count = */ 1,
                /* transpose = */ false,
                uMatrix,
                /* offset = */ 0
        );

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
        GLES20.glUniform1i(uTextureUnitHandle, /* x = */0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4);
        GLES20.glFinish();
    }
}
