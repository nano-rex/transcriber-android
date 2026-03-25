package com.convoy.androidtranscriber.asr;

import android.content.Context;
import android.util.Log;

import com.convoy.androidtranscriber.engine.WhisperEngine;
import com.convoy.androidtranscriber.engine.WhisperEngineJava;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Whisper {
    public interface WhisperListener {
        void onUpdateReceived(String message);
        void onResultReceived(String result);
    }

    private static final String TAG = "Whisper";
    public static final String MSG_PROCESSING = "Processing...";
    public static final String MSG_PROCESSING_DONE = "Processing done";
    public static final String MSG_FILE_NOT_FOUND = "Input file does not exist";

    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final WhisperEngine whisperEngine;
    private String wavFilePath;
    private WhisperListener listener;

    public Whisper(Context context) {
        whisperEngine = new WhisperEngineJava(context);
    }

    public void setListener(WhisperListener listener) {
        this.listener = listener;
    }

    public void loadModel(String modelPath, String vocabPath, boolean isMultilingual) {
        try {
            whisperEngine.initialize(modelPath, vocabPath, isMultilingual);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing model", e);
            sendUpdate("Model initialization failed");
        }
    }

    public void unloadModel() {
        whisperEngine.deinitialize();
    }

    public boolean isInProgress() {
        return inProgress.get();
    }

    public void setFilePath(String wavFilePath) {
        this.wavFilePath = wavFilePath;
    }

    public void start() {
        if (!inProgress.compareAndSet(false, true)) return;
        new Thread(this::transcribeFile).start();
    }

    private void transcribeFile() {
        try {
            if (!whisperEngine.isInitialized() || wavFilePath == null) {
                sendUpdate("Engine not initialized or file path not set");
                return;
            }
            File waveFile = new File(wavFilePath);
            if (!waveFile.exists()) {
                sendUpdate(MSG_FILE_NOT_FOUND);
                return;
            }
            sendUpdate(MSG_PROCESSING);
            String result = whisperEngine.transcribeFile(wavFilePath);
            sendResult(result);
            sendUpdate(MSG_PROCESSING_DONE);
        } catch (Exception e) {
            Log.e(TAG, "Error during transcription", e);
            sendUpdate("Transcription failed: " + e.getMessage());
        } finally {
            inProgress.set(false);
        }
    }

    private void sendUpdate(String message) {
        if (listener != null) listener.onUpdateReceived(message);
    }

    private void sendResult(String message) {
        if (listener != null) listener.onResultReceived(message);
    }
}
