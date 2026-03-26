package com.convoy.androidtranscriber.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String PREFS = "transcriber_settings";
    private static final String KEY_AI_ENHANCE = "ai_enhance_enabled";
    private static final String KEY_TRIM = "trim_enabled";

    private AppSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isAiEnhanceEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AI_ENHANCE, true);
    }

    public static void setAiEnhanceEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AI_ENHANCE, enabled).apply();
    }

    public static boolean isTrimEnabled(Context context) {
        return prefs(context).getBoolean(KEY_TRIM, true);
    }

    public static void setTrimEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TRIM, enabled).apply();
    }
}
