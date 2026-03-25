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
            WaveUtil.createWaveFile(outWavFile.getAbsolutePath(), shortsToBytes(pcm16), SAMPLE_RATE, 1, 2);
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
    }
}
