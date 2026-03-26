package com.convoy.androidtranscriber.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AudioImportUtil {
    private static final String TAG = "AudioImportUtil";
    private static final long TIMEOUT_US = 10000;
    public static final int TRANSCRIBE_CHUNK_SECONDS = 300;

    private AudioImportUtil() {}

    public interface ProgressListener {
        void onProgress(int percent, String stage);
    }

    public static ImportedAudio importToWav(Context context, Uri uri) throws IOException {
        return importToWav(context, uri, null);
    }

    public static ImportedAudio importToWav(Context context, Uri uri, ProgressListener listener) throws IOException {
        String displayName = queryDisplayName(context, uri);
        if (displayName == null) displayName = "imported_audio";
        notifyProgress(listener, 5, "preparing import");

        File importsDir = new File(context.getFilesDir(), "imports");
        if (!importsDir.exists()) importsDir.mkdirs();

        String lower = displayName.toLowerCase(Locale.US);
        File outFile = new File(importsDir, sanitizeBaseName(displayName) + ".wav");

        if (lower.endsWith(".wav")) {
            File rawCopy = new File(importsDir, sanitizeBaseName(displayName) + ".source.wav");
            copyUriToFile(context, uri, rawCopy, listener);
            normalizeWavTo16kMono(rawCopy, outFile, listener);
            notifyProgress(listener, 100, "import complete");
            return new ImportedAudio(displayName, outFile);
        }

        decodeMediaTo16kMonoWav(context, uri, outFile, listener);
        notifyProgress(listener, 100, "import complete");
        return new ImportedAudio(displayName, outFile);
    }

    private static void copyUriToFile(Context context, Uri uri, File target, ProgressListener listener) throws IOException {
        long totalBytes = -1L;
        try (var descriptor = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (descriptor != null) totalBytes = descriptor.getLength();
        } catch (Exception ignored) {
        }
        try (var in = context.getContentResolver().openInputStream(uri);
             var out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Unable to open input stream");
            byte[] buffer = new byte[8192];
            int read;
            long copied = 0L;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                copied += read;
                if (totalBytes > 0) {
                    int percent = 5 + (int) Math.min(45, (copied * 45) / totalBytes);
                    notifyProgress(listener, percent, "copying media");
                }
            }
        }
    }

    private static void normalizeWavTo16kMono(File source, File target, ProgressListener listener) throws IOException {
        notifyProgress(listener, 60, "enhancing audio");
        float[] sourceSamples = WaveUtil.getSamples(source.getAbsolutePath());
        if (sourceSamples.length == 0) throw new IOException("Unable to decode wav samples");
        short[] pcm16 = floatToPcm16(enhanceForSpeech(resampleLinear(sourceSamples, 16000, 16000)));
        notifyProgress(listener, 90, "writing enhanced wav");
        WaveUtil.createWaveFile(target.getAbsolutePath(), shortsToBytes(pcm16), 16000, 1, 2);
    }

    private static void decodeMediaTo16kMonoWav(Context context, Uri uri, File outFile, ProgressListener listener) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            notifyProgress(listener, 10, "reading media");
            extractor.setDataSource(context, uri, null);
            int audioTrack = selectAudioTrack(extractor);
            if (audioTrack < 0) throw new IOException("No audio track found in selected media");
            extractor.selectTrack(audioTrack);

            MediaFormat format = extractor.getTrackFormat(audioTrack);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("Audio mime type missing");

            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int sourceSampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 16000;
            int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int decodePercent = 15;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                        if (inputBuffer != null) {
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                long presentationTimeUs = extractor.getSampleTime();
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        byte[] chunk = new byte[info.size];
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        outputBuffer.get(chunk);
                        pcmOut.write(chunk);
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                    if (decodePercent < 70) {
                        decodePercent += 2;
                        notifyProgress(listener, decodePercent, "decoding media");
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            codec.stop();
            codec.release();

            short[] decoded = bytesToShorts(pcmOut.toByteArray());
            short[] mono = downmixToMono(decoded, channelCount);
            float[] monoFloat = pcm16ToFloat(mono);
            notifyProgress(listener, 75, "enhancing audio");
            float[] resampled = enhanceForSpeech(resampleLinear(monoFloat, sourceSampleRate, 16000));
            short[] finalPcm = floatToPcm16(resampled);
            notifyProgress(listener, 92, "writing enhanced wav");
            WaveUtil.createWaveFile(outFile.getAbsolutePath(), shortsToBytes(finalPcm), 16000, 1, 2);
        } catch (Exception e) {
            throw new IOException("Failed to import media: " + e.getMessage(), e);
        } finally {
            extractor.release();
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private static String queryDisplayName(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) return cursor.getString(nameIndex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to query display name", e);
        }
        return null;
    }

    private static String sanitizeBaseName(String displayName) {
        String base = displayName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static short[] bytesToShorts(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        short[] shorts = new short[bytes.length / 2];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = buffer.getShort();
        }
        return shorts;
    }

    private static byte[] shortsToBytes(short[] shorts) {
        ByteBuffer buffer = ByteBuffer.allocate(shorts.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : shorts) buffer.putShort(s);
        return buffer.array();
    }

    private static short[] downmixToMono(short[] pcm, int channelCount) {
        if (channelCount <= 1) return pcm;
        int frames = pcm.length / channelCount;
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channelCount; c++) {
                sum += pcm[i * channelCount + c];
            }
            mono[i] = (short) (sum / channelCount);
        }
        return mono;
    }

    private static float[] pcm16ToFloat(short[] pcm) {
        float[] out = new float[pcm.length];
        for (int i = 0; i < pcm.length; i++) out[i] = pcm[i] / 32768f;
        return out;
    }

    private static short[] floatToPcm16(float[] samples) {
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float clamped = Math.max(-1f, Math.min(1f, samples[i]));
            out[i] = (short) (clamped * 32767f);
        }
        return out;
    }

    private static float[] resampleLinear(float[] input, int sourceRate, int targetRate) {
        if (sourceRate <= 0 || sourceRate == targetRate) return input;
        int outputLength = (int) Math.max(1, Math.round(input.length * (targetRate / (double) sourceRate)));
        float[] output = new float[outputLength];
        double ratio = sourceRate / (double) targetRate;
        for (int i = 0; i < outputLength; i++) {
            double srcIndex = i * ratio;
            int left = (int) Math.floor(srcIndex);
            int right = Math.min(left + 1, input.length - 1);
            double frac = srcIndex - left;
            output[i] = (float) ((1.0 - frac) * input[left] + frac * input[right]);
        }
        return output;
    }

    private static float[] enhanceForSpeech(float[] input) {
        if (input.length == 0) return input;

        float[] filtered = new float[input.length];
        float previousInput = 0f;
        float previousOutput = 0f;
        for (int i = 0; i < input.length; i++) {
            float current = input[i];
            float highPass = (float) (0.97 * (previousOutput + current - previousInput));
            filtered[i] = highPass;
            previousInput = current;
            previousOutput = highPass;
        }

        float[] leveled = applyAdaptiveSpeechGain(filtered);
        return softLimit(leveled);
    }

    private static float[] applyAdaptiveSpeechGain(float[] input) {
        int window = 1600;
        float[] output = new float[input.length];
        double avgAbs = 0.0;
        for (float sample : input) avgAbs += Math.abs(sample);
        avgAbs = avgAbs / Math.max(1, input.length);
        double gate = Math.max(0.004, avgAbs * 0.55);

        for (int start = 0; start < input.length; start += window) {
            int end = Math.min(input.length, start + window);
            double rms = 0.0;
            for (int i = start; i < end; i++) {
                rms += input[i] * input[i];
            }
            rms = Math.sqrt(rms / Math.max(1, end - start));

            float gain;
            if (rms < gate * 0.8) {
                gain = 1.1f;
            } else if (rms < 0.025) {
                gain = 6.5f;
            } else if (rms < 0.06) {
                gain = 3.8f;
            } else if (rms < 0.12) {
                gain = 2.2f;
            } else {
                gain = 1.2f;
            }

            for (int i = start; i < end; i++) {
                float sample = input[i];
                if (Math.abs(sample) < gate) {
                    sample *= 0.45f;
                }
                output[i] = sample * gain;
            }
        }

        return output;
    }

    private static float[] softLimit(float[] input) {
        float peak = 0f;
        for (float sample : input) peak = Math.max(peak, Math.abs(sample));
        float preGain = peak > 0f ? Math.min(10f, 0.96f / peak) : 1f;
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            float sample = input[i] * preGain;
            output[i] = (float) Math.tanh(sample * 1.4f) / 1.05f;
        }
        return output;
    }

    private static void notifyProgress(ProgressListener listener, int percent, String stage) {
        if (listener != null) {
            listener.onProgress(Math.max(0, Math.min(100, percent)), stage);
        }
    }

    public static class ImportedAudio {
        public final String displayName;
        public final File wavFile;

        public ImportedAudio(String displayName, File wavFile) {
            this.displayName = displayName;
            this.wavFile = wavFile;
        }
    }

    public static List<File> splitWavForTranscription(Context context, File wavFile) throws IOException {
        List<File> chunks = new ArrayList<>();
        float[] samples = WaveUtil.getSamples(wavFile.getAbsolutePath());
        if (samples.length == 0) {
            chunks.add(wavFile);
            return chunks;
        }

        int maxSamplesPerChunk = 16000 * TRANSCRIBE_CHUNK_SECONDS;
        if (samples.length <= maxSamplesPerChunk) {
            chunks.add(wavFile);
            return chunks;
        }

        File chunkDir = new File(context.getFilesDir(), "transcribe-chunks");
        if (!chunkDir.exists()) chunkDir.mkdirs();
        clearOldChunks(chunkDir);

        String base = wavFile.getName();
        if (base.toLowerCase(Locale.US).endsWith(".wav")) {
            base = base.substring(0, base.length() - 4);
        }

        int chunkCount = (int) Math.ceil(samples.length / (double) maxSamplesPerChunk);
        for (int i = 0; i < chunkCount; i++) {
            int start = i * maxSamplesPerChunk;
            int end = Math.min(samples.length, start + maxSamplesPerChunk);
            int len = Math.max(0, end - start);
            if (len <= 0) break;
            float[] chunkSamples = new float[len];
            System.arraycopy(samples, start, chunkSamples, 0, len);
            File chunkFile = new File(chunkDir, base + ".part-" + String.format(Locale.US, "%02d", i + 1) + ".wav");
            WaveUtil.createWaveFile(chunkFile.getAbsolutePath(), shortsToBytes(floatToPcm16(chunkSamples)), 16000, 1, 2);
            chunks.add(chunkFile);
        }

        return chunks;
    }

    private static void clearOldChunks(File chunkDir) {
        File[] files = chunkDir.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".wav"));
        if (files == null) return;
        for (File file : files) {
            if (file != null && file.exists()) {
                // Best-effort cleanup of prior temporary chunk files.
                file.delete();
            }
        }
    }
}
