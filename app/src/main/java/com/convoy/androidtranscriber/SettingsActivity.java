package com.convoy.androidtranscriber;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.convoy.androidtranscriber.util.AppSettings;
import com.convoy.androidtranscriber.util.StorageUtils;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Switch switchAiEnhance = findViewById(R.id.switchAiEnhance);
        Switch switchTrim = findViewById(R.id.switchTrim);
        TextView tvFolderPath = findViewById(R.id.tvFolderPath);
        Button btnFolderSetup = findViewById(R.id.btnFolderSetup);

        switchAiEnhance.setChecked(AppSettings.isAiEnhanceEnabled(this));
        switchTrim.setChecked(AppSettings.isTrimEnabled(this));
        refreshFolderPath(tvFolderPath);

        switchAiEnhance.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                AppSettings.setAiEnhanceEnabled(this, isChecked));
        switchTrim.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                AppSettings.setTrimEnabled(this, isChecked));
        btnFolderSetup.setOnClickListener(v -> showFolderModeDialog(tvFolderPath));
    }

    private void showFolderModeDialog(TextView tvFolderPath) {
        String[] labels = new String[]{
                "Documents folder",
                "Downloads folder",
                "Internal app folder"
        };
        String[] values = new String[]{
                AppSettings.STORAGE_DOCUMENTS,
                AppSettings.STORAGE_DOWNLOADS,
                AppSettings.STORAGE_INTERNAL
        };
        new AlertDialog.Builder(this)
                .setTitle("Choose base folder")
                .setItems(labels, (dialog, which) -> {
                    AppSettings.setStorageMode(this, values[which]);
                    refreshFolderPath(tvFolderPath);
                })
                .show();
    }

    private void refreshFolderPath(TextView tvFolderPath) {
        tvFolderPath.setText("Current base folder:\n" + StorageUtils.describeBaseDir(this));
    }
}
