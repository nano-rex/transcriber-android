package com.convoy.androidtranscriber;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.convoy.androidtranscriber.util.ModelUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageModelsActivity extends AppCompatActivity {
    private EditText etSearch;
    private TextView tvStatus;
    private ListView listModels;

    private final List<ModelRow> allRows = new ArrayList<>();
    private final List<ModelRow> filteredRows = new ArrayList<>();
    private ModelListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_models);

        etSearch = findViewById(R.id.etSearchModels);
        tvStatus = findViewById(R.id.tvModelStatus);
        listModels = findViewById(R.id.listModels);

        adapter = new ModelListAdapter(this, filteredRows, this::handleRowAction);
        listModels.setAdapter(adapter);

        etSearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
        });

        loadRows();
    }

    private void loadRows() {
        allRows.clear();
        allRows.add(buildBundledRow("tiny-en", "ASR", "models/ggml-tiny.en.bin", false));
        allRows.add(buildBundledRow("tiny", "ASR", "models/ggml-tiny.bin", true));
        allRows.add(buildSummaryRulesRow());

        applyFilter(etSearch.getText() == null ? "" : etSearch.getText().toString());
    }

    private void applyFilter(String query) {
        String normalized = query.trim().toLowerCase(Locale.US);
        filteredRows.clear();
        for (ModelRow row : allRows) {
            if (normalized.isEmpty() || row.displayName.toLowerCase(Locale.US).contains(normalized)) {
                filteredRows.add(row);
            }
        }
        adapter.notifyDataSetChanged();
        tvStatus.setText(filteredRows.isEmpty() ? "No models found." : "Models listed: " + filteredRows.size());
    }

    private ModelRow buildBundledRow(String displayName, String category, String assetPath, boolean multilingual) {
        boolean available = assetExists(assetPath);
        return new ModelRow(displayName, category, available ? "Bundled" : "Missing bundled asset", multilingual,
                assetPath, available, true, false, available ? "Bundled" : "Missing", false, null);
    }

    private ModelRow buildSummaryRulesRow() {
        return new ModelRow("summary-rules", "Summary", "Bundled heuristic summarizer", true,
                "built-in", true, true, false, "Bundled", false, null);
    }

    private void handleRowAction(ModelRow row) {
        if (!row.actionEnabled) {
            tvStatus.setText(row.displayName + " is already built in.");
            return;
        }
        if (row.customFile) {
            confirmRemove(row);
            return;
        }
    }

    private void confirmRemove(ModelRow row) {
        new AlertDialog.Builder(this)
                .setTitle("Remove model")
                .setMessage("Remove " + row.displayName + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    File file = new File(row.location);
                    boolean deleted = !file.exists() || file.delete();
                    tvStatus.setText(deleted ? "Removed " + row.displayName : "Failed to remove " + row.displayName);
                    loadRows();
                })
                .show();
    }

    private boolean assetExists(String path) {
        try {
            getAssets().open(path).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static final class ModelRow {
        public final String displayName;
        public final String category;
        public final String state;
        public final boolean multilingual;
        public final String location;
        public final boolean available;
        public final boolean bundled;
        public final boolean customFile;
        public final String actionLabel;
        public final boolean actionEnabled;
        public final String downloadUrl;

        public ModelRow(String displayName, String category, String state, boolean multilingual, String location,
                        boolean available, boolean bundled, boolean customFile, String actionLabel,
                        boolean actionEnabled, String downloadUrl) {
            this.displayName = displayName;
            this.category = category;
            this.state = state;
            this.multilingual = multilingual;
            this.location = location;
            this.available = available;
            this.bundled = bundled;
            this.customFile = customFile;
            this.actionLabel = actionLabel;
            this.actionEnabled = actionEnabled;
            this.downloadUrl = downloadUrl;
        }

        public String statusLine() {
            return category + " | " + state + " | " + (multilingual ? "multilingual" : "english-only")
                    + " | " + (bundled ? "built-in" : "custom");
        }
    }
}
