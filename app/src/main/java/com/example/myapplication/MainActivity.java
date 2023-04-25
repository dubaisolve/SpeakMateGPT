package com.example.myapplication;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import android.widget.MediaController.MediaPlayerControl;
public class MainActivity extends AppCompatActivity {
    private static final int SETTINGS_REQUEST_CODE = 1;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final long WARNING_THRESHOLD_BYTES = (long) (MAX_FILE_SIZE_BYTES * 0.9); // 90% of 25 MB
    private static String fileName = null;
    private MediaRecorder recorder = null;
    private ProgressDialog progressDialog;
    private MaterialButton recordButton, stopButton, transcribeButton , translateButton , flushButton , stopPauseButton;
    private EditText transcriptionTextView;
    private String audioFilePath;
    private MediaPlayer mediaPlayer;
    private Handler fileSizeCheckHandler = new Handler();
    // Add a variable for the MediaController
    private MediaController mediaController;
    private String gptApiKey;
    private String elevenLabsApiKey;
    private String voiceId;
    // Implement a custom MediaPlayerControl
    private MediaController.MediaPlayerControl mediaPlayerControl = new MediaController.MediaPlayerControl() {
        @Override
        public void start() {
            if (mediaPlayer != null) {
                mediaPlayer.start();
            }
        }

        @Override
        public void pause() {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
        }

        @Override
        public int getDuration() {
            return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
        }

        @Override
        public int getCurrentPosition() {
            return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        }

        @Override
        public void seekTo(int pos) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(pos);
            }
        }

        @Override
        public boolean isPlaying() {
            return mediaPlayer != null && mediaPlayer.isPlaying();
        }

        @Override
        public int getBufferPercentage() {
            return 0;
        }

        @Override
        public boolean canPause() {
            return true;
        }

        @Override
        public boolean canSeekBackward() {
            return true;
        }

        @Override
        public boolean canSeekForward() {
            return true;
        }

        @Override
        public int getAudioSessionId() {
            return mediaPlayer != null ? mediaPlayer.getAudioSessionId() : 0;
        }
    };
    private void updateListeners() {
        Button gpt3Button = findViewById(R.id.gpt3_button);
        transcribeButton.setOnClickListener(v -> transcribeAudio(gptApiKey));
        translateButton.setOnClickListener(v -> translateAudio(gptApiKey));
        gpt3Button.setOnClickListener(v -> {
            String text = transcriptionTextView.getText().toString();
            sendTextToGpt3(text, gptApiKey, elevenLabsApiKey, voiceId);
        });
    }
    private void checkFileSizeAndWarn() {
        File audioFile = new File(fileName);
        if (audioFile.length() > WARNING_THRESHOLD_BYTES) {
            Toast.makeText(this, "Recording is reaching the maximum size limit. Please wrap up soon.", Toast.LENGTH_LONG).show();
        }
    }
    private void enableEditTextFocus() {
        transcriptionTextView.setFocusable(true);
        transcriptionTextView.setFocusableInTouchMode(true);
        transcriptionTextView.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(transcriptionTextView, InputMethodManager.SHOW_IMPLICIT);
    }
    private Gpt3Service gpt3Service;
    private ElevenLabsService elevenLabsService;
    private Gpt3Service createGpt3Service() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(Gpt3Service.class);
    }
    private void updateRecordButtonIconColor(@ColorRes int colorResId) {
        int color = ContextCompat.getColor(this, colorResId);
        ColorStateList colorStateList = ColorStateList.valueOf(color);
        recordButton.setIconTint(colorStateList);
    }
    private TranscriptionService transcriptionService;
        private TranscriptionService createTranscriptionService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/") // Changed the base URL to include "v1/"
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(TranscriptionService.class);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mediaController = new MediaController(this);
        mediaController.setMediaPlayer(mediaPlayerControl);
        mediaController.setAnchorView(findViewById(R.id.media_controller_anchor));
        Retrofit retrofitElevenLabs = new Retrofit.Builder()
                .baseUrl("https://api.elevenlabs.io/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        elevenLabsService = retrofitElevenLabs.create(ElevenLabsService.class);
        initViews();
        initListeners();
        updateListeners();
        fileName = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/audio.mp3";
        transcriptionService = createTranscriptionService();
        gpt3Service = createGpt3Service();
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_RECORD_AUDIO_PERMISSION);
    }
    private void initViews() {
        recordButton = findViewById(R.id.recordButton);
        stopButton = findViewById(R.id.stopButton);
        transcribeButton = findViewById(R.id.transcribeButton);
        translateButton = findViewById(R.id.translateButton);
        flushButton = findViewById(R.id.flush_button);
        transcriptionTextView = findViewById(R.id.transcriptionTextView);
        stopPauseButton = findViewById(R.id.stop_pause_button);
    }

    private void initListeners() {
        // Retrieve the API key and voice ID from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        String gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        String elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        String voiceId = sharedPreferences.getString("VOICE_ID", "");
        transcriptionTextView.setOnClickListener(v -> enableEditTextFocus());

        recordButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());
        transcribeButton.setOnClickListener(v -> transcribeAudio(gptApiKey));
        translateButton.setOnClickListener(v -> translateAudio(gptApiKey));

        Button gpt3Button = findViewById(R.id.gpt3_button);
        gpt3Button.setOnClickListener(v -> {
            String text = transcriptionTextView.getText().toString();

            // Pass the retrieved values to the sendTextToGpt3 method
            sendTextToGpt3(text,gptApiKey,elevenLabsApiKey, voiceId);
        });
        flushButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flushMessages();
            }
        });
        Button copyButton = findViewById(R.id.copy_button);
        copyButton.setOnClickListener(v -> copyTextToClipboard());
        stopPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    pauseAudio();
                } else if (mediaPlayer != null) {
                    playAudio(audioFilePath); // Save audioFilePath as a class variable to reuse here
                }
            }
        });
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            gptApiKey = data.getStringExtra("GPT_API_KEY");
            elevenLabsApiKey = data.getStringExtra("ELEVEN_LABS_API_KEY");
            voiceId = data.getStringExtra("VOICE_ID");
            updateListeners();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");
    }
    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recordButton = findViewById(R.id.recordButton);
            updateRecordButtonIconColor(R.color.red);
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(fileName);
            try {
                recorder.prepare();
            } catch (IOException e) {
                Log.e("AudioRecorder", "prepare() failed");
            }
            recorder.start();
            // Periodically check the file size and display a warning message if needed
            fileSizeCheckHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkFileSizeAndWarn();
                    fileSizeCheckHandler.postDelayed(this, 5000); // Check every 5 seconds
                }
            }, 5000);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }
    private void stopRecording() {
        if (recorder != null) {
            updateRecordButtonIconColor(R.color.button_icon_color);
            recorder.stop();
            recorder.release();
            recorder = null;
            // Remove the periodic check for file size
            fileSizeCheckHandler.removeCallbacksAndMessages(null);
            File audioFile = new File(fileName);
            if (audioFile.length() > MAX_FILE_SIZE_BYTES) {
                Toast.makeText(this, "Recording too long. Please keep the recording under 25 MB.", Toast.LENGTH_LONG).show();
                // Delete the oversized recording.
                audioFile.delete();
            } else {
                Toast.makeText(this, "Recording stopped. File saved at: " + audioFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        }
    }
    private void transcribeAudio(String gptApiKey) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Transcribing audio...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        String token = gptApiKey; // Replace with your OpenAI API key
        String model = "whisper-1";
        File audioFile = new File(fileName);
        RequestBody requestFile = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);
        RequestBody modelPart = RequestBody.create(model, MediaType.parse("text/plain"));
        transcriptionService.transcribeAudio("Bearer " + gptApiKey, modelPart, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressDialog.dismiss();
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        Log.d("TranscriptionResponse", jsonResponse);
                        Gson gson = new Gson();
                        JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
                        final String transcription = jsonObject.get("text").getAsString();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                transcriptionTextView.setText(transcription);
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        String errorBody = response.errorBody().string();
                        Log.e("TranscriptionError", "Error response body: " + errorBody);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.e("TranscriptionError", "Unsuccessful response: " + response.code());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressDialog.dismiss();
                Log.e("TranscriptionError", "Request failed: ", t);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    private void translateAudio(String gptApiKey) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Translating audio...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        String token = gptApiKey; // Replace with your OpenAI API key
        String model = "whisper-1";
        File audioFile = new File(fileName);
        RequestBody requestFile = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);
        RequestBody modelPart = RequestBody.create(model, MediaType.parse("text/plain"));
        transcriptionService.translateAudio("Bearer " + gptApiKey, modelPart, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressDialog.dismiss();
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        Log.d("TranslationResponse", jsonResponse);
                        Gson gson = new Gson();
                        JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
                        final String translation = jsonObject.get("text").getAsString();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                transcriptionTextView.setText(translation);
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        String errorBody = response.errorBody().string();
                        Log.e("TranslationError", "Error response body: " + errorBody);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.e("TranslationError", "Unsuccessful response: " + response.code());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Failed to translate the audio", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressDialog.dismiss();
                Log.e("TranslationError", "Request failed: ", t);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Failed to translate the audio", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    private JsonArray messagesArray = new JsonArray();

    private void sendTextToGpt3(String text, String gptApiKey, String elevenLabsApiKey, String voiceId) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending to GPT-3.5 Turbo...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        String token = gptApiKey;
        // Add user message to the messagesArray
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", text);
        messagesArray.add(userMessage);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("model", "gpt-3.5-turbo");
        jsonObject.add("messages", messagesArray);
        jsonObject.addProperty("max_tokens", 500);
        jsonObject.addProperty("n", 1);
        jsonObject.addProperty("temperature", 0.5);
        gpt3Service.chatCompletion("Bearer " + gptApiKey, RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"))).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressDialog.dismiss();
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        Log.d("Gpt3Response", jsonResponse);
                        Gson gson = new Gson();
                        JsonObject responseObject = gson.fromJson(jsonResponse, JsonObject.class);
                        final String generatedText = responseObject.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                transcriptionTextView.setText(generatedText);
                            }
                        });
                        // Call getAudioResponseFromElevenLabs if ELEVEN_LABS_API_KEY and VOICE_ID are not empty
                        if (!elevenLabsApiKey.isEmpty() && !voiceId.isEmpty()) {
                            getAudioResponseFromElevenLabs(generatedText, elevenLabsApiKey, voiceId);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    final String errorMessage;
                    String errorDetails = "Unsuccessful response: " + response.code();
                    try {
                        errorDetails += " - " + response.errorBody().string();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    errorMessage = errorDetails;
                    Log.e("Gpt3Error", errorMessage);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Failed to get a response from GPT-3.5 Turbo: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressDialog.dismiss();
                String errorMessage = "";
                if (t instanceof HttpException) {
                    ResponseBody responseBody = ((HttpException) t).response().errorBody();
                    try {
                        errorMessage = responseBody.string();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    errorMessage = t.getMessage();
                }
                final String finalErrorMessage = errorMessage;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + finalErrorMessage, Toast.LENGTH_LONG).show());
            }

        });
    }
    private void getAudioResponseFromElevenLabs(String text, String apiKey, String voiceId) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("text", text);
            JsonObject voiceSettings = new JsonObject();
            voiceSettings.addProperty("stability", 0);
            voiceSettings.addProperty("similarity_boost", 0);
            jsonObject.add("voice_settings", voiceSettings);
            elevenLabsService.textToSpeech(voiceId, apiKey, RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"))).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        try {
                            File tempMp3 = File.createTempFile("response", "mp3", getCacheDir());
                            tempMp3.deleteOnExit();
                            FileOutputStream fos = new FileOutputStream(tempMp3);
                            InputStream is = new BufferedInputStream(response.body().byteStream());
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                            fos.flush();
                            fos.close();
                            is.close();
                            playAudio(tempMp3.getAbsolutePath());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e("ElevenLabsError", "Unsuccessful response: " + response.code());
                    }
                }
                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e("ElevenLabsError", "Request failed: ", t);
                }
            });
        }
    private void playAudio(String audioFilePath) {
        this.audioFilePath = audioFilePath;
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
            }

            try {
                mediaPlayer.setDataSource(audioFilePath);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        mediaController.show(0);
        }
    private void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
    }
    private void copyTextToClipboard() {
        String textToCopy = transcriptionTextView.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("GPT-3.5 Turbo Response", textToCopy);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    private void flushMessages() {
        // Clear messagesArray
        messagesArray = new JsonArray();

        // Clear the transcriptionTextView
        transcriptionTextView.setText("");
    }
}
