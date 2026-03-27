package com.convoy.androidtranscriber.engine;

import com.convoy.androidtranscriber.util.WaveUtil;

import java.io.IOException;
import java.util.Locale;

public class WhisperEngineWhisperCpp implements WhisperEngine {
    private long contextPtr = 0L;
    private boolean initialized = false;
    private String languageHint = null;

    @Override
    public boolean isInitialized() {
        return initialized && contextPtr != 0L;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        deinitialize();
        contextPtr = WhisperCppLib.initContext(modelPath);
        if (contextPtr == 0L) {
            throw new IOException("Failed to load whisper.cpp model: " + modelPath);
        }
        languageHint = multilingual ? null : "en";
        initialized = true;
        return true;
    }

    @Override
    public void deinitialize() {
        if (contextPtr != 0L) {
            WhisperCppLib.freeContext(contextPtr);
            contextPtr = 0L;
        }
        initialized = false;
        languageHint = null;
    }

    @Override
    public String transcribeFile(String wavePath) {
        float[] samples = WaveUtil.getSamples(wavePath);
        return transcribeBuffer(samples);
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        if (!isInitialized() || samples == null || samples.length == 0) return "";
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        boolean ok = WhisperCppLib.fullTranscribe(contextPtr, threads, samples, languageHint);
        if (!ok) return "";
        int count = WhisperCppLib.getTextSegmentCount(contextPtr);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String segment = WhisperCppLib.getTextSegment(contextPtr, i);
            if (segment != null && !segment.trim().isEmpty()) {
                if (out.length() > 0) out.append(' ');
                out.append(segment.trim());
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }
}
