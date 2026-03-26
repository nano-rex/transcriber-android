package com.convoy.androidtranscriber;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.convoy.androidtranscriber.util.ModelUtils;
import com.convoy.androidtranscriber.util.ModelUtils.ModelSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageModelsActivity extends AppCompatActivity {
    private EditText etSearch;
    private TextView tvStatus;
    private ListView listModels;
    private Button btnPrimaryAction;
    private Button btnSecondaryAction;

    private final List<ModelRow> allRows = new ArrayList<>();
    private final List<ModelRow> filteredRows = new ArrayList<>();
    private ModelListAdapter adapter;
    private int selectedIndex = -1;

    private final ActivityResultLauncher<String[]> importModelLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onModelPicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_models);

        etSearch = findViewById(R.id.etSearchModels);
        tvStatus = findViewById(R.id.tvModelStatus);
        listModels = findViewById(R.id.listModels);
        btnPrimaryAction = findViewById(R.id.btnPrimaryModelAction);
        btnSecondaryAction = findViewById(R.id.btnSecondaryModelAction);

        adapter = new ModelListAdapter(this, filteredRows);
        listModels.setAdapter(adapter);
        listModels.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        etSearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
        });

        listModels.setOnItemClickListener((parent, view, position, id) -> {
            selectedIndex = position;
            listModels.setItemChecked(position, true);
            refreshActionButtons();
        });

        btnPrimaryAction.setOnClickListener(v -> runPrimaryAction());
        btnSecondaryAction.setOnClickListener(v -> runSecondaryAction());

        loadRows();
    }

    private void loadRows() {
        allRows.clear();
        for (ModelSpec spec : ModelUtils.bundledModelSpecs()) {
            boolean available = assetExists(spec.assetPath);
            String action = available ? "Built-in" : "Import";
            allRows.add(new ModelRow(spec.label, available ? "Bundled" : "Missing", spec.multilingual,
                    spec.assetPath, available, true, false, action));
        }

        File[] customFiles = ModelUtils.customModelsDir(this).listFiles((dir, name) ->
                name.toLowerCase(Locale.US).endsWith(".tflite"));
        if (customFiles != null) {
            for (File file : customFiles) {
                boolean multilingual = !file.getName().toLowerCase(Locale.US).contains(".en");
                allRows.add(new ModelRow(file.getName(), "Downloaded", multilingual,
                        file.getAbsolutePath(), true, false, true, "Remove"));
            }
        }

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
        if (selectedIndex >= filteredRows.size()) {
            selectedIndex = -1;
            listModels.clearChoices();
        }
        tvStatus.setText(filteredRows.isEmpty() ? "No models found." : "Models listed: " + filteredRows.size());
        refreshActionButtons();
    }

    private void refreshActionButtons() {
        ModelRow selected = getSelectedRow();
        if (selected == null) {
            btnPrimaryAction.setEnabled(false);
            btnSecondaryAction.setEnabled(false);
            btnPrimaryAction.setText("Download / Import");
            btnSecondaryAction.setText("Remove");
            return;
        }

        if (selected.customFile) {
            btnPrimaryAction.setEnabled(false);
            btnPrimaryAction.setText("Downloaded");
            btnSecondaryAction.setEnabled(true);
            btnSecondaryAction.setText("Remove");
            return;
        }

        if (selected.available) {
            btnPrimaryAction.setEnabled(false);
            btnPrimaryAction.setText("Built-in");
            btnSecondaryAction.setEnabled(false);
            btnSecondaryAction.setText("Remove");
        } else {
            btnPrimaryAction.setEnabled(true);
            btnPrimaryAction.setText("Import File");
            btnSecondaryAction.setEnabled(false);
            btnSecondaryAction.setText("Remove");
        }
    }

    private void runPrimaryAction() {
        ModelRow selected = getSelectedRow();
        if (selected == null) {
            tvStatus.setText("Select a model first.");
            return;
        }
        if (!selected.available && !selected.customFile) {
            importModelLauncher.launch(new String[]{"application/octet-stream", "*/*"});
        }
    }

    private void runSecondaryAction() {
        ModelRow selected = getSelectedRow();
        if (selected == null || !selected.customFile) {
            tvStatus.setText("Select a downloaded custom model to remove.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Remove model")
                .setMessage("Remove " + selected.displayName + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    File file = new File(selected.location);
                    if (file.exists()) file.delete();
                    selectedIndex = -1;
                    loadRows();
                })
                .show();
    }

    private void onModelPicked(Uri uri) {
        ModelRow selected = getSelectedRow();
        if (uri == null || selected == null) return;
        new Thread(() -> {
            try {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }

                String displayName = queryDisplayName(uri);
                if (displayName == null || !displayName.toLowerCase(Locale.US).endsWith(".tflite")) {
                    throw new IOException("Pick a .tflite model file");
                }
                File outFile;
                if (selected.displayName.toLowerCase(Locale.US).contains("small")) {
                    outFile = new File(ModelUtils.customModelsDir(this), "whisper-small.tflite");
                } else {
                    outFile = new File(ModelUtils.customModelsDir(this), sanitizeName(displayName));
                }
                copyUriToFile(uri, outFile);
                runOnUiThread(() -> {
                    tvStatus.setText("Imported model: " + outFile.getName());
                    loadRows();
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Import failed: " + e.getMessage()));
            }
        }).start();
    }

    private ModelRow getSelectedRow() {
        if (selectedIndex < 0 || selectedIndex >= filteredRows.size()) return null;
        return filteredRows.get(selectedIndex);
    }

    private boolean assetExists(String path) {
        try {
            getAssets().open(path).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) return cursor.getString(nameIndex);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void copyUriToFile(Uri uri, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Unable to open model file");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static final class ModelRow {
        public final String displayName;
        public final String state;
        public final boolean multilingual;
        public final String location;
        public final boolean available;
        public final boolean bundled;
        public final boolean customFile;
        public final String actionLabel;

        public ModelRow(String displayName, String state, boolean multilingual, String location,
                        boolean available, boolean bundled, boolean customFile, String actionLabel) {
            this.displayName = displayName;
            this.state = state;
            this.multilingual = multilingual;
            this.location = location;
            this.available = available;
            this.bundled = bundled;
            this.customFile = customFile;
            this.actionLabel = actionLabel;
        }

        public String statusLine() {
            return state + " | " + (multilingual ? "multilingual" : "english-only")
                    + " | " + (bundled ? "built-in" : "custom");
        }
    }
}
