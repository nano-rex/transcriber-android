package com.convoy.androidtranscriber.util;

import android.app.ActivityManager;
import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModelUtils {
    private ModelUtils() {}

    public static final String TINY_EN = "tiny-en";
    public static final String TINY = "tiny";
    public static final String SMALL = "small";
    public static final String MEDIUM = "medium";

    public static String recommendModelTier(Context context) {
        int cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }
        double memGb = memoryInfo.totalMem / 1024.0 / 1024.0 / 1024.0;

        if (cpuThreads >= 8 && memGb >= 12.0) return MEDIUM;
        if (cpuThreads >= 4 && memGb >= 6.0) return SMALL;
        return TINY_EN;
    }

    public static Map<String, String> bundledModelAssets() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(TINY_EN, "models/whisper-tiny.en.tflite");
        map.put(TINY, "models/whisper-tiny.tflite");
        map.put(SMALL, "models/whisper-small.tflite");
        map.put(MEDIUM, "models/whisper-medium.tflite");
        return map;
    }

    public static boolean isMultilingual(String tier) {
        return !TINY_EN.equals(tier);
    }

    public static String vocabAssetForTier(String tier) {
        return isMultilingual(tier) ? "filters_vocab_multilingual.bin" : "filters_vocab_en.bin";
    }
}
