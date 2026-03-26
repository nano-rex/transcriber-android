package com.convoy.androidtranscriber;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.convoy.androidtranscriber.util.AppSettings;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Switch switchAiEnhance = findViewById(R.id.switchAiEnhance);
        Switch switchTrim = findViewById(R.id.switchTrim);

        switchAiEnhance.setChecked(AppSettings.isAiEnhanceEnabled(this));
        switchTrim.setChecked(AppSettings.isTrimEnabled(this));

        switchAiEnhance.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                AppSettings.setAiEnhanceEnabled(this, isChecked));
        switchTrim.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                AppSettings.setTrimEnabled(this, isChecked));
    }
}
