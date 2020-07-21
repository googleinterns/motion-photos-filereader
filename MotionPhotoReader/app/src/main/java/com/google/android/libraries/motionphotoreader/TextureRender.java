package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.nio.IntBuffer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_INFO_LOG_LENGTH;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.GL_NO_ERROR;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRUE;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glGetProgramInfoLog;
import static android.opengl.GLES20.glGetProgramiv;
import static android.opengl.GLES20.glGetShaderInfoLog;
import static android.opengl.GLES20.glGetShaderiv;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUseProgram;

public class TextureRender {

    private static final String TAG = "TextureRender";
    private static final String VERTEX_SHADER = "attribute vec4 a_Position\n"
            + "void main() {\n"
            + "  gl_Position = a_Position;\n"
            + "}\n";
    private static final String FRAGMENT_SHADER = "precision mediump float;\n"
            + "uniform vec4 u_Color;\n"
            + "void main() {\n"
            + "  gl_FragColor = u_Color;\n"
            + "}\n";

    private float[] stMatrix;
    private int textureID;
    private int program;

    public TextureRender() {
        Matrix.setIdentityM(stMatrix, /* smOffset = */ 0);
    }

    public int getTextureID() {
        return textureID;
    }

    public void drawFrame(SurfaceTexture surfaceTexture) {
        checkGlError("onDrawFrame start");
        surfaceTexture.getTransformMatrix(stMatrix);

        glClearColor(/* red = */ 0.0f, /* green = */ 0.0f, /* blue = */ 0.0f, /* alpha = */ 1.0f);
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);

        glUseProgram(program);
        checkGlError("glUseProgram");

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID);


    }

    private int createProgram(String vertexSource, String fragmentSource) {
        // Load the shaders
        int vertexShader = loadShader(GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int fragmentShader = loadShader(GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            return 0;
        }

        // Create the program
        int program = glCreateProgram();
        if (program != 0) {
            glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            glAttachShader(program, fragmentShader);
            checkGlError("glAttachShader");
            glLinkProgram(program);
            int[] linkStatus = new int[1];
            glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GL_TRUE) {
                String infoLog = glGetProgramInfoLog(program);
                Log.e(TAG, "Could not link program: " + infoLog);
                glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    private void checkGlError(String tag) {
        int error;
        while ((error = glGetError()) != GL_NO_ERROR) {
            throw new RuntimeException(tag+ ": glError " + error);
        }
    }

    private int loadShader(int type, String shaderSrc) {
        // Create the shader object
        int shader = glCreateShader(type);
        if (shader == 0) {
            return 0;
        }

        // Load the shader source, compile the shader
        glShaderSource(shader, shaderSrc);
        glCompileShader(shader);

        // check the compile status
        int[] compiled = new int[1];
        glGetShaderiv(shader, GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            // Retrieve the compiler messages when compilation fails
            int[] infoLen = new int[0];
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, infoLen, 0);
            if (infoLen[0] > 1) {
                String infoLog = glGetShaderInfoLog(shader);
                Log.d(TAG, infoLog);
            }
        }
        return shader;
    }
}
