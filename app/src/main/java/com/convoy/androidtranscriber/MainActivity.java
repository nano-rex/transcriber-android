package com.convoy.androidtranscriber;

import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.convoy.androidtranscriber.asr.Whisper;
import com.convoy.androidtranscriber.util.AppSettings;
import com.convoy.androidtranscriber.util.AssetUtils;
import com.convoy.androidtranscriber.util.AudioImportUtil;
import com.convoy.androidtranscriber.util.DiarizationUtils;
import com.convoy.androidtranscriber.util.ModelUtils;
import com.convoy.androidtranscriber.util.ModelUtils.HardwareAssessment;
import com.convoy.androidtranscriber.util.ModelUtils.ModelSpec;
import com.convoy.androidtranscriber.util.StorageUtils;
import com.convoy.androidtranscriber.util.SummaryUtils;
import com.convoy.androidtranscriber.util.WaveUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String DEFAULT_SAMPLE_ASSET = "audio/jfk.wav";

    private TextView tvModelRecommendation;
    private TextView tvSelectedFile;
    private TextView tvStatus;
    private Spinner spinnerModel;
    private Button btnViewResults;
    private Button btnTranscribe;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Whisper whisper;
    private File currentImportedWav;
    private String recommendedTier;
    private ModelSpec selectedModel;
    private final List<ModelSpec> availableModels = new ArrayList<>();
    private long startTimeMs;
    private String latestTranscript = "";
    private String latestDiarized = "";
    private String latestSummary = "";
    private int defaultStatusColor;
    private final Runnable transcriptionProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (!whisper.isInProgress()) return;
            long elapsed = System.currentTimeMillis() - startTimeMs;
            int percent = estimateTranscriptionPercent(elapsed);
            tvStatus.setText(String.format(Locale.US, "Status: transcribing... %d%%", percent));
            handler.postDelayed(this, 700);
        }
    };

    private final ActivityResultLauncher<String[]> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onMediaPicked);
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvModelRecommendation = findViewById(R.id.tvModelRecommendation);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvStatus = findViewById(R.id.tvStatus);
        defaultStatusColor = tvStatus.getCurrentTextColor();
        spinnerModel = findViewById(R.id.spinnerModel);
        btnViewResults = findViewById(R.id.btnViewResults);
        Button btnPickFile = findViewById(R.id.btnPickFile);
        btnTranscribe = findViewById(R.id.btnTranscribe);
        Button btnSavedOutputs = findViewById(R.id.btnSavedOutputs);
        Button btnManageModels = findViewById(R.id.btnManageModels);
        Button btnSettings = findViewById(R.id.btnSettings);

        whisper = new Whisper(this);
        whisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                handler.post(() -> {
                    if (Whisper.MSG_PROCESSING_DONE.equals(message)) {
                        handler.removeCallbacks(transcriptionProgressUpdater);
                        long elapsed = System.currentTimeMillis() - startTimeMs;
                        tvStatus.setText("Status: processing done in " + elapsed + " ms (100%)");
                    } else {
                        tvStatus.setText("Status: " + message);
                    }
                });
            }

            @Override
            public void onResultReceived(String result) {
                handler.post(() -> {
                    String safeResult = result == null ? "" : result.trim();
                    String diarizedText = buildDiarizedText(safeResult);
                    String summaryText = SummaryUtils.buildSummaryReport(safeResult);
                    latestTranscript = safeResult;
                    latestDiarized = diarizedText;
                    latestSummary = summaryText;
                    btnViewResults.setEnabled(true);
                    writeOutputsIfPossible(safeResult, diarizedText);
                    setStatusNormal();
                    openResultsWindow();
                });
            }
        });

        recommendedTier = ModelUtils.recommendModelTier(this);
        tvModelRecommendation.setText(buildRecommendationText(recommendedTier));
        setupModelSpinner();
        ensureBundledSampleReady();

        btnPickFile.setOnClickListener(v -> pickMediaLauncher.launch(new String[]{"audio/*", "video/*"}));
        btnTranscribe.setOnClickListener(v -> startTranscription());
        btnSavedOutputs.setOnClickListener(v -> startActivity(new Intent(this, SavedOutputsActivity.class)));
        btnManageModels.setOnClickListener(v -> startActivity(new Intent(this, ManageModelsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnViewResults.setOnClickListener(v -> openResultsWindow());
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                refreshHardwareStatus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupModelSpinner();
    }

    private void setupModelSpinner() {
        availableModels.clear();
        availableModels.addAll(ModelUtils.availableModels(this));
        if (availableModels.isEmpty()) {
            availableModels.add(new ModelSpec(ModelUtils.TINY, ModelUtils.TINY, "models/whisper-tiny.tflite", true, true));
        }

        List<String> labels = new ArrayList<>();
        int recommendedIndex = 0;
        for (int i = 0; i < availableModels.size(); i++) {
            ModelSpec spec = availableModels.get(i);
            labels.add(spec.label + (spec.multilingual ? " [multilingual]" : " [english-only]"));
            if (spec.tierHint().equals(recommendedTier)) recommendedIndex = i;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(adapter);
        spinnerModel.setSelection(Math.max(0, Math.min(recommendedIndex, labels.size() - 1)));
        refreshHardwareStatus();
    }

    private String buildRecommendationText(String tier) {
        HardwareAssessment assessment = ModelUtils.assessHardware(this, tier);
        return String.format(Locale.US,
                "Recommended model: %s (%d threads, %.1f/%.1f GB free RAM, load %.2f)",
                tier, assessment.cpuThreads, assessment.availRamGb, assessment.totalRamGb, assessment.systemLoad);
    }

    private void ensureBundledSampleReady() {
        try {
            File sampleOut = new File(new File(getFilesDir(), "imports"), "jfk.wav");
            AssetUtils.copyAssetToFile(this, DEFAULT_SAMPLE_ASSET, sampleOut);
            currentImportedWav = sampleOut;
            tvSelectedFile.setText("Selected file: bundled sample jfk.wav");
        } catch (IOException e) {
            setStatusWarning();
            tvStatus.setText("Status: failed to prepare bundled sample: " + e.getMessage());
        }
    }

    private void onMediaPicked(Uri uri) {
        if (uri == null) return;
        tvStatus.setText("Status: importing media...");
        setStatusNormal();
        new Thread(() -> {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }

            try {
                AudioImportUtil.ImportedAudio imported = AudioImportUtil.importToWav(this, uri, (percent, stage) ->
                        handler.post(() -> tvStatus.setText("Status: importing media... " + percent + "% (" + stage + ")")));
                currentImportedWav = imported.wavFile;
                handler.post(() -> {
                    tvSelectedFile.setText("Selected file: " + imported.displayName + "\nEnhanced WAV: " + imported.wavFile.getAbsolutePath());
                    tvStatus.setText("Status: media imported and normalized to WAV (100%)");
                });
            } catch (IOException e) {
                handler.post(this::setStatusWarning);
                handler.post(() -> tvStatus.setText("Status: import failed - " + e.getMessage()));
            }
        }).start();
    }

    private void startTranscription() {
        if (currentImportedWav == null || !currentImportedWav.exists()) {
            tvStatus.setText("Status: pick a media file first");
            return;
        }
        int modelIndex = spinnerModel.getSelectedItemPosition();
        if (modelIndex < 0 || modelIndex >= availableModels.size()) {
            tvStatus.setText("Status: pick a model first");
            return;
        }
        selectedModel = availableModels.get(modelIndex);
        HardwareAssessment assessment = ModelUtils.assessHardware(this, selectedModel.tierHint());
        startTimeMs = System.currentTimeMillis();
        latestTranscript = "";
        latestDiarized = "";
        latestSummary = "";
        btnViewResults.setEnabled(false);
        if (assessment.canRun) {
            setStatusNormal();
            tvStatus.setText("Status: preparing model...");
        } else {
            setStatusWarning();
            tvStatus.setText("Status: warning - " + assessment.message);
        }

        new Thread(() -> {
            List<File> wavParts = null;
            try {
                File modelFile = ensureModelFile(selectedModel);
                File vocabFile = ensureVocabFile(selectedModel);
                whisper.unloadModel();
                whisper.loadModel(modelFile.getAbsolutePath(), vocabFile.getAbsolutePath(), selectedModel.multilingual);
                wavParts = AudioImportUtil.splitWavForTranscription(this, currentImportedWav);
                handler.post(transcriptionProgressUpdater);
                String result = transcribeWavParts(wavParts);
                handler.removeCallbacks(transcriptionProgressUpdater);
                long elapsed = System.currentTimeMillis() - startTimeMs;
                handler.post(() -> {
                    tvStatus.setText("Status: processing done in " + elapsed + " ms (100%)");
                    String safeResult = result == null ? "" : result.trim();
                    String diarizedText = buildDiarizedText(safeResult);
                    String summaryText = SummaryUtils.buildSummaryReport(safeResult);
                    latestTranscript = safeResult;
                    latestDiarized = diarizedText;
                    latestSummary = summaryText;
                    btnViewResults.setEnabled(true);
                    writeOutputsIfPossible(safeResult, diarizedText);
                    setStatusNormal();
                    openResultsWindow();
                });
            } catch (Exception e) {
                handler.removeCallbacks(transcriptionProgressUpdater);
                handler.post(this::setStatusWarning);
                handler.post(() -> tvStatus.setText("Status: failed to start transcription - " + e.getMessage()));
            } finally {
                AudioImportUtil.cleanupSplitWavParts(currentImportedWav, wavParts);
            }
        }).start();
    }

    private String transcribeWavParts(List<File> wavParts) {
        if (wavParts == null || wavParts.isEmpty()) {
            return whisper.transcribeBlocking(currentImportedWav.getAbsolutePath());
        }

        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < wavParts.size(); i++) {
            File part = wavParts.get(i);
            int partIndex = i + 1;
            int basePercent = (int) Math.round((i * 100.0) / wavParts.size());
            handler.post(() -> tvStatus.setText(String.format(Locale.US,
                    "Status: transcribing part %d/%d... %d%%",
                    partIndex, wavParts.size(), Math.min(95, basePercent))));
            String partText = whisper.transcribeBlocking(part.getAbsolutePath());
            if (partText != null) {
                partText = partText.trim();
                if (!partText.isEmpty()) {
                    if (merged.length() > 0) merged.append(' ');
                    merged.append(partText);
                }
            }
        }
        return merged.toString();
    }

    private File ensureModelFile(ModelSpec spec) throws IOException {
        if (spec.bundled) {
            File outFile = new File(new File(getFilesDir(), "models"), new File(spec.assetPath).getName());
            return AssetUtils.copyAssetToFile(this, spec.assetPath, outFile);
        }
        File file = new File(spec.assetPath);
        if (!file.exists()) throw new IOException("Model file not found: " + file.getName());
        return file;
    }

    private File ensureVocabFile(ModelSpec spec) throws IOException {
        String assetPath = ModelUtils.vocabAssetForModel(spec);
        File outFile = new File(new File(getFilesDir(), "vocab"), new File(assetPath).getName());
        return AssetUtils.copyAssetToFile(this, assetPath, outFile);
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
            File outputsDir = StorageUtils.outputsDir(this);
            if (!outputsDir.exists()) outputsDir.mkdirs();
            String base = currentImportedWav.getName();
            if (base.endsWith(".wav")) base = base.substring(0, base.length() - 4);
            float[] samples = WaveUtil.getSamples(currentImportedWav.getAbsolutePath());
            List<DiarizationUtils.TextSegment> segments = DiarizationUtils.buildTextSegments(transcript, samples.length);
            String timestampedTranscript = DiarizationUtils.buildTimestampedTranscript(segments);
            File enhancedOutput = new File(outputsDir, base + ".enhanced.wav");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Files.copy(currentImportedWav.toPath(), enhancedOutput.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                copyFileCompat(currentImportedWav, enhancedOutput);
            }
            writeTextFile(new File(outputsDir, base + ".transcript.txt"), timestampedTranscript);
            writeTextFile(new File(outputsDir, base + ".summary.txt"), SummaryUtils.buildSummaryReport(transcript));
            writeTextFile(new File(outputsDir, base + ".diarized.txt"), diarizedText == null ? "" : diarizedText);
            writeMetadataFile(new File(outputsDir, base + ".meta.json"), transcript, diarizedText, samples.length);
        } catch (Exception e) {
            setStatusWarning();
            tvStatus.setText("Status: transcript done, but failed to write outputs: " + e.getMessage());
        }
    }

    private void writeTextFile(File file, String content) throws IOException {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            fos.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void openResultsWindow() {
        Intent intent = new Intent(this, ResultsActivity.class);
        intent.putExtra(ResultsActivity.EXTRA_TITLE, currentImportedWav == null ? "Latest Result" : currentImportedWav.getName());
        intent.putExtra(ResultsActivity.EXTRA_TRANSCRIPT, latestTranscript);
        intent.putExtra(ResultsActivity.EXTRA_DIARIZED, latestDiarized);
        intent.putExtra(ResultsActivity.EXTRA_SUMMARY, latestSummary);
        startActivity(intent);
    }

    private void refreshHardwareStatus() {
        int modelIndex = spinnerModel.getSelectedItemPosition();
        if (modelIndex < 0 || modelIndex >= availableModels.size()) {
            btnTranscribe.setEnabled(true);
            return;
        }
        ModelSpec spec = availableModels.get(modelIndex);
        HardwareAssessment assessment = ModelUtils.assessHardware(this, spec.tierHint());
        if (assessment.canRun) {
            setStatusNormal();
            tvStatus.setText(String.format(Locale.US,
                    "Status: ready. Using %.1f GB RAM, %.1f GB free, model needs %.1f GB",
                    assessment.usedRamGb, assessment.availRamGb, assessment.estimatedModelRamGb));
        } else {
            setStatusWarning();
            tvStatus.setText("Status: warning - " + assessment.message);
        }
    }

    private int estimateTranscriptionPercent(long elapsedMs) {
        if (currentImportedWav == null || selectedModel == null) return 5;
        int sampleCount = WaveUtil.getSamples(currentImportedWav.getAbsolutePath()).length;
        double durationSeconds = sampleCount / 16000.0;
        double factor = estimatedRealtimeFactor(selectedModel.tierHint());
        long estimatedTotalMs = Math.max(6000L, Math.round(durationSeconds * factor * 1000.0));
        int percent = (int) Math.min(95, Math.max(1, (elapsedMs * 100) / estimatedTotalMs));
        return percent;
    }

    private double estimatedRealtimeFactor(String tier) {
        String normalized = tier == null ? "" : tier.toLowerCase(Locale.US);
        if (normalized.contains("medium")) return 2.8;
        if (normalized.contains("small")) return 1.7;
        return 1.1;
    }

    private void setStatusWarning() {
        tvStatus.setTextColor(Color.RED);
    }

    private void setStatusNormal() {
        tvStatus.setTextColor(defaultStatusColor);
    }

    private void copyFileCompat(File source, File target) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(source);
             java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, read);
            }
        }
    }

    private void writeMetadataFile(File file, String transcript, String diarizedText, int sampleCount) throws IOException {
        double durationSeconds = sampleCount / 16000.0;
        String json = "{\n"
                + "  \"input\": \"" + escapeJson(currentImportedWav.getAbsolutePath()) + "\",\n"
                + "  \"model\": \"" + escapeJson(selectedModel == null ? "" : selectedModel.label) + "\",\n"
                + "  \"duration_seconds\": " + String.format(Locale.US, "%.2f", durationSeconds) + ",\n"
                + "  \"transcript_chars\": " + (transcript == null ? 0 : transcript.length()) + ",\n"
                + "  \"diarized_chars\": " + (diarizedText == null ? 0 : diarizedText.length()) + "\n"
                + "}\n";
        writeTextFile(file, json);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(transcriptionProgressUpdater);
    }
}
