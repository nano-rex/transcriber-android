package com.convoy.androidtranscriber.util;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class SummaryUtils {
    private SummaryUtils() {}

    public static String makeSummary(String text) {
        if (text == null || text.trim().isEmpty()) return "No transcription produced.";
        String normalized = text.trim();
        if (normalized.length() < 220) return normalized;
        return normalized.substring(0, 220) + "...";
    }

    public static String makeKeyPoints(String text) {
        if (text == null || text.trim().isEmpty()) return "- No key points available\n";

        String lower = text.toLowerCase();
        List<String> points = new ArrayList<>();
        points.add("- Overall conversation captured in transcript");
        addIfFound(lower, points, "meeting", "- Mentions a meeting or discussion event");
        addIfFound(lower, points, "report", "- References a report or reported information");
        addIfFound(lower, points, "invoice", "- References invoice-related discussion");
        addIfFound(lower, points, "sales", "- References sales-related discussion");
        addIfFound(lower, points, "tomorrow", "- Contains a near-term time reference");
        addIfFound(lower, points, "next week", "- Contains a future scheduling reference");
        points.add("- Review names, dates, and numbers manually for accuracy");

        StringBuilder builder = new StringBuilder();
        for (String point : points) {
            builder.append(point).append('\n');
        }
        return builder.toString();
    }

    public static String buildSummaryReport(String text) {
        return "Overview:\n" + makeSummary(text) + "\n\nKey Points:\n" + makeKeyPoints(text);
    }

    public static String buildSummaryReport(Context context, String text) {
        if (context != null && text != null && !text.trim().isEmpty() && GemmaSummaryRunner.isGemmaAvailable(context)) {
            try {
                String gemma = GemmaSummaryRunner.summarize(context, text);
                if (gemma != null && !gemma.trim().isEmpty()) {
                    return gemma.trim();
                }
            } catch (Exception ignored) {
            }
        }
        return buildSummaryReport(text);
    }

    private static void addIfFound(String text, List<String> points, String needle, String point) {
        if (text.contains(needle)) points.add(point);
    }
}
