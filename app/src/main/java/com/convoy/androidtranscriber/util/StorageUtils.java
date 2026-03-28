package com.convoy.androidtranscriber.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class StorageUtils {
    private static final String APP_FOLDER = "TranscriberAndroid";
    private static final String INTERNAL_FOLDER = "transcriber_private";

    private StorageUtils() {}

    public static File baseDir(Context context) {
        return baseDirForMode(context, AppSettings.getStorageMode(context));
    }

    public static File baseDirForMode(Context context, String mode) {
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
        return internalChildDir(context, "models");
    }

    public static File customModelsDir(Context context) {
        return internalChildDir(context, "models-custom");
    }

    public static File denoiseDir(Context context) {
        return internalChildDir(context, "denoise");
    }

    public static File chunksDir(Context context) {
        return internalChildDir(context, "chunks");
    }

    public static String describeBaseDir(Context context) {
        return baseDir(context).getAbsolutePath();
    }

    public static String describeBaseDirForMode(Context context, String mode) {
        return baseDirForMode(context, mode).getAbsolutePath();
    }

    public static void migrateWorkspace(Context context, String fromMode, String toMode) throws IOException {
        File fromBase = baseDirForMode(context, fromMode);
        File toBase = baseDirForMode(context, toMode);
        if (fromBase.getAbsolutePath().equals(toBase.getAbsolutePath())) return;
        if (!fromBase.exists()) return;

        String[] children = new String[]{"outputs", "imports"};
        for (String child : children) {
            moveDirectoryContents(new File(fromBase, child), new File(toBase, child));
        }
    }

    public static void migratePrivateWorkspaceToInternal(Context context) throws IOException {
        String[] children = new String[]{"models", "models-custom", "denoise", "chunks"};
        for (String mode : new String[]{AppSettings.STORAGE_DOCUMENTS, AppSettings.STORAGE_DOWNLOADS}) {
            File legacyBase = baseDirForMode(context, mode);
            for (String child : children) {
                moveDirectoryContents(new File(legacyBase, child), new File(internalBaseDir(context), child));
            }
        }
    }

    private static File childDir(Context context, String name) {
        File dir = new File(baseDir(context), name);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File internalBaseDir(Context context) {
        File dir = new File(context.getFilesDir(), INTERNAL_FOLDER);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File internalChildDir(Context context, String name) {
        try {
            migratePrivateWorkspaceToInternal(context);
        } catch (IOException ignored) {
        }
        File dir = new File(internalBaseDir(context), name);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static void moveDirectoryContents(File sourceDir, File targetDir) throws IOException {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) return;
        if (!targetDir.exists()) targetDir.mkdirs();
        File[] files = sourceDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            File target = new File(targetDir, file.getName());
            if (file.isDirectory()) {
                moveDirectoryContents(file, target);
                deleteTree(file);
            } else {
                copyOrMoveFile(file, target);
            }
        }
    }

    private static void copyOrMoveFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveError) {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (!source.delete()) {
                throw moveError;
            }
        }
    }

    private static void deleteTree(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteTree(child);
                } else {
                    child.delete();
                }
            }
        }
        dir.delete();
    }
}
