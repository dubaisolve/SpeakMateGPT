package com.dubaisolve.speakmate;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
public class MainActivity extends AppCompatActivity {
    private static final int SETTINGS_REQUEST_CODE = 1;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final long WARNING_THRESHOLD_BYTES = (long) (MAX_FILE_SIZE_BYTES * 0.9); // 90% of 25 MB
    private static String fileName = null;
    private MediaRecorder recorder = null;
    private ProgressDialog progressDialog;
    private MaterialButton recordButton;

    private String audioFilePath;
    private MediaPlayer mediaPlayer;
    private Handler fileSizeCheckHandler = new Handler();
    // Add a variable for the MediaController
    private MediaController mediaController;
    private String gptApiKey;
    private String elevenLabsApiKey;
    private String voiceId;
    private String model;
    private String maxTokens ;
    private String n;
    private String temperature;

    private RecyclerView recyclerView;
    private MessageAdapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private List<Message> myDataset;
    private EditText userInput;
    private Button sendButton;
    private boolean isRecording = false;
    private PopupMenu popupMenu;

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
      }
    private void checkFileSizeAndWarn() {
        File audioFile = new File(fileName);
        if (audioFile.length() > WARNING_THRESHOLD_BYTES) {
            Toast.makeText(this, "Recording is reaching the maximum size limit. Please wrap up soon.", Toast.LENGTH_LONG).show();
        }
    }
    private void enableEditTextFocus() {
        userInput.setFocusable(true);
        userInput.setFocusableInTouchMode(true);
        userInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(userInput, InputMethodManager.SHOW_IMPLICIT);
    }
    private Gpt3Service gpt3Service;
    private ElevenLabsService elevenLabsService;
    private Gpt3Service createGpt3Service() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS) // connect timeout
                .writeTimeout(60, TimeUnit.SECONDS) // write timeout
                .readTimeout(90, TimeUnit.SECONDS) // read timeout
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client) // add this line
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

        // RecyclerView setup
        recyclerView = (RecyclerView) findViewById(R.id.message_list);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        myDataset = new ArrayList<>(); // Moved out of local scope
        mAdapter = new MessageAdapter(myDataset);
        recyclerView.setAdapter(mAdapter);

        // EditText and Button setup
        userInput = findViewById(R.id.user_input);
        sendButton = findViewById(R.id.send_button);
        sendButton.setOnClickListener(v -> {
            String text = userInput.getText().toString().trim();
            if (!text.isEmpty()) {
                Message userMessage = new Message("User", text);
                myDataset.add(userMessage);
                mAdapter.notifyItemInserted(myDataset.size() - 1);
                layoutManager.scrollToPosition(myDataset.size() - 1);
                userInput.setText("");
            }
        });


        // Remaining setup code
        mediaController = new MediaController(this);
        mediaController.setMediaPlayer(mediaPlayerControl);
        mediaController.setAnchorView(findViewById(R.id.message_list));

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS) // connect timeout
                .writeTimeout(60, TimeUnit.SECONDS) // write timeout
                .readTimeout(90, TimeUnit.SECONDS) // read timeout
                .build();

        Retrofit retrofitElevenLabs = new Retrofit.Builder()
                .baseUrl("https://api.elevenlabs.io/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        elevenLabsService = retrofitElevenLabs.create(ElevenLabsService.class);

        initViews();
        initListeners();
        updateListeners();
        fileName = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/audio.mp3";
        transcriptionService = createTranscriptionService();

        Retrofit retrofitOpenAI = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        gpt3Service = retrofitOpenAI.create(Gpt3Service.class);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_RECORD_AUDIO_PERMISSION);
    }
    private void initViews() {
        recordButton = findViewById(R.id.recordButton);
        // Remove other lines related to buttons that are now in the popup menu
    }

    private void initListeners() {
        // Retrieve the API key and voice ID from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        String gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        String elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        String voiceId = sharedPreferences.getString("VOICE_ID", "");
        String model = sharedPreferences.getString("MODEL","gpt-3.5-turbo");
        String maxTokens = sharedPreferences.getString("MAX_TOKENS", "500"); // default to 500 if not set
        String n = sharedPreferences.getString("N", "1"); // default to 1 if not set
        String temperature = sharedPreferences.getString("TEMPERATURE", "0.5"); // default to 0.5 if not set

        // Refactor the listeners
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopRecording();
                    transcribeAudio(gptApiKey); // Make sure you have the gptApiKey available here.
                    isRecording = false;
                } else {
                    startRecording();
                    isRecording = true;
                }
            }
        });


        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = userInput.getText().toString().trim();
                if (!text.isEmpty()) {
                    // If there is text in the userInput, send it as a message
                    Message userMessage = new Message("User", text);
                    myDataset.add(userMessage);
                    mAdapter.notifyItemInserted(myDataset.size() - 1);
                    layoutManager.scrollToPosition(myDataset.size() - 1);
                    userInput.setText("");
                } else {
                    // Otherwise, show the pop-up menu with icons
                    PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
                    MenuInflater inflater = popupMenu.getMenuInflater();
                    inflater.inflate(R.menu.popup_menu, popupMenu.getMenu());

                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.save_audio_button:
                                    shareAudioFile(audioFilePath);
                                    return true;
                                case R.id.stop_pause_button:
                                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                                        pauseAudio();
                                    } else if (mediaPlayer != null) {
                                        playAudio(audioFilePath);
                                    }
                                    return true;
                                case R.id.copy_button:
                                    copyTextToClipboard();
                                    return true;
                                case R.id.flush_button:
                                    flushMessages();
                                    return true;
                                case R.id.translate_button:
                                    translateAudio(gptApiKey);
                                    return true;
                                case R.id.send_gpt3_button:
                                    // Build a string that contains the entire conversation history
                                    StringBuilder conversation = new StringBuilder();
                                    for (Message message : myDataset) {
                                        // Append the sender and the content of each message to the conversation
                                        conversation.append(message.getSender()).append(": ").append(message.getContent()).append("\n");
                                    }
                                    sendTextToGpt3(conversation.toString(), gptApiKey, elevenLabsApiKey, voiceId, model, maxTokens, n, temperature);
                                    return true;
                                default:
                                    return false;
                            }
                        }
                    });

                    popupMenu.show(); // Show the popup menu
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
            model = data.getStringExtra("MODEL");
            maxTokens = data.getStringExtra("MAX_TOKENS");
            n = data.getStringExtra("N");
            temperature = data.getStringExtra("TEMPERATURE");
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
        model = sharedPreferences.getString("MODEL","gpt-3.5-turbo");
        maxTokens = sharedPreferences.getString("MAX_TOKENS", "500"); // default to 500 if not set
        n = sharedPreferences.getString("N", "1"); // default to 1 if not set
        temperature = sharedPreferences.getString("TEMPERATURE", "0.5"); // default to 0.5 if not set

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
                                Message aiMessage = new Message("User", transcription);
                                myDataset.add(aiMessage);
                                mAdapter.notifyItemInserted(myDataset.size() - 1);
                                layoutManager.scrollToPosition(myDataset.size() - 1);
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
                                Message aiMessage = new Message("User", translation);
                                myDataset.add(aiMessage);
                                mAdapter.notifyItemInserted(myDataset.size() - 1);
                                layoutManager.scrollToPosition(myDataset.size() - 1);
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

    private void sendTextToGpt3(String text, String gptApiKey, String elevenLabsApiKey, String voiceId,String model,String maxTokens,String n,String temperature) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending to AI...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        String token = gptApiKey;
        // Add user message to the messagesArray
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", text);
        messagesArray.add(userMessage);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("model", model);
        jsonObject.add("messages", messagesArray);

        try {
            jsonObject.addProperty("max_tokens", Integer.parseInt(maxTokens));
        } catch (NumberFormatException e) {
            jsonObject.addProperty("max_tokens", 500); // Default value
        }

        try {
            jsonObject.addProperty("n", Integer.parseInt(n));
        } catch (NumberFormatException e) {
            jsonObject.addProperty("n", 1); // Default value
        }

        try {
            jsonObject.addProperty("temperature", Double.parseDouble(temperature));
        } catch (NumberFormatException e) {
            jsonObject.addProperty("temperature", 0.5); // Default value
        }
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
                                Message aiMessage = new Message("AI", generatedText);
                                myDataset.add(aiMessage);
                                mAdapter.notifyItemInserted(myDataset.size() - 1);
                                layoutManager.scrollToPosition(myDataset.size() - 1);
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
    private void getAudioResponseFromElevenLabs(String text, String elevenLabsApiKey, String voiceId) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("text", text);
        JsonObject voiceSettings = new JsonObject();
        voiceSettings.addProperty("stability", 0);
        voiceSettings.addProperty("similarity_boost", 0);
        jsonObject.add("voice_settings", voiceSettings);
        elevenLabsService.textToSpeech(voiceId, elevenLabsApiKey, RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"))).enqueue(new Callback<ResponseBody>() {
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
    private void shareAudioFile(String filePath) {
        try {
            File file = new File(filePath);
            Uri fileUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.setType("audio/mp3");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share audio file"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error sharing audio file", Toast.LENGTH_SHORT).show();
        }
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
        StringBuilder textToCopy = new StringBuilder();
        for (Message message : myDataset) {
            textToCopy.append(message.getSender()).append(": ").append(message.getContent()).append("\n");
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Chat Messages", textToCopy.toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    private void flushMessages() {
        // Clear messagesArray
        messagesArray = new JsonArray();

        // Clear the chat messages
        myDataset.clear();
        mAdapter.notifyDataSetChanged();
    }
}
