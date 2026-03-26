package com.convoy.androidtranscriber.util;

import android.app.ActivityManager;
import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    public static List<ModelSpec> bundledModelSpecs() {
        List<ModelSpec> specs = new ArrayList<>();
        specs.add(new ModelSpec(TINY_EN, "tiny-en", "models/whisper-tiny.en.tflite", true, false));
        specs.add(new ModelSpec(TINY, "tiny", "models/whisper-tiny.tflite", true, true));
        return specs;
    }

    public static List<ModelSpec> availableModels(Context context) {
        List<ModelSpec> specs = new ArrayList<>();
        for (ModelSpec spec : bundledModelSpecs()) {
            if (assetExists(context, spec.assetPath)) specs.add(spec);
        }

        File customDir = customModelsDir(context);
        File[] files = customDir.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".tflite"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String id = "custom:" + fileName;
                boolean multilingual = !looksEnglishOnly(fileName);
                String label = stripExtension(fileName) + " (custom)";
                specs.add(new ModelSpec(id, label, file.getAbsolutePath(), false, multilingual));
            }
        }
        return specs;
    }

    public static ModelSpec findModel(Context context, String id) {
        for (ModelSpec spec : availableModels(context)) {
            if (spec.id.equals(id)) return spec;
        }
        return null;
    }

    public static boolean isMultilingual(String tier) {
        return !TINY_EN.equals(tier);
    }

    public static String vocabAssetForTier(String tier) {
        return isMultilingual(tier) ? "filters_vocab_multilingual.bin" : "filters_vocab_en.bin";
    }

    public static String vocabAssetForModel(ModelSpec spec) {
        return spec.multilingual ? "filters_vocab_multilingual.bin" : "filters_vocab_en.bin";
    }

    public static File customModelsDir(Context context) {
        File dir = new File(context.getFilesDir(), "models-custom");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static boolean assetExists(Context context, String path) {
        try {
            context.getAssets().open(path).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean looksEnglishOnly(String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        return lower.contains(".en.") || lower.contains("-en.") || lower.endsWith(".en.tflite");
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static final class ModelSpec {
        public final String id;
        public final String label;
        public final String assetPath;
        public final boolean bundled;
        public final boolean multilingual;

        public ModelSpec(String id, String label, String assetPath, boolean bundled, boolean multilingual) {
            this.id = id;
            this.label = label;
            this.assetPath = assetPath;
            this.bundled = bundled;
            this.multilingual = multilingual;
        }

        public String tierHint() {
            String normalized = label.toLowerCase(Locale.US);
            if (normalized.contains("medium")) return MEDIUM;
            if (normalized.contains("small")) return SMALL;
            if (normalized.contains("tiny-en")) return TINY_EN;
            if (normalized.contains("tiny")) return TINY;
            return multilingual ? TINY : TINY_EN;
        }
    }
}
