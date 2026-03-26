package com.convoy.androidtranscriber.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public final class StorageUtils {
    private StorageUtils() {}

    public static File outputsDir(Context context) {
        File root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (root == null) {
            root = new File(context.getFilesDir(), "documents");
        }
        File dir = new File(new File(root, "TranscriberAndroid"), "outputs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
