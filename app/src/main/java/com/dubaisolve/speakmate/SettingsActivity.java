package com.dubaisolve.speakmate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SettingsActivity extends AppCompatActivity {

    private MediaRecorder recorder;
    private String audioFilePath;
    private static final int REQUEST_PICK_AUDIO = 1;
    private ElevenLabsService elevenLabsService;

    private TextInputEditText gptApiKeyEditText, elevenLabsApiKeyEditText, elevenLabsVoiceIdEditText,modelEditText,maxTokensEditText,nEditText,temperatureEditText;
    private String gptApiKey, elevenLabsApiKey, voiceId,model,maxTokens,n,temperature;

    public class VoicesResponse {
        private List<Voice> voices;

        // Getters and setters
        public List<Voice> getVoices() {
            return voices;
        }

        public void setVoices(List<Voice> voices) {
            this.voices = voices;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.elevenlabs.io/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        elevenLabsService = retrofit.create(ElevenLabsService.class);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        gptApiKeyEditText = findViewById(R.id.gpt_api_key_edit_text);
        elevenLabsApiKeyEditText = findViewById(R.id.eleven_labs_api_key_edit_text);
        elevenLabsVoiceIdEditText = findViewById(R.id.eleven_labs_voice_id_edit_text);
        modelEditText = findViewById(R.id.model_edit_text);
        maxTokensEditText = findViewById(R.id.max_tokens_edit_text);
        nEditText = findViewById(R.id.n_edit_text);
        temperatureEditText = findViewById(R.id.temperature_edit_text);
        loadData();
        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveData();
                // Finish SettingsActivity and return to MainActivity
                finish();
            }
        });

        // Find the "Sample Voice" button
        Button sampleVoiceButton = findViewById(R.id.sample_voice_button);

        // Set a click listener for the "Sample Voice" button
        sampleVoiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create a popup menu with two options
                PopupMenu popupMenu = new PopupMenu(SettingsActivity.this, v);
                MenuInflater inflater = popupMenu.getMenuInflater();
                inflater.inflate(R.menu.sample_voice_menu, popupMenu.getMenu());

                // Set a click listener for the menu items
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.upload_from_file:
                                // Handle the "Upload from File" option
                                pickAudioFile();
                                return true;
                            case R.id.record_audio:
                                // Handle the "Record Audio" option
                                startRecording();
                                AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
                                builder.setTitle("Recording")
                                        .setMessage("Recording in progress...")
                                        .setNegativeButton("Stop Recording", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                stopRecording();
                                            }
                                        })
                                        .setCancelable(false)
                                        .show();
                                return true;

                            default:
                                return false;
                        }
                    }
                });

                // Show the popup menu
                popupMenu.show();
            }
        });
        Button getVoicesButton = findViewById(R.id.get_voices_button);
        getVoicesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchVoices();
            }
        });

    }

    private void fetchVoices() {
        elevenLabsService.getVoices(elevenLabsApiKey).enqueue(new Callback<VoicesResponse>() {
            @Override
            public void onResponse(Call<VoicesResponse> call, Response<VoicesResponse> response) {
                if (response.isSuccessful()) {
                    List<Voice> voices = response.body().getVoices();
                    Log.d("API_RESPONSE", "Voices: " + new Gson().toJson(voices)); // Log entire response

                    // Create an Intent to start the VoiceSamplesActivity
                    Intent intent = new Intent(SettingsActivity.this, VoiceSamplesActivity.class);

                    // Convert the voices list to an ArrayList if it's not already
                    ArrayList<Voice> voiceArrayList = new ArrayList<>(voices);

                    // Pass the voices data as extras
                    intent.putParcelableArrayListExtra("voices", voiceArrayList);

                    // Start the activity
                    startActivity(intent);
                } else {
                    handleApiError(response);
                }
            }

            @Override
            public void onFailure(Call<VoicesResponse> call, Throwable t) {
                // Handle failure (e.g., network issues)
                handleNetworkError(t);
            }
        });
    }

    private void handleApiError(Response<?> response) {
        // You can log the error or display a user-friendly message
        Log.e("API_ERROR", "Error response code: " + response.code());
        Toast.makeText(this, "Failed to fetch voices. Please try again later.", Toast.LENGTH_SHORT).show();
    }

    private void handleNetworkError(Throwable throwable) {
        // You can log the error or display a user-friendly message
        Log.e("NETWORK_ERROR", "Network error: " + throwable.getMessage());
        Toast.makeText(this, "Network error. Please check your connection and try again.", Toast.LENGTH_SHORT).show();
    }
    private void startRecording() {
        audioFilePath = getExternalCacheDir().getAbsolutePath() + "/recorded_audio.3gp";

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncodingBitRate(96000);
        recorder.setOutputFile(audioFilePath);

        try {
            recorder.prepare();
            recorder.start();
            Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
            Toast.makeText(this, "Recording finished", Toast.LENGTH_SHORT).show();
            // Proceed with the prompt for naming and uploading
            promptForVoiceName(Uri.fromFile(new File(audioFilePath)));
        }
    }
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    } else {
                        // Handle the case where the column doesn't exist
                        // You can set a default value or handle it as per your requirement
                        result = "unknown_filename";
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null || result.isEmpty()) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private byte[] readBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    private void pickAudioFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select an Audio File"),
                    REQUEST_PICK_AUDIO);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_PICK_AUDIO:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();

                    // Ask the user to enter a name for the voice sample
                    promptForVoiceName(uri);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    private void uploadVoiceSample(String voiceName, Uri audioFileUri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            InputStream inputStream = contentResolver.openInputStream(audioFileUri);
            if (inputStream == null) {
                throw new FileNotFoundException("Could not open input stream for " + audioFileUri);
            }

            byte[] bytes = readBytes(inputStream);

            // Create RequestBody for the file
            MediaType mediaType = MediaType.parse("audio/mpeg");
            RequestBody fileBody = RequestBody.create(bytes, mediaType);

            // Get the file name
            String fileName = getFileName(audioFileUri);

            // Create MultipartBody.Part for the file
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("files", fileName, fileBody);

            // Create RequestBody for the name
            RequestBody namePart = RequestBody.create(voiceName, MediaType.parse("text/plain"));

            // Call the API without the "Bearer " prefix
            elevenLabsService.addVoice(elevenLabsApiKey, namePart, filePart).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        // Handle success response
                        Toast.makeText(SettingsActivity.this, "Voice sample uploaded successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        // Handle error response
                        String errorMessage = "Error: " + response.code() + " " + response.message();
                        Toast.makeText(SettingsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        try {
                            String errorBody = response.errorBody().string();
                            Log.e("ElevenLabsError", "Error body: " + errorBody);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    // Handle failure
                    String errorMessage = "Request failed: " + t.getMessage();
                    Toast.makeText(SettingsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    Log.e("API_ERROR", errorMessage, t);
                }
            });
        } catch (Exception e) {
            String errorMessage = "File reading failed: " + e.getMessage();
            Toast.makeText(SettingsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            Log.e("FILE_ERROR", errorMessage, e);
        }
    }

    // Method to prompt the user to enter a name for the voice sample
    private void promptForVoiceName(final Uri audioFileUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Voice Name");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String voiceName = input.getText().toString();

                // Call the uploadVoiceSample method with the voice name and audio file URI
                uploadVoiceSample(voiceName, audioFileUri);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
    private void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");
        model = sharedPreferences.getString("MODEL", "");
        maxTokens = sharedPreferences.getString("MAX_TOKENS", "");
        n = sharedPreferences.getString("N", "");
        temperature = sharedPreferences.getString("TEMPERATURE", "");
        gptApiKeyEditText.setText(gptApiKey);
        elevenLabsApiKeyEditText.setText(elevenLabsApiKey);
        elevenLabsVoiceIdEditText.setText(voiceId);
        modelEditText.setText(model);
        maxTokensEditText.setText(maxTokens);
        nEditText.setText(n);
        temperatureEditText.setText(temperature);
    }
    private void saveData() {
        Log.d("SettingsActivity", "Saving data");
        gptApiKey = gptApiKeyEditText.getText().toString();
        elevenLabsApiKey = elevenLabsApiKeyEditText.getText().toString();
        voiceId = elevenLabsVoiceIdEditText.getText().toString();
        model = modelEditText.getText().toString();
        maxTokens = maxTokensEditText.getText().toString();
        n = nEditText.getText().toString();
        temperature = temperatureEditText.getText().toString();

        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("GPT_API_KEY", gptApiKey);
        editor.putString("ELEVEN_LABS_API_KEY", elevenLabsApiKey);
        editor.putString("VOICE_ID", voiceId);
        editor.putString("MODEL", model);
        editor.putString("MAX_TOKENS", maxTokens);
        editor.putString("N", n);
        editor.putString("TEMPERATURE", temperature);
        editor.commit();
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
