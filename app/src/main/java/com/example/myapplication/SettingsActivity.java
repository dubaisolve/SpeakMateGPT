package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText gptApiKeyEditText, elevenLabsApiKeyEditText, elevenLabsVoiceIdEditText;
    private String gptApiKey, elevenLabsApiKey, voiceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        gptApiKeyEditText = findViewById(R.id.gpt_api_key_edit_text);
        elevenLabsApiKeyEditText = findViewById(R.id.eleven_labs_api_key_edit_text);
        elevenLabsVoiceIdEditText = findViewById(R.id.eleven_labs_voice_id_edit_text);

        loadData();

        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveData();

                Intent resultIntent = new Intent();
                resultIntent.putExtra("GPT_API_KEY", gptApiKey);
                resultIntent.putExtra("ELEVEN_LABS_API_KEY", elevenLabsApiKey);
                resultIntent.putExtra("VOICE_ID", voiceId);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    private void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");

        gptApiKeyEditText.setText(gptApiKey);
        elevenLabsApiKeyEditText.setText(elevenLabsApiKey);
        elevenLabsVoiceIdEditText.setText(voiceId);
    }

    private void saveData() {
        gptApiKey = gptApiKeyEditText.getText().toString();
        elevenLabsApiKey = elevenLabsApiKeyEditText.getText().toString();
        voiceId = elevenLabsVoiceIdEditText.getText().toString();

        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("GPT_API_KEY", gptApiKey);
        editor.putString("ELEVEN_LABS_API_KEY", elevenLabsApiKey);
        editor.putString("VOICE_ID", voiceId);
        editor.apply();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
