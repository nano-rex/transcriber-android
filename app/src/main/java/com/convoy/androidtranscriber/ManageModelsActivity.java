package com.convoy.androidtranscriber;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
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
    private Button btnDelete;

    private final List<ModelSpec> allModels = new ArrayList<>();
    private final List<ModelSpec> filteredModels = new ArrayList<>();
    private ArrayAdapter<String> adapter;
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
        Button btnImport = findViewById(R.id.btnImportModel);
        btnDelete = findViewById(R.id.btnDeleteModel);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, new ArrayList<>());
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
            ModelSpec spec = filteredModels.get(position);
            String message = "Type: " + (spec.bundled ? "bundled" : "custom")
                    + "\nLanguage: " + (spec.multilingual ? "multilingual" : "english-only")
                    + "\nPath: " + spec.assetPath;
            new AlertDialog.Builder(this)
                    .setTitle(spec.label)
                    .setMessage(message)
                    .setPositiveButton("Close", null)
                    .show();
        });

        btnImport.setOnClickListener(v -> importModelLauncher.launch(new String[]{"application/octet-stream", "*/*"}));
        btnDelete.setOnClickListener(v -> deleteSelectedModel());

        loadModels();
    }

    private void loadModels() {
        allModels.clear();
        allModels.addAll(ModelUtils.availableModels(this));
        applyFilter(etSearch.getText() == null ? "" : etSearch.getText().toString());
    }

    private void applyFilter(String query) {
        String normalized = query.trim().toLowerCase(Locale.US);
        filteredModels.clear();
        List<String> labels = new ArrayList<>();
        for (ModelSpec spec : allModels) {
            if (normalized.isEmpty() || spec.label.toLowerCase(Locale.US).contains(normalized)) {
                filteredModels.add(spec);
                labels.add(spec.label + (spec.bundled ? " [bundled]" : " [custom]"));
            }
        }
        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();
        btnDelete.setEnabled(canDeleteSelection());
        tvStatus.setText(filteredModels.isEmpty() ? "No models found." : "Installed models: " + filteredModels.size());
    }

    private void onModelPicked(Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }

            try {
                String name = queryDisplayName(uri);
                if (name == null || !name.toLowerCase(Locale.US).endsWith(".tflite")) {
                    throw new IOException("Pick a .tflite model file");
                }
                File outFile = new File(ModelUtils.customModelsDir(this), sanitizeName(name));
                copyUriToFile(uri, outFile);
                runOnUiThread(() -> {
                    tvStatus.setText("Imported model: " + outFile.getName());
                    loadModels();
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Import failed: " + e.getMessage()));
            }
        }).start();
    }

    private boolean canDeleteSelection() {
        return selectedIndex >= 0
                && selectedIndex < filteredModels.size()
                && !filteredModels.get(selectedIndex).bundled;
    }

    private void deleteSelectedModel() {
        if (!canDeleteSelection()) {
            tvStatus.setText("Select a custom model to delete.");
            return;
        }
        ModelSpec spec = filteredModels.get(selectedIndex);
        new AlertDialog.Builder(this)
                .setTitle("Delete model")
                .setMessage("Delete " + spec.label + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    File file = new File(spec.assetPath);
                    if (file.exists()) file.delete();
                    selectedIndex = -1;
                    loadModels();
                })
                .show();
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
}
