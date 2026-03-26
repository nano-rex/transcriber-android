package com.convoy.androidtranscriber;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ResultsActivity extends AppCompatActivity {
    public static final String EXTRA_TRANSCRIPT = "extra_transcript";
    public static final String EXTRA_DIARIZED = "extra_diarized";
    public static final String EXTRA_SUMMARY = "extra_summary";

    private TextView tvContent;
    private String transcriptText = "";
    private String diarizedText = "";
    private String summaryText = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        transcriptText = valueOrEmpty(getIntent().getStringExtra(EXTRA_TRANSCRIPT));
        diarizedText = valueOrEmpty(getIntent().getStringExtra(EXTRA_DIARIZED));
        summaryText = valueOrEmpty(getIntent().getStringExtra(EXTRA_SUMMARY));

        Button btnTranscript = findViewById(R.id.btnResultsTranscript);
        Button btnDiarized = findViewById(R.id.btnResultsDiarized);
        Button btnSummary = findViewById(R.id.btnResultsSummary);
        tvContent = findViewById(R.id.tvResultsContent);

        btnTranscript.setOnClickListener(v -> showSection("Transcript", transcriptText));
        btnDiarized.setOnClickListener(v -> showSection("Diarized Transcript", diarizedText));
        btnSummary.setOnClickListener(v -> showSection("Overview + Key Points", summaryText));

        if (!transcriptText.isEmpty()) {
            showSection("Transcript", transcriptText);
        } else if (!diarizedText.isEmpty()) {
            showSection("Diarized Transcript", diarizedText);
        } else {
            showSection("Overview + Key Points", summaryText);
        }
    }

    private void showSection(String title, String content) {
        setTitle(title);
        tvContent.setVisibility(View.VISIBLE);
        tvContent.setText(content.isEmpty() ? "No content available." : content);
    }

    private String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
