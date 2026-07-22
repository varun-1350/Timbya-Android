package com.example.timbya.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.example.timbya.R;
import com.example.timbya.utils.Constants;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);

        bindSwitch(R.id.switchStatus, Constants.SHOW_STATUS_TEXT, true);
        bindSwitch(R.id.switchListening, Constants.SHOW_LISTENING_LABEL, true);
        bindSwitch(R.id.switchProcessing, Constants.SHOW_AI_STATE, true);
        bindSwitch(R.id.switchTranscript, Constants.SHOW_DEBUG_TEXT, false);
        bindSwitch(R.id.switchStartMinimized, Constants.START_MINIMIZED, true);
    }

    private void bindSwitch(int switchId, String preferenceKey, boolean defaultValue) {
        Switch control = findViewById(switchId);
        control.setChecked(preferences.getBoolean(preferenceKey, defaultValue));
        control.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(preferenceKey, checked).apply());
    }
}