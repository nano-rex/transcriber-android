package com.convoy.androidtranscriber.engine;

public final class WhisperCppLib {
    static {
        System.loadLibrary("whisper");
    }

    private WhisperCppLib() {}

    public static native long initContext(String modelPath);
    public static native void freeContext(long contextPtr);
    public static native boolean fullTranscribe(long contextPtr, int numThreads, float[] audioData, String languageHint);
    public static native int getTextSegmentCount(long contextPtr);
    public static native String getTextSegment(long contextPtr, int index);
}
