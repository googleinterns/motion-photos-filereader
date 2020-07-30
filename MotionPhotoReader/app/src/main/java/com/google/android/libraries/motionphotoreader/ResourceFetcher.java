package com.google.android.libraries.motionphotoreader;

import android.content.Context;
import android.util.Log;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A suite of methods to fetch resource files, asset files, and raw files.
 */
class ResourceFetcher {
    private static final String TAG = "ResourceFetcher";

    /**
     * Gets a resource from the res/raw folder and creates a temporary file holding the resource.
     * Used to fetch motion photo files.
     * @param context The context containing the raw resource.
     * @param id The id of the file.
     * @param prefix The name of the temporary file to create from the raw resource.
     * @param suffix The extension of the temporary file to create from the raw resource.
     * @return a File object containing the raw resource file.
     */
    public static File fetchRawFile(Context context,
                                    int id, String prefix,
                                    String suffix) throws IOException {
        try (InputStream input = context.getResources().openRawResource(id)) {
            File file = null;
            try {
                file = File.createTempFile(prefix, suffix);
            } catch (IOException e) {
                Log.e(TAG, "Error fetching raw file", e);
            }
            writeBytesToFile(input, file);
            return file;
        }
    }

    /**
     * Gets a resource from the res/raw folder and creates a temporary file holding the resource.
     * Used to fetch motion photo files.
     * @param classLoader The class loader for the resource.
     * @param filename The name of the resource file.
     * @return a File object containing the raw resource file.
     */
    public static File fetchResourceFile(ClassLoader classLoader, String filename) {
        URL resource = classLoader.getResource(filename);
        return new File(resource.getPath());
    }

    /**
     * Gets a resource from the assets folder of a given context and creates a temporary file
     * holding the resource. Used in instrumentation tests to fetch motion photo files.
     * @param context The context containing the asset file.
     * @param filename The name of the asset file.
     * @return a File object containing the raw resource file.
     * @throws IOException if an error occurs while creating the new file
     */
    public static File fetchAssetFile(Context context,
                                      String filename,
                                      String prefix,
                                      String suffix) throws IOException {
        try (InputStream input = context.getResources().getAssets().open(filename)) {
            // Write file to temporary folder for instrumentation test access
            File f = File.createTempFile(prefix, suffix);
            writeBytesToFile(input, f);
            return f;
        }
    }

    private static void writeBytesToFile(InputStream input, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteStreams.copy(input, fos);
        }
    }
}
