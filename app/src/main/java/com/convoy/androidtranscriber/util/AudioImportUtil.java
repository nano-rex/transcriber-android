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
import java.util.Locale;

public final class AudioImportUtil {
    private static final String TAG = "AudioImportUtil";
    private static final long TIMEOUT_US = 10000;

    private AudioImportUtil() {}

    public static ImportedAudio importToWav(Context context, Uri uri) throws IOException {
        String displayName = queryDisplayName(context, uri);
        if (displayName == null) displayName = "imported_audio";

        File importsDir = new File(context.getFilesDir(), "imports");
        if (!importsDir.exists()) importsDir.mkdirs();

        String lower = displayName.toLowerCase(Locale.US);
        File outFile = new File(importsDir, sanitizeBaseName(displayName) + ".wav");

        if (lower.endsWith(".wav")) {
            File rawCopy = new File(importsDir, sanitizeBaseName(displayName) + ".source.wav");
            copyUriToFile(context, uri, rawCopy);
            normalizeWavTo16kMono(rawCopy, outFile);
            return new ImportedAudio(displayName, outFile);
        }

        decodeMediaTo16kMonoWav(context, uri, outFile);
        return new ImportedAudio(displayName, outFile);
    }

    private static void copyUriToFile(Context context, Uri uri, File target) throws IOException {
        try (var in = context.getContentResolver().openInputStream(uri);
             var out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Unable to open input stream");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void normalizeWavTo16kMono(File source, File target) throws IOException {
        float[] sourceSamples = WaveUtil.getSamples(source.getAbsolutePath());
        if (sourceSamples.length == 0) throw new IOException("Unable to decode wav samples");
        short[] pcm16 = floatToPcm16(resampleLinear(sourceSamples, 16000, 16000));
        WaveUtil.createWaveFile(target.getAbsolutePath(), shortsToBytes(pcm16), 16000, 1, 2);
    }

    private static void decodeMediaTo16kMonoWav(Context context, Uri uri, File outFile) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
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
            float[] resampled = resampleLinear(monoFloat, sourceSampleRate, 16000);
            short[] finalPcm = floatToPcm16(resampled);
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

    public static class ImportedAudio {
        public final String displayName;
        public final File wavFile;

        public ImportedAudio(String displayName, File wavFile) {
            this.displayName = displayName;
            this.wavFile = wavFile;
        }
    }
}
