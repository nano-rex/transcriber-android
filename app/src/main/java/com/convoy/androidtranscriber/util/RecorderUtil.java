package com.convoy.androidtranscriber.util;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecorderUtil {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private RecorderUtil() {}

    public static RecorderSession startRecording(File outWavFile) {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBuffer, 4096);
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        AtomicBoolean running = new AtomicBoolean(true);
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        byte[] buffer = new byte[bufferSize];

        Thread worker = new Thread(() -> {
            recorder.startRecording();
            while (running.get()) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    pcmOut.write(buffer, 0, read);
                }
            }
            recorder.stop();
            recorder.release();
        }, "wav-recorder");
        worker.start();

        return new RecorderSession(outWavFile, running, worker, pcmOut);
    }

    public static final class RecorderSession {
        private final File outWavFile;
        private final AtomicBoolean running;
        private final Thread worker;
        private final ByteArrayOutputStream pcmOut;

        private RecorderSession(File outWavFile, AtomicBoolean running, Thread worker, ByteArrayOutputStream pcmOut) {
            this.outWavFile = outWavFile;
            this.running = running;
            this.worker = worker;
            this.pcmOut = pcmOut;
        }

        public File stopAndSave() throws Exception {
            running.set(false);
            worker.join();

            byte[] pcmBytes = pcmOut.toByteArray();
            short[] pcm16 = bytesToShorts(pcmBytes);
            short[] enhanced = enhancePcm16(pcm16);
            WaveUtil.createWaveFile(outWavFile.getAbsolutePath(), shortsToBytes(enhanced), SAMPLE_RATE, 1, 2);
            return outWavFile;
        }

        private short[] bytesToShorts(byte[] bytes) {
            ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            short[] out = new short[bytes.length / 2];
            for (int i = 0; i < out.length; i++) out[i] = bb.getShort();
            return out;
        }

        private byte[] shortsToBytes(short[] shorts) {
            ByteBuffer bb = ByteBuffer.allocate(shorts.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : shorts) bb.putShort(s);
            return bb.array();
        }

        private short[] enhancePcm16(short[] pcm16) {
            if (pcm16.length == 0) return pcm16;
            float[] floatSamples = new float[pcm16.length];
            for (int i = 0; i < pcm16.length; i++) {
                floatSamples[i] = pcm16[i] / 32768f;
            }

            float previousIn = 0f;
            float previousOut = 0f;
            for (int i = 0; i < floatSamples.length; i++) {
                float current = floatSamples[i];
                float filtered = (float) (0.97 * (previousOut + current - previousIn));
                floatSamples[i] = filtered;
                previousIn = current;
                previousOut = filtered;
            }

            floatSamples = applyAdaptiveGain(floatSamples);
            short[] out = new short[pcm16.length];
            for (int i = 0; i < floatSamples.length; i++) {
                float sample = (float) Math.tanh(floatSamples[i] * 1.5f) / 1.05f;
                sample = Math.max(-0.98f, Math.min(0.98f, sample));
                out[i] = (short) (sample * 32767f);
            }
            return out;
        }

        private float[] applyAdaptiveGain(float[] samples) {
            int window = 1600;
            float[] out = new float[samples.length];
            double avgAbs = 0.0;
            for (float sample : samples) avgAbs += Math.abs(sample);
            avgAbs = avgAbs / Math.max(1, samples.length);
            double gate = Math.max(0.004, avgAbs * 0.55);

            for (int start = 0; start < samples.length; start += window) {
                int end = Math.min(samples.length, start + window);
                double rms = 0.0;
                for (int i = start; i < end; i++) rms += samples[i] * samples[i];
                rms = Math.sqrt(rms / Math.max(1, end - start));

                float gain;
                if (rms < gate * 0.8) {
                    gain = 1.1f;
                } else if (rms < 0.025) {
                    gain = 6.0f;
                } else if (rms < 0.06) {
                    gain = 3.5f;
                } else if (rms < 0.12) {
                    gain = 2.0f;
                } else {
                    gain = 1.1f;
                }

                for (int i = start; i < end; i++) {
                    float sample = samples[i];
                    if (Math.abs(sample) < gate) sample *= 0.45f;
                    out[i] = sample * gain;
                }
            }
            return out;
        }
    }
}
