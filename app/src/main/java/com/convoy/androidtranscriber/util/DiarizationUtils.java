package com.convoy.androidtranscriber.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DiarizationUtils {
    public static final int SAMPLE_RATE = 16000;
    public static final int CHUNK_SECONDS = 30;
    public static final int CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_SECONDS;

    private DiarizationUtils() {}

    public static List<TextSegment> buildTextSegments(String transcript, int totalSamples) {
        String safe = transcript == null ? "" : transcript.trim();
        if (safe.isEmpty()) return Collections.emptyList();

        int chunkCount = Math.max(1, (int) Math.ceil(totalSamples / (double) CHUNK_SAMPLES));
        String[] words = safe.split("\\s+");
        List<TextSegment> segments = new ArrayList<>();

        int wordsPerChunk = Math.max(1, (int) Math.ceil(words.length / (double) chunkCount));
        int cursor = 0;
        for (int i = 0; i < chunkCount; i++) {
            int startSample = i * CHUNK_SAMPLES;
            int endSample = Math.min(totalSamples, startSample + CHUNK_SAMPLES);
            if (startSample >= endSample) break;

            int endWord = Math.min(words.length, cursor + wordsPerChunk);
            StringBuilder text = new StringBuilder();
            for (int w = cursor; w < endWord; w++) {
                if (text.length() > 0) text.append(' ');
                text.append(words[w]);
            }
            cursor = endWord;

            segments.add(new TextSegment(
                    startSample / (double) SAMPLE_RATE,
                    endSample / (double) SAMPLE_RATE,
                    text.toString().trim()
            ));
            if (cursor >= words.length && i >= chunkCount - 1) break;
        }

        if (!segments.isEmpty() && cursor < words.length) {
            TextSegment last = segments.get(segments.size() - 1);
            StringBuilder merged = new StringBuilder(last.text);
            while (cursor < words.length) {
                if (merged.length() > 0) merged.append(' ');
                merged.append(words[cursor++]);
            }
            segments.set(segments.size() - 1, new TextSegment(last.startSeconds, last.endSeconds, merged.toString()));
        }

        return segments;
    }

    public static String buildTimestampedTranscript(List<TextSegment> segments) {
        StringBuilder out = new StringBuilder();
        for (TextSegment seg : segments) {
            if (seg.text.isEmpty()) continue;
            out.append('[')
                    .append(formatTimestamp(seg.startSeconds))
                    .append(" - ")
                    .append(formatTimestamp(seg.endSeconds))
                    .append("]\n")
                    .append(seg.text)
                    .append("\n\n");
        }
        return out.toString().trim();
    }

    public static String buildDiarizedTranscript(float[] samples, List<TextSegment> segments) {
        if (samples == null || samples.length == 0 || segments == null || segments.isEmpty()) return "";
        double[] energies = new double[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            int start = Math.max(0, (int) Math.floor(seg.startSeconds * SAMPLE_RATE));
            int end = Math.min(samples.length, (int) Math.ceil(seg.endSeconds * SAMPLE_RATE));
            energies[i] = rms(samples, start, end);
        }

        double c1 = min(energies);
        double c2 = max(energies);
        if (Math.abs(c1 - c2) < 1e-9) c2 = c1 + 1e-6;

        for (int iter = 0; iter < 8; iter++) {
            double s1 = 0.0, s2 = 0.0;
            int n1 = 0, n2 = 0;
            for (double e : energies) {
                if (Math.abs(e - c1) <= Math.abs(e - c2)) {
                    s1 += e;
                    n1++;
                } else {
                    s2 += e;
                    n2++;
                }
            }
            if (n1 > 0) c1 = s1 / n1;
            if (n2 > 0) c2 = s2 / n2;
        }

        boolean lowerIsSpeaker1 = c1 <= c2;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            if (seg.text.isEmpty()) continue;
            boolean nearC1 = Math.abs(energies[i] - c1) <= Math.abs(energies[i] - c2);
            int speaker = lowerIsSpeaker1 ? (nearC1 ? 1 : 2) : (nearC1 ? 2 : 1);
            out.append('[')
                    .append(formatTimestamp(seg.startSeconds))
                    .append(" - ")
                    .append(formatTimestamp(seg.endSeconds))
                    .append("] Speaker ")
                    .append(speaker)
                    .append(": ")
                    .append(seg.text)
                    .append("\n\n");
        }
        return out.toString().trim();
    }

    public static String formatTimestamp(double seconds) {
        int total = Math.max(0, (int) Math.floor(seconds));
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int secs = total % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs);
    }

    private static double rms(float[] samples, int start, int end) {
        if (start >= end) return 0.0;
        double sumSq = 0.0;
        for (int i = start; i < end; i++) {
            double s = samples[i];
            sumSq += s * s;
        }
        return Math.sqrt(sumSq / (end - start));
    }

    private static double min(double[] values) {
        double m = Double.POSITIVE_INFINITY;
        for (double v : values) m = Math.min(m, v);
        return m;
    }

    private static double max(double[] values) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : values) m = Math.max(m, v);
        return m;
    }

    public static final class TextSegment {
        public final double startSeconds;
        public final double endSeconds;
        public final String text;

        public TextSegment(double startSeconds, double endSeconds, String text) {
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.text = text == null ? "" : text;
        }
    }
}
