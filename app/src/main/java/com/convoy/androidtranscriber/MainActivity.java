package com.convoy.androidtranscriber;

import android.app.ActivityManager;
import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.convoy.androidtranscriber.asr.Whisper;
import com.convoy.androidtranscriber.util.AssetUtils;
import com.convoy.androidtranscriber.util.AudioImportUtil;
import com.convoy.androidtranscriber.util.DiarizationUtils;
import com.convoy.androidtranscriber.util.ModelUtils;
import com.convoy.androidtranscriber.util.RecorderUtil;
import com.convoy.androidtranscriber.util.SummaryUtils;
import com.convoy.androidtranscriber.util.WaveUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String DEFAULT_SAMPLE_ASSET = "audio/jfk.wav";

    private TextView tvModelRecommendation;
    private TextView tvSelectedFile;
    private TextView tvStatus;
    private TextView tvTranscript;
    private TextView tvDiarized;
    private TextView tvSummary;
    private Spinner spinnerModel;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Whisper whisper;
    private File currentImportedWav;
    private RecorderUtil.RecorderSession recorderSession;
    private boolean isRecording = false;
    private String recommendedTier;
    private long startTimeMs;

    private final ActivityResultLauncher<String[]> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onMediaPicked);
    private final ActivityResultLauncher<String> requestRecordPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecording();
                } else {
                    tvStatus.setText("Status: microphone permission denied");
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvModelRecommendation = findViewById(R.id.tvModelRecommendation);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvStatus = findViewById(R.id.tvStatus);
        tvTranscript = findViewById(R.id.tvTranscript);
        tvDiarized = findViewById(R.id.tvDiarized);
        tvSummary = findViewById(R.id.tvSummary);
        spinnerModel = findViewById(R.id.spinnerModel);
        Button btnPickFile = findViewById(R.id.btnPickFile);
        Button btnRecord = findViewById(R.id.btnRecord);
        Button btnTranscribe = findViewById(R.id.btnTranscribe);

        whisper = new Whisper(this);
        whisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                handler.post(() -> {
                    if (Whisper.MSG_PROCESSING_DONE.equals(message)) {
                        long elapsed = System.currentTimeMillis() - startTimeMs;
                        tvStatus.setText("Status: processing done in " + elapsed + " ms");
                    } else {
                        tvStatus.setText("Status: " + message);
                    }
                });
            }

            @Override
            public void onResultReceived(String result) {
                handler.post(() -> {
                    String safeResult = result == null ? "" : result.trim();
                    tvTranscript.setText(safeResult);
                    String diarizedText = buildDiarizedText(safeResult);
                    tvDiarized.setText(diarizedText);
                    tvSummary.setText(SummaryUtils.buildSummaryReport(safeResult));
                    writeOutputsIfPossible(safeResult, diarizedText);
                });
            }
        });

        recommendedTier = ModelUtils.recommendModelTier(this);
        tvModelRecommendation.setText(buildRecommendationText(recommendedTier));
        setupModelSpinner(recommendedTier);
        ensureBundledSampleReady();

        btnPickFile.setOnClickListener(v -> pickMediaLauncher.launch(new String[]{"audio/*", "video/*"}));
        btnRecord.setOnClickListener(v -> toggleRecording(btnRecord));
        btnTranscribe.setOnClickListener(v -> startTranscription());
    }

    private void setupModelSpinner(String recommendedTier) {
        List<String> available = new ArrayList<>();
        for (Map.Entry<String, String> entry : ModelUtils.bundledModelAssets().entrySet()) {
            String tier = entry.getKey();
            String asset = entry.getValue();
            if (assetExists(asset)) available.add(tier);
        }
        if (available.isEmpty()) available.add(ModelUtils.TINY_EN);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, available);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(adapter);

        int recommendedIndex = Math.max(0, available.indexOf(recommendedTier));
        spinnerModel.setSelection(recommendedIndex >= 0 ? recommendedIndex : 0);
    }

    private String buildRecommendationText(String tier) {
        int cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activityManager != null) activityManager.getMemoryInfo(memoryInfo);
        double memGb = memoryInfo.totalMem / 1024.0 / 1024.0 / 1024.0;
        return String.format(Locale.US, "Recommended model: %s (%d threads, %.1f GB RAM)", tier, cpuThreads, memGb);
    }

    private boolean assetExists(String path) {
        try {
            getAssets().open(path).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void ensureBundledSampleReady() {
        try {
            File sampleOut = new File(new File(getFilesDir(), "imports"), "jfk.wav");
            AssetUtils.copyAssetToFile(this, DEFAULT_SAMPLE_ASSET, sampleOut);
            currentImportedWav = sampleOut;
            tvSelectedFile.setText("Selected file: bundled sample jfk.wav");
        } catch (IOException e) {
            tvStatus.setText("Status: failed to prepare bundled sample: " + e.getMessage());
        }
    }

    private void onMediaPicked(Uri uri) {
        if (uri == null) return;
        tvStatus.setText("Status: importing media...");
        new Thread(() -> {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }

            try {
                AudioImportUtil.ImportedAudio imported = AudioImportUtil.importToWav(this, uri);
                currentImportedWav = imported.wavFile;
                handler.post(() -> {
                    tvSelectedFile.setText("Selected file: " + imported.displayName + "\nPrepared WAV: " + imported.wavFile.getAbsolutePath());
                    tvStatus.setText("Status: media imported and normalized to WAV");
                });
            } catch (IOException e) {
                handler.post(() -> tvStatus.setText("Status: import failed - " + e.getMessage()));
            }
        }).start();
    }

    private void startTranscription() {
        if (currentImportedWav == null || !currentImportedWav.exists()) {
            tvStatus.setText("Status: pick a media file first");
            return;
        }
        String selectedTier = String.valueOf(spinnerModel.getSelectedItem());
        startTimeMs = System.currentTimeMillis();
        tvTranscript.setText("");
        tvDiarized.setText("");
        tvSummary.setText("");
        tvStatus.setText("Status: preparing model...");

        new Thread(() -> {
            try {
                File modelFile = ensureModelFile(selectedTier);
                File vocabFile = ensureVocabFile(selectedTier);
                whisper.unloadModel();
                whisper.loadModel(modelFile.getAbsolutePath(), vocabFile.getAbsolutePath(), ModelUtils.isMultilingual(selectedTier));
                whisper.setFilePath(currentImportedWav.getAbsolutePath());
                whisper.start();
            } catch (Exception e) {
                handler.post(() -> tvStatus.setText("Status: failed to start transcription - " + e.getMessage()));
            }
        }).start();
    }

    private File ensureModelFile(String tier) throws IOException {
        String assetPath = ModelUtils.bundledModelAssets().get(tier);
        if (assetPath == null) throw new IOException("Unknown model tier: " + tier);
        File outFile = new File(new File(getFilesDir(), "models"), new File(assetPath).getName());
        return AssetUtils.copyAssetToFile(this, assetPath, outFile);
    }

    private File ensureVocabFile(String tier) throws IOException {
        String assetPath = ModelUtils.vocabAssetForTier(tier);
        File outFile = new File(new File(getFilesDir(), "vocab"), new File(assetPath).getName());
        return AssetUtils.copyAssetToFile(this, assetPath, outFile);
    }

    private void toggleRecording(Button btnRecord) {
        if (isRecording) {
            stopRecording(btnRecord);
            return;
        }
        requestRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void startRecording() {
        try {
            File recordingsDir = new File(getFilesDir(), "recordings");
            if (!recordingsDir.exists()) recordingsDir.mkdirs();
            File out = new File(recordingsDir, "recording_" + System.currentTimeMillis() + ".wav");
            recorderSession = RecorderUtil.startRecording(out);
            isRecording = true;
            Button btnRecord = findViewById(R.id.btnRecord);
            btnRecord.setText("Stop recording");
            tvStatus.setText("Status: recording...");
            tvSelectedFile.setText("Selected file: recording in progress");
        } catch (Exception e) {
            tvStatus.setText("Status: failed to start recording - " + e.getMessage());
        }
    }

    private void stopRecording(Button btnRecord) {
        if (recorderSession == null) return;
        new Thread(() -> {
            try {
                File recorded = recorderSession.stopAndSave();
                currentImportedWav = recorded;
                handler.post(() -> {
                    isRecording = false;
                    recorderSession = null;
                    btnRecord.setText("Record audio");
                    tvStatus.setText("Status: recording saved");
                    tvSelectedFile.setText("Selected file: " + recorded.getAbsolutePath());
                });
            } catch (Exception e) {
                handler.post(() -> {
                    isRecording = false;
                    recorderSession = null;
                    btnRecord.setText("Record audio");
                    tvStatus.setText("Status: failed to stop recording - " + e.getMessage());
                });
            }
        }).start();
    }

    private String buildDiarizedText(String transcript) {
        if (currentImportedWav == null || !currentImportedWav.exists()) return "";
        float[] samples = WaveUtil.getSamples(currentImportedWav.getAbsolutePath());
        List<DiarizationUtils.TextSegment> segments = DiarizationUtils.buildTextSegments(transcript, samples.length);
        return DiarizationUtils.buildDiarizedTranscript(samples, segments);
    }

    private void writeOutputsIfPossible(String transcript, String diarizedText) {
        if (currentImportedWav == null) return;
        try {
            File outputsDir = new File(getFilesDir(), "outputs");
            if (!outputsDir.exists()) outputsDir.mkdirs();
            String base = currentImportedWav.getName();
            if (base.endsWith(".wav")) base = base.substring(0, base.length() - 4);
            float[] samples = WaveUtil.getSamples(currentImportedWav.getAbsolutePath());
            List<DiarizationUtils.TextSegment> segments = DiarizationUtils.buildTextSegments(transcript, samples.length);
            String timestampedTranscript = DiarizationUtils.buildTimestampedTranscript(segments);
            writeTextFile(new File(outputsDir, base + ".transcript.txt"), timestampedTranscript);
            writeTextFile(new File(outputsDir, base + ".summary.txt"), SummaryUtils.buildSummaryReport(transcript));
            writeTextFile(new File(outputsDir, base + ".diarized.txt"), diarizedText == null ? "" : diarizedText);
        } catch (Exception e) {
            tvStatus.setText("Status: transcript done, but failed to write outputs: " + e.getMessage());
        }
    }

    private void writeTextFile(File file, String content) throws IOException {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            fos.write((content == null ? "" : content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecording && recorderSession != null) {
            try {
                recorderSession.stopAndSave();
            } catch (Exception ignored) {
            } finally {
                isRecording = false;
                recorderSession = null;
            }
        }
    }
}
