package com.convoy.androidtranscriber.engine;

import android.content.Context;
import android.util.Log;

import com.convoy.androidtranscriber.util.WaveUtil;
import com.convoy.androidtranscriber.util.WhisperUtil;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class WhisperEngineJava implements WhisperEngine {
    private static final String TAG = "WhisperEngineJava";
    private final WhisperUtil whisperUtil = new WhisperUtil();
    private final Context context;
    private boolean initialized = false;
    private Interpreter interpreter = null;

    public WhisperEngineJava(Context context) {
        this.context = context;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        loadModel(modelPath);
        boolean loaded = whisperUtil.loadFiltersAndVocab(multilingual, vocabPath);
        initialized = loaded;
        return initialized;
    }

    @Override
    public void deinitialize() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        initialized = false;
    }

    @Override
    public String transcribeFile(String wavePath) {
        float[] samples = WaveUtil.getSamples(wavePath);
        if (samples == null || samples.length == 0) return "";

        int chunkSamples = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE;
        int totalChunks = (int) Math.ceil(samples.length / (double) chunkSamples);
        StringBuilder out = new StringBuilder();

        for (int chunk = 0; chunk < totalChunks; chunk++) {
            int start = chunk * chunkSamples;
            int remaining = Math.max(0, samples.length - start);
            int copyLength = Math.min(chunkSamples, remaining);
            if (copyLength <= 0) break;

            float[] inputSamples = new float[chunkSamples];
            System.arraycopy(samples, start, inputSamples, 0, copyLength);

            float[] melSpectrogram = getMelSpectrogram(inputSamples);
            String chunkText = runInference(melSpectrogram).trim();
            if (!chunkText.isEmpty()) {
                if (out.length() > 0) out.append(' ');
                out.append(chunkText);
            }
        }

        return out.toString();
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        return null;
    }

    private void loadModel(String modelPath) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(modelPath)) {
            FileChannel fileChannel = fileInputStream.getChannel();
            ByteBuffer tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Runtime.getRuntime().availableProcessors());
            interpreter = new Interpreter(tfliteModel, options);
        }
    }

    private float[] getMelSpectrogram(float[] inputSamples) {
        int cores = Runtime.getRuntime().availableProcessors();
        return whisperUtil.getMelSpectrogram(inputSamples, inputSamples.length, cores);
    }

    private String runInference(float[] inputData) {
        Tensor inputTensor = interpreter.getInputTensor(0);
        TensorBuffer inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType());
        Tensor outputTensor = interpreter.getOutputTensor(0);
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32);

        int inputSize = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES;
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder());
        for (float input : inputData) {
            inputBuf.putFloat(input);
        }
        inputBuf.rewind();
        inputBuffer.loadBuffer(inputBuf);

        interpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer());

        ByteBuffer output = outputBuffer.getBuffer().order(ByteOrder.nativeOrder());
        output.rewind();
        StringBuilder result = new StringBuilder();
        int outputLen = outputBuffer.getIntArray().length;
        for (int i = 0; i < outputLen && output.remaining() >= Integer.BYTES; i++) {
            int token = output.getInt();
            if (token == whisperUtil.getTokenEOT()) break;
            if (token < whisperUtil.getTokenEOT()) {
                String word = whisperUtil.getWordFromToken(token);
                if (word != null) result.append(word);
            } else {
                Log.d(TAG, "Skipping token: " + token);
            }
        }
        return result.toString();
    }
}
