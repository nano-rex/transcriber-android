package com.convoy.androidtranscriber.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public final class StorageUtils {
    private static final String APP_FOLDER = "TranscriberAndroid";

    private StorageUtils() {}

    public static File baseDir(Context context) {
        String mode = AppSettings.getStorageMode(context);
        File root;
        switch (mode) {
            case AppSettings.STORAGE_DOWNLOADS:
                root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (root == null) root = new File(context.getFilesDir(), "downloads");
                break;
            case AppSettings.STORAGE_INTERNAL:
                root = new File(context.getFilesDir(), "workspace");
                break;
            case AppSettings.STORAGE_DOCUMENTS:
            default:
                root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (root == null) root = new File(context.getFilesDir(), "documents");
                break;
        }
        File dir = new File(root, APP_FOLDER);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File outputsDir(Context context) {
        return childDir(context, "outputs");
    }

    public static File importsDir(Context context) {
        return childDir(context, "imports");
    }

    public static File modelsDir(Context context) {
        return childDir(context, "models");
    }

    public static File customModelsDir(Context context) {
        return childDir(context, "models-custom");
    }

    public static File denoiseDir(Context context) {
        return childDir(context, "denoise");
    }

    public static File chunksDir(Context context) {
        return childDir(context, "chunks");
    }

    public static String describeBaseDir(Context context) {
        return baseDir(context).getAbsolutePath();
    }

    private static File childDir(Context context, String name) {
        File dir = new File(baseDir(context), name);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
