package com.google.android.libraries.motionphotoreader;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES30.*;

/**
 * Renders frames from a MediaCodec decoder (obtained from a surface texture attached to an OpenGL
 * texture) onto an EGL surface.
 */
public class TextureRender {

    private static final String TAG = "TextureRender";

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;

    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
            "uniform mat4 uMatrix;\n" +
            "in vec2 aPosition;\n" +
            "out vec2 TexCoord;\n" +
            "void main() {\n" +
            "  TexCoord = (vec2(1.0, 1.0) + aPosition) * 0.5;\n" +
            "  gl_Position = uMatrix * vec4(aPosition, 0.0, 1.0);\n" +
            "}";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "uniform sampler2D uTexUnit;\n" +
            "in vec2 TexCoord;\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "  FragColor = texture(uTexUnit, TexCoord);\n" +
            "}";

    // A single plane comprised of two triangles to hold the image texture
    private final float[] triangleVerticesData = {
            // positions (x,y)
            -1.0f, -1.0f,  // bottom left
             1.0f, -1.0f,  // bottom right
            -1.0f,  1.0f,  // top left
             1.0f,  1.0f,  // top right
    };

    private float[] uMatrix = new float[16];

    private int textureID;
    private int program;
    private int aPositionHandle;
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

    public void onSurfaceCreated(int width, int height) {
        Log.d(TAG, "Initializing state");

        glClearColor(1.0f, 0.0f, 0.0f, 0.0f);

        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = linkProgram(vertexShader, fragmentShader);

        // Validate the program
        glValidateProgram(program);
        final int[] validateStatus = new int[1];
        glGetProgramiv(program, GL_VALIDATE_STATUS, validateStatus, /* offset = */ 0);
        Log.v(TAG, "Program validation results: " + validateStatus[0]
                + "\nLog: " + glGetProgramInfoLog(program));

        glViewport(0, 0, width, height);
        glUseProgram(program);

        // Create and bind textures
        int[] textureIds = new int[1];
        glGenTextures(/* n = */ 1, textureIds, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
        textureID = textureIds[0];

        // Set texture parameters
        glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_MIN_FILTER,
                GL_NEAREST
        );
        glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_MAG_FILTER,
                GL_LINEAR
        );
        glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_WRAP_S,
                GL_CLAMP_TO_EDGE
        );
        glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_WRAP_T,
                GL_CLAMP_TO_EDGE
        );

        // TODO: Switch to VBOs and VAOs
        aPositionHandle = glGetAttribLocation(program, "aPosition");
        glVertexAttribPointer(
                aPositionHandle,
                /* size = */ 2,
                /* type = */ GL_FLOAT,
                /* normalized = */ false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        );
        glEnableVertexAttribArray(aPositionHandle);

        uMatrixHandle = glGetUniformLocation(program, "uMatrix");
        glUniformMatrix4fv(
                uMatrixHandle,
                /* count = */ 1,
                /* transpose = */ false,
                uMatrix,
                /* offset = */ 0
        );

        uTextureUnitHandle = glGetUniformLocation(program, "uTexUnit");
        glUniform1i(uTextureUnitHandle, /* x = */0);
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

    public void drawFrame(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "Drawing frame");

        // Set the transform matrix of the surface texture
        surfaceTexture.getTransformMatrix(uMatrix);

        glClear(/* mask = */ GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4);
    }
}
