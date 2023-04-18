package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

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

        // Initialize RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Voice> voices = new ArrayList<>();
        voices.add(new Voice("21m00Tcm4TlvDq8ikWAM", "Rachel", "https://storage.googleapis.com/eleven-public-prod/premade/voices/21m00Tcm4TlvDq8ikWAM/6edb9076-c3e4-420c-b6ab-11d43fe341c8.mp3"));
        voices.add(new Voice("AZnzlk1XvdvUeBnXmlld", "Domi", "https://storage.googleapis.com/eleven-public-prod/premade/voices/AZnzlk1XvdvUeBnXmlld/69c5373f-0dc2-4efd-9232-a0140182c0a9.mp3"));
        voices.add(new Voice("EXAVITQu4vr4xnSDxMaL", "Bella", "https://storage.googleapis.com/eleven-public-prod/premade/voices/EXAVITQu4vr4xnSDxMaL/04365bce-98cc-4e99-9f10-56b60680cda9.mp3"));
        voices.add(new Voice("ErXwobaYiN019PkySvjV", "Antoni", "https://storage.googleapis.com/eleven-public-prod/premade/voices/ErXwobaYiN019PkySvjV/38d8f8f0-1122-4333-b323-0b87478d506a.mp3"));
        voices.add(new Voice("MF3mGyEYCl7XYWbV9V6O", "Elli", "https://storage.googleapis.com/eleven-public-prod/premade/voices/MF3mGyEYCl7XYWbV9V6O/f9fd64c3-5d62-45cd-b0dc-ad722ee3284e.mp3"));
        voices.add(new Voice("TxGEqnHWrfWFTfGW9XjX", "Josh", "https://storage.googleapis.com/eleven-public-prod/premade/voices/TxGEqnHWrfWFTfGW9XjX/c6c80dcd-5fe5-4a4c-a74c-b3fec4c62c67.mp3"));
        voices.add(new Voice("VR6AewLTigWG4xSOukaG", "Arnold", "https://storage.googleapis.com/eleven-public-prod/premade/voices/VR6AewLTigWG4xSOukaG/66e83dc2-6543-4897-9283-e028ac5ae4aa.mp3"));

        VoiceAdapter.OnItemClickListener onItemClickListener = previewUrl -> {
            // Handle the click event, e.g., open the link in a browser
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl));
            startActivity(browserIntent);
        };

        VoiceAdapter adapter = new VoiceAdapter(voices, onItemClickListener);
        recyclerView.setAdapter(adapter);
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
