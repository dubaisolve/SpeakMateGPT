package com.dubaisolve.speakmate;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.google.android.material.button.MaterialButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import org.apache.commons.io.IOUtils;

public class MainActivity extends AppCompatActivity {
    private static final int IMPORT_AUDIO_REQUEST_CODE = 42;
    private static final int IMPORT_IMAGE_REQUEST_CODE = 43;
    private static final int SETTINGS_REQUEST_CODE = 1;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final long WARNING_THRESHOLD_BYTES = (long) (MAX_FILE_SIZE_BYTES * 0.9); // 90% of 25 MB
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 1001;

    private boolean isRecording = false;
    // Declare API key variables
    private static String fileName = null;
    private MediaRecorder recorder = null;
    private ProgressDialog progressDialog;
    private MaterialButton recordButton;
    private MaterialButton backButton;
    private String audioFilePath;
    private MediaPlayer mediaPlayer;
    private Handler fileSizeCheckHandler = new Handler();
    private RecyclerView recyclerView;
    private MessageAdapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private List<Message> myDataset;
    private EditText userInput;
    private MaterialButton importMaterialButton, importAudioButton, sendButton;

    private Gpt3Service gpt3Service;
    private ElevenLabsService elevenLabsService;
    private Gpt4VisionService gpt4VisionService;
    private TranscriptionService transcriptionService;
    private String gptApiKey;
    private String elevenLabsApiKey;
    private String voiceId;
    private String model;
    private String maxTokens;
    private String n;
    private String temperature;

    @Override
    protected void onResume() {
        super.onResume();
        // Reload API keys when returning to MainActivity
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");
        model = sharedPreferences.getString("MODEL", "gpt-4o");
        maxTokens = sharedPreferences.getString("MAX_TOKENS", "500");
        n = sharedPreferences.getString("N", "1");
        temperature = sharedPreferences.getString("TEMPERATURE", "0.5");

    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(fileName);
            try {
                recorder.prepare();
                recorder.start();
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("AudioRecorder", "prepare() failed");
                e.printStackTrace();
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }
    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
                Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();

                // Transcribe the recorded audio
                File audioFile = new File(fileName);
                if (audioFile.exists()) {
                    transcribeAudioFile(audioFile, gptApiKey);
                }
            } catch (RuntimeException stopException) {
                // Handle the case where stop() is called before start()
                stopException.printStackTrace();
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                // Permission denied
                Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void transcribeAudioFile(File audioFile, String gptApiKey) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Transcribing audio...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        RequestBody requestFile = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        String transcriptionModel = "whisper-1"; // Use a different variable name
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);
        RequestBody modelPart = RequestBody.create(transcriptionModel, MediaType.parse("text/plain"));

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
                        runOnUiThread(() -> {
                            Message userMessage = new Message("User", transcription);
                            myDataset.add(userMessage);
                            mAdapter.notifyItemInserted(myDataset.size() - 1);
                            layoutManager.scrollToPosition(myDataset.size() - 1);

                            // Send transcribed text to GPT-3 with the correct model
                            sendTextToGpt3(transcription, gptApiKey, elevenLabsApiKey, voiceId, model, maxTokens, n, temperature);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    handleTranscriptionError(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressDialog.dismiss();
                Log.e("TranscriptionError", "Request failed: ", t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleTranscriptionError(Response<ResponseBody> response) {
        try {
            String errorBody = response.errorBody().string();
            Log.e("TranscriptionError", "Error response body: " + errorBody);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.e("TranscriptionError", "Unsuccessful response: " + response.code());
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show());
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");
        model = sharedPreferences.getString("MODEL", "gpt-4o");
        maxTokens = sharedPreferences.getString("MAX_TOKENS", "500");
        n = sharedPreferences.getString("N", "1");
        temperature = sharedPreferences.getString("TEMPERATURE", "0.5");
        if (gptApiKey.isEmpty() || elevenLabsApiKey.isEmpty() || voiceId.isEmpty()) {
            // Prompt the user to enter API keys
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
            // Optionally, you can finish the MainActivity to prevent the user from going back without entering keys
            // finish();
        }
        // RecyclerView setup
        recyclerView = findViewById(R.id.message_list);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        myDataset = new ArrayList<>();
        mAdapter = new MessageAdapter(myDataset);
        recyclerView.setAdapter(mAdapter);

        // UI elements
        userInput = findViewById(R.id.user_input);
        sendButton = findViewById(R.id.send_button);
        importMaterialButton = findViewById(R.id.import_image_button);
        importAudioButton = findViewById(R.id.import_audio_button);
        recordButton = findViewById(R.id.recordButton);
        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
                isRecording = false;
                recordButton.setIcon(ContextCompat.getDrawable(this, R.drawable.record_button));
            } else {
                startRecording();
                isRecording = true;
                recordButton.setIcon(ContextCompat.getDrawable(this, R.drawable.stop_button));
            }
        });
        backButton = findViewById(R.id.back_button);
        sendButton.setOnClickListener(v -> sendMessage());
        importMaterialButton.setOnClickListener(v -> importImages());
        importAudioButton.setOnClickListener(v -> importAudioFile());
        backButton.setOnClickListener(v -> resetButtons());

        setupApiServices();
        setupUserInputBehavior();

        fileName = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/audio.mp3";
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE);
                return true;
            case R.id.action_help:
                Intent helpIntent = new Intent(MainActivity.this, HelpActivity.class);
                startActivity(helpIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupApiServices() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();

        Retrofit retrofitOpenAI = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        gpt3Service = retrofitOpenAI.create(Gpt3Service.class);
        gpt4VisionService = retrofitOpenAI.create(Gpt4VisionService.class);

        Retrofit retrofitElevenLabs = new Retrofit.Builder()
                .baseUrl("https://api.elevenlabs.io/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        elevenLabsService = retrofitElevenLabs.create(ElevenLabsService.class);
        transcriptionService = createTranscriptionService();
    }

    private void setupUserInputBehavior() {
        userInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                updateLayoutForTextInput();
            }
        });

        userInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    recordButton.setVisibility(View.GONE);
                    updateLayoutForTextInput();
                } else {
                    restoreInitialLayout();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        backButton.setOnClickListener(v -> restoreInitialLayout());
    }

    private void updateLayoutForTextInput() {
        if (importMaterialButton.getVisibility() == View.VISIBLE || importAudioButton.getVisibility() == View.VISIBLE) {
            importMaterialButton.setVisibility(View.GONE);
            importAudioButton.setVisibility(View.GONE);
            backButton.setVisibility(View.VISIBLE);
        }

        // Use FrameLayout.LayoutParams because the user_input is inside a FrameLayout
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) userInput.getLayoutParams();
        params.width = FrameLayout.LayoutParams.MATCH_PARENT;  // Expand the input field to take full width
        userInput.setLayoutParams(params);
    }


    private void restoreInitialLayout() {
        importMaterialButton.setVisibility(View.VISIBLE);
        importAudioButton.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.GONE);

        if (userInput.getText().length() == 0) {
            recordButton.setVisibility(View.VISIBLE);
        }

        // Restore the original input field width using FrameLayout.LayoutParams
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) userInput.getLayoutParams();
        params.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        userInput.setLayoutParams(params);
    }


    private void resetButtons() {
        importMaterialButton.setVisibility(View.VISIBLE);
        importAudioButton.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.GONE);
        userInput.setHint("Type your message here...");
    }

    private void sendMessage() {
        String text = userInput.getText().toString().trim();
        if (!text.isEmpty()) {
            Message userMessage = new Message("User", text);
            myDataset.add(userMessage);
            mAdapter.notifyItemInserted(myDataset.size() - 1);
            layoutManager.scrollToPosition(myDataset.size() - 1);
            userInput.setText("");

            // Send message to GPT-3
            sendTextToGpt3(text, gptApiKey, elevenLabsApiKey, voiceId, model, maxTokens, n, temperature);
        }
    }

    private void importAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, IMPORT_AUDIO_REQUEST_CODE);
    }

    private void importImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, IMPORT_IMAGE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_AUDIO_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri audioUri = data.getData();
            if (audioUri != null) {
                transcribeAudioWithUri(audioUri, gptApiKey);
            } else {
                Toast.makeText(this, "Failed to import audio file", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == IMPORT_IMAGE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                sendImages(data.getClipData(), null, gptApiKey);
            } else if (data.getData() != null) {
                sendImages(null, data.getData(), gptApiKey);
            } else {
                Toast.makeText(this, "Failed to import images", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private TranscriptionService createTranscriptionService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(TranscriptionService.class);
    }

    private void transcribeAudioWithUri(Uri audioUri, String gptApiKey) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Transcribing audio...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        try {
            File tempFile = createTempFileFromUri(audioUri);
            RequestBody requestFile = RequestBody.create(tempFile, MediaType.parse("audio/mpeg"));
            String model = "whisper-1";
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", tempFile.getName(), requestFile);
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
                            runOnUiThread(() -> {
                                Message aiMessage = new Message("User", transcription);
                                myDataset.add(aiMessage);
                                mAdapter.notifyItemInserted(myDataset.size() - 1);
                                layoutManager.scrollToPosition(myDataset.size() - 1);
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
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show());
                    }

                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    progressDialog.dismiss();
                    Log.e("TranscriptionError", "Request failed: ", t);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (IOException e) {
            progressDialog.dismiss();
            e.printStackTrace();
            Toast.makeText(this, "Failed to process the audio file", Toast.LENGTH_SHORT).show();
        }
    }

    private File createTempFileFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        File tempFile = File.createTempFile("audio", ".m4a", getCacheDir());
        tempFile.deleteOnExit();
        FileOutputStream out = new FileOutputStream(tempFile);
        if (inputStream != null) {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            inputStream.close();
        }
        out.close();
        return tempFile;
    }

    private void sendImages(ClipData clipData, Uri singleImageUri, String gptApiKey) {
        JsonArray messagesArray = new JsonArray();
        JsonObject textMessage = new JsonObject();
        textMessage.addProperty("type", "text");
        String promptText = (clipData != null && clipData.getItemCount() > 1) ?
                "extract all text from these images " :
                "extract all text from image";
        textMessage.addProperty("text", promptText);
        messagesArray.add(textMessage);

        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri imageUri = clipData.getItemAt(i).getUri();
                Log.d("ImageProcessing", "Processing URI: " + imageUri.toString());
                addImageToMessagesArray(imageUri, messagesArray);
            }
        } else if (singleImageUri != null) {
            addImageToMessagesArray(singleImageUri, messagesArray);
        }

        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        String maxTokens = sharedPreferences.getString("MAX_TOKENS", "500");

        sendGpt4VisionRequest(messagesArray, gptApiKey, maxTokens);
    }

    private void addImageToMessagesArray(Uri imageUri, JsonArray messagesArray) {
        String base64Image = encodeImageToBase64(imageUri);
        if (!base64Image.isEmpty()) {
            JsonObject imageMessage = new JsonObject();
            imageMessage.addProperty("type", "image_url");
            JsonObject imageUrlObject = new JsonObject();
            imageUrlObject.addProperty("url", "data:image/jpeg;base64," + base64Image);
            imageUrlObject.addProperty("detail", "high");
            imageMessage.add("image_url", imageUrlObject);
            messagesArray.add(imageMessage);
        }
    }

    private String encodeImageToBase64(Uri imageUri) {
        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            byte[] bytes = IOUtils.toByteArray(inputStream);
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private void sendGpt4VisionRequest(JsonArray messagesArray, String gptApiKey, String maxTokens) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Analyzing images...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "gpt-4o");
        JsonArray newMessagesArray = new JsonArray();
        JsonObject messageObject = new JsonObject();
        messageObject.addProperty("role", "user");
        messageObject.add("content", messagesArray);
        newMessagesArray.add(messageObject);

        requestBody.add("messages", newMessagesArray);
        try {
            requestBody.addProperty("max_tokens", Integer.parseInt(maxTokens));
        } catch (NumberFormatException e) {
            requestBody.addProperty("max_tokens", 500);
        }

        RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json"));

        gpt4VisionService.chatCompletion("Bearer " + gptApiKey, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progressDialog.dismiss();
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        Log.d("Gpt4VisionResponse", jsonResponse);
                        Gson gson = new Gson();
                        JsonObject responseObject = gson.fromJson(jsonResponse, JsonObject.class);
                        final String imageAnalysisResult = responseObject.getAsJsonArray("choices").get(0)
                                .getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
                        Log.d("ImageAnalysisResult", imageAnalysisResult);

                        String newPromptForGpt3 = imageAnalysisResult + " Ignore everything apart from the question/s. Answer the question/s by providing Question number and option or logical sequence like: Q1 - 2 or Q1 - 1,3,5 keep it short";
                        Log.d("NewPromptForGpt3", newPromptForGpt3);

                        sendTextToGpt3(newPromptForGpt3, gptApiKey, elevenLabsApiKey, voiceId, model, maxTokens, n, temperature);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    handleErrorResponse(response);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressDialog.dismiss();
                Log.e("Gpt4VisionError", "Request failed: ", t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to analyze the images", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleErrorResponse(Response<ResponseBody> response) {
        String errorMessage = "Failed to analyze the images";
        try {
            String errorBody = response.errorBody().string();
            Log.e("Gpt4VisionError", "Error response body: " + errorBody);

            Gson gson = new Gson();
            JsonObject errorResponse = gson.fromJson(errorBody, JsonObject.class);
            JsonObject errorObject = errorResponse.getAsJsonObject("error");
            if (errorObject != null && errorObject.has("message")) {
                errorMessage = errorObject.get("message").getAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.e("Gpt4VisionError", "Unsuccessful response: " + response.code());
        final String finalErrorMessage = errorMessage;
        runOnUiThread(() -> showErrorMessageDialog(finalErrorMessage));
    }

    private void showErrorMessageDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void sendTextToGpt3(String text, String gptApiKey, String elevenLabsApiKey, String voiceId, String model, String maxTokens, String n, String temperature) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending to AI...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Null or empty checks for the inputs
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(MainActivity.this, "Text input is empty", Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
            return;
        }

        if (gptApiKey == null || gptApiKey.trim().isEmpty()) {
            Toast.makeText(MainActivity.this, "API Key is missing", Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
            return;
        }

        if (maxTokens == null || maxTokens.trim().isEmpty()) {
            maxTokens = "500"; // Default value
        }

        if (n == null || n.trim().isEmpty()) {
            n = "1"; // Default value
        }

        if (temperature == null || temperature.trim().isEmpty()) {
            temperature = "0.1"; // Default value
        }

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("model", model);

        JsonArray messagesArray = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", text);
        messagesArray.add(userMessage);

        jsonObject.add("messages", messagesArray);

        try {
            jsonObject.addProperty("max_tokens", Integer.parseInt(maxTokens.trim()));
        } catch (NumberFormatException e) {
            jsonObject.addProperty("max_tokens", 500); // Default value
        }

        try {
            jsonObject.addProperty("n", Integer.parseInt(n.trim()));
        } catch (NumberFormatException e) {
            jsonObject.addProperty("n", 1); // Default value
        }

        try {
            jsonObject.addProperty("temperature", Double.parseDouble(temperature.trim()));
        } catch (NumberFormatException e) {
            jsonObject.addProperty("temperature", 0.1); // Default value
        }

        RequestBody body = RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"));

        gpt3Service.chatCompletion("Bearer " + gptApiKey, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progressDialog.dismiss();
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        Log.d("Gpt3Response", jsonResponse);
                        Gson gson = new Gson();
                        JsonObject responseObject = gson.fromJson(jsonResponse, JsonObject.class);
                        final String generatedText = responseObject.getAsJsonArray("choices").get(0)
                                .getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                        final String aiResponse = generatedText.replaceFirst("AI: ", "").replaceFirst("Assistant: ", "");

                        runOnUiThread(() -> {
                            Message aiMessage = new Message("AI", aiResponse);
                            myDataset.add(aiMessage);
                            mAdapter.notifyItemInserted(myDataset.size() - 1);
                            layoutManager.scrollToPosition(myDataset.size() - 1);
                        });

                        if (!elevenLabsApiKey.isEmpty() && !voiceId.isEmpty()) {
                            getAudioResponseFromElevenLabs(aiResponse, elevenLabsApiKey, voiceId);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    String errorDetails = "Unsuccessful response: " + response.code();
                    try {
                        errorDetails += " - " + response.errorBody().string();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.e("Gpt3Error", errorDetails);
                    String finalErrorDetails = errorDetails;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get a response from AI: " + finalErrorDetails, Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressDialog.dismiss();
                String errorMessage = t instanceof HttpException ? ((HttpException) t).response().errorBody().toString() : t.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        });
    }


    private void getAudioResponseFromElevenLabs(String text, String elevenLabsApiKey, String voiceId) {
        if (elevenLabsApiKey.isEmpty() || voiceId.isEmpty()) {
            Log.e("ElevenLabsError", "Eleven Labs API key or Voice ID is missing.");
            return;
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("text", text);
        jsonObject.addProperty("model_id", "eleven_multilingual_v2");
        JsonObject voiceSettings = new JsonObject();
        voiceSettings.addProperty("stability", 0.71);
        voiceSettings.addProperty("similarity_boost", 0.5);
        jsonObject.add("voice_settings", voiceSettings);

        RequestBody body = RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"));

        elevenLabsService.textToSpeech(voiceId, elevenLabsApiKey, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        File tempMp3 = File.createTempFile("response", ".mp3", getCacheDir());
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
    }
}
