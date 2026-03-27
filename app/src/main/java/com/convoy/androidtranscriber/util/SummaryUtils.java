package com.convoy.androidtranscriber.util;

public final class SummaryUtils {
    private SummaryUtils() {}

    public static String buildSummaryReport(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "No transcription produced.";
        }
        return "No AI summary model is installed on Android yet.\n\nThis build only stores the transcript. A real summary requires a second local model runtime, which is not implemented in the Android app yet.";
    }
}
