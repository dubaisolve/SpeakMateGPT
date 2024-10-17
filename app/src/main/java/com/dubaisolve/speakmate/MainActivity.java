package com.dubaisolve.speakmate;

import android.Manifest;
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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.google.android.material.button.MaterialButton;

import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import okhttp3.WebSocket;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import android.widget.PopupMenu;

import android.app.Dialog;
import android.widget.SeekBar;
import android.widget.Button;
import android.view.Window;

import android.media.AudioFormat;

import okhttp3.Request;
import okhttp3.WebSocketListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

import org.apache.commons.io.IOUtils;

public class MainActivity extends AppCompatActivity {
    private static final int IMPORT_AUDIO_REQUEST_CODE = 42;
    private static final int IMPORT_IMAGE_REQUEST_CODE = 43;
    private static final int SETTINGS_REQUEST_CODE = 1;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final long WARNING_THRESHOLD_BYTES = (long) (MAX_FILE_SIZE_BYTES * 0.9); // 90% of 25 MB
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 1001;
    private boolean isRecordingPermissionRequestedByUser = false;


    private boolean isRecording = false;
    // Declare API key variables
    private static String fileName = null;
    private MediaRecorder recorder = null;
    private MaterialButton recordButton;
    private MaterialButton backButton;
    private String audioFilePath;
    private MediaPlayer mediaPlayer;
    private Handler fileSizeCheckHandler = new Handler(Looper.getMainLooper());
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
    private ProgressBar progressBar;
    private AlertDialog progressDialog;
    private ActivityResultLauncher<Intent> importAudioLauncher;
    private ActivityResultLauncher<Intent> importImageLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;
    // For the overlay
    private View overlayView;
    private MaterialButton recordButtonOverlay;
    private MaterialButton stopButtonOverlay;
    private MaterialButton closeButtonOverlay;

    // For WebSocket
    private OkHttpClient webSocketClient;
    private WebSocket webSocket;

    // For Audio Recording
    private AudioRecord audioRecord;
    private boolean isOverlayRecording = false;
    private int bufferSize;
    private Switch audioToggleSwitch;
    private boolean isAudioOutputEnabled = false; // Default is off

    // Audio playback variables
    private AudioTrack audioTrack;
    private final int sampleRate = 24000; // As per Realtime API requirements
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private final int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
    private int minBufferSize;
    private boolean isAudioTrackInitialized = false;



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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 (API level 31)
                recorder = new MediaRecorder(this);
            } else {
                recorder = new MediaRecorder();
            }

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

    private void showProgressDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false); // Prevent dialog from being dismissed

        // Inflate custom view
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.progress_dialog, null);
        builder.setView(dialogView);

        TextView messageText = dialogView.findViewById(R.id.dialog_progress_message);
        messageText.setText(message);

        progressDialog = builder.create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isRecordingPermissionRequestedByUser) {
                    // Permission granted, proceed with recording
                    isRecordingPermissionRequestedByUser = false; // Reset the flag
                    startOverlayRecording(); // This will check permissions again but they are now granted
                }
            } else {
                // Permission denied, inform the user
                Toast.makeText(this, "Permission to record audio was denied", Toast.LENGTH_SHORT).show();
                isRecordingPermissionRequestedByUser = false; // Reset the flag
            }
        }
    }

    private void transcribeAudioFile(File audioFile, String gptApiKey) {
        showProgressDialog("Transcribing audio...");
        RequestBody requestFile = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        String transcriptionModel = "whisper-1"; // Use a different variable name
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);
        RequestBody modelPart = RequestBody.create(transcriptionModel, MediaType.parse("text/plain"));

        transcriptionService.transcribeAudio("Bearer " + gptApiKey, modelPart, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
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
                dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
                Log.e("TranscriptionError", "Request failed: ", t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show());
            }
        });
    }
    private synchronized void writeAudioData(byte[] audioData) {
        if (!isAudioTrackInitialized) {
            initAudioTrack();
        }

        if (audioTrack != null) {
            audioTrack.write(audioData, 0, audioData.length);
        }
    }

    private void initAudioTrack() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        audioTrack = new AudioTrack(
                audioAttributes,
                audioFormat,
                minBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);

        if (audioTrack.getState() != AudioTrack.STATE_UNINITIALIZED) {
            audioTrack.play();
            isAudioTrackInitialized = true;
        } else {
            Log.e("AudioTrack", "Failed to initialize AudioTrack");
        }
    }

    private synchronized void releaseAudioTrack() {
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
            isAudioTrackInitialized = false;
        }
    }


    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
    }

    // To hide the progress bar
    private void hideProgressBar() {
        progressBar.setVisibility(View.GONE);
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

        // Set the content view first
        setContentView(R.layout.activity_main);

        // Initialize the overlay
        initOverlay();

        // Initialize UI elements
        userInput = findViewById(R.id.user_input);
        sendButton = findViewById(R.id.send_button);
        importMaterialButton = findViewById(R.id.import_image_button);
        importAudioButton = findViewById(R.id.import_audio_button);
        recordButton = findViewById(R.id.recordButton);
        backButton = findViewById(R.id.back_button);
        progressBar = findViewById(R.id.progress_bar);
        recyclerView = findViewById(R.id.message_list);
        // Initialize minBufferSize
        minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);


        // Check if sendButton is null
        if (sendButton == null) {
            Log.e("MainActivity", "sendButton is null. Please check the ID in your layout file.");
            Toast.makeText(this, "Error: sendButton not found in layout.", Toast.LENGTH_LONG).show();
            return; // Exit onCreate() to prevent further NullPointerExceptions
        }

        // Set initial tag and icon for sendButton
        sendButton.setTag("gpt3");
        sendButton.setIconResource(R.drawable.gpt3_button);

        // Set up the click listener for sendButton
        sendButton.setOnClickListener(v -> {
            String tag = (String) sendButton.getTag();
            if ("send".equals(tag)) {
                sendMessage();
            } else if ("gpt3".equals(tag)) {
                showOverlay();
            }
        });

        // Initialize SharedPreferences and retrieve API keys and settings
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");
        model = sharedPreferences.getString("MODEL", "gpt-4o");
        maxTokens = sharedPreferences.getString("MAX_TOKENS", "500");
        n = sharedPreferences.getString("N", "1");
        temperature = sharedPreferences.getString("TEMPERATURE", "0.5");

        // Check if API keys are missing and prompt the user to enter them
        if (gptApiKey.isEmpty() || elevenLabsApiKey.isEmpty() || voiceId.isEmpty()) {
            // Prompt the user to enter API keys
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
            // Optionally, you can finish the MainActivity to prevent the user from going back without entering keys
            // finish();
        }

        // RecyclerView setup
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        myDataset = new ArrayList<>();
        mAdapter = new MessageAdapter(this, myDataset, new MessageAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Message clickedMessage = myDataset.get(position);
                showPopupMenu(view, clickedMessage);
            }
        });
        recyclerView.setAdapter(mAdapter);

        // Set up recordButton click listener
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

        // Set up other button click listeners
        importMaterialButton.setOnClickListener(v -> importImages());
        importAudioButton.setOnClickListener(v -> importAudioFile());
        backButton.setOnClickListener(v -> restoreInitialLayout());

        // Set up user input behavior
        setupUserInputBehavior();

        // Setup API services
        setupApiServices();

        // Initialize fileName for audio recording
        fileName = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/audio.mp3";

        // Initialize ActivityResultLaunchers
        importAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri audioUri = result.getData().getData();
                        if (audioUri != null) {
                            transcribeAudioWithUri(audioUri, gptApiKey);
                        } else {
                            Toast.makeText(this, "Failed to import audio file", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        importImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            sendImages(data.getClipData(), null, gptApiKey);
                        } else if (data.getData() != null) {
                            sendImages(null, data.getData(), gptApiKey);
                        } else {
                            Toast.makeText(this, "Failed to import images", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Handle any data returned from SettingsActivity if needed
                });
    }


    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        int hours = milliseconds / (1000 * 60 * 60);

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void showMediaPlayerDialogWithFile(File audioFile) {
        // Create a Dialog
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.media_player_dialog);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        // Initialize UI components
        Button playButton = dialog.findViewById(R.id.play_button);
        Button pauseButton = dialog.findViewById(R.id.pause_button);
        Button stopButton = dialog.findViewById(R.id.stop_button);
        SeekBar seekBar = dialog.findViewById(R.id.seek_bar);
        TextView currentTimeTextView = dialog.findViewById(R.id.current_time);
        TextView totalTimeTextView = dialog.findViewById(R.id.total_time);

        // Initialize MediaPlayer
        MediaPlayer dialogMediaPlayer = new MediaPlayer();
        try {
            dialogMediaPlayer.setDataSource(audioFile.getAbsolutePath());
            dialogMediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing audio", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return;
        }

        // Set total duration
        int duration = dialogMediaPlayer.getDuration();
        totalTimeTextView.setText(formatTime(duration));

        // Update SeekBar and current time
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (dialogMediaPlayer != null) {
                    try {
                        if (dialogMediaPlayer.isPlaying()) {
                            int currentPosition = dialogMediaPlayer.getCurrentPosition();
                            seekBar.setProgress(currentPosition);
                            currentTimeTextView.setText(formatTime(currentPosition));
                            handler.postDelayed(this, 500);
                        } else {
                            // If not playing, stop updating
                            handler.removeCallbacks(this);
                        }
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                        // Stop updating if MediaPlayer is in an invalid state
                        handler.removeCallbacks(this);
                    }
                }
            }
        };


        // Configure SeekBar
        seekBar.setMax(duration);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && dialogMediaPlayer != null) {
                    dialogMediaPlayer.seekTo(progress);
                    currentTimeTextView.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Set up play button
        playButton.setOnClickListener(v -> {
            if (dialogMediaPlayer != null && !dialogMediaPlayer.isPlaying()) {
                dialogMediaPlayer.start();
                handler.postDelayed(runnable, 0);
            }
        });

        // Set up pause button
        pauseButton.setOnClickListener(v -> {
            if (dialogMediaPlayer != null && dialogMediaPlayer.isPlaying()) {
                dialogMediaPlayer.pause();
            }
        });

        // Set up stop button
        stopButton.setOnClickListener(v -> {
            if (dialogMediaPlayer != null) {
                dialogMediaPlayer.stop();
                dialogMediaPlayer.reset();
                try {
                    dialogMediaPlayer.setDataSource(audioFile.getAbsolutePath());
                    dialogMediaPlayer.prepare();
                    seekBar.setProgress(0);
                    currentTimeTextView.setText(formatTime(0));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Release MediaPlayer when dialog is dismissed
        dialog.setOnDismissListener(dialogInterface -> {
            if (dialogMediaPlayer != null) {
                dialogMediaPlayer.release();
            }
        });

        dialog.show();
    }

    private void handleWebSocketMessage(String message) {
        Log.d("WebSocket", "Received message: " + message); // Log every message--- to be moved out on prod
        try {
            JSONObject event = new JSONObject(message);
            String eventType = event.getString("type");
            Log.d("WebSocketEvent", "Event Type: " + eventType);

            switch (eventType) {
                case "conversation.item.input_audio_transcription.completed":
                    handleUserTranscription(event);
                    break;
                case "conversation.item.created":
                    // Handle if needed
                    break;

                case "conversation.interrupted":
                    // Stop any audio playback and release AudioTrack
                    runOnUiThread(() -> {
                        releaseAudioTrack();
                    });
                    Log.d("WebSocketEvent", "Conversation interrupted by user speech.");
                    break;

                case "response.content_part.done":
                    // Existing code for content_part.done
                    JSONObject part = event.getJSONObject("part");
                    String contentType = part.getString("type");

                    if (contentType.equals("audio")) {
                        String transcript = part.getString("transcript");

                        runOnUiThread(() -> {
                            displayAIResponse(transcript);
                        });
                    } else if (contentType.equals("text")) {
                        String text = part.getString("text");

                        runOnUiThread(() -> {
                            displayAIResponse(text);
                        });
                    }
                    break;

                case "response.audio.delta":
                    // Handle audio delta
                    if (isAudioOutputEnabled) {
                        String delta = event.getString("delta");

                        // Decode the base64-encoded audio data
                        byte[] audioData = Base64.decode(delta, Base64.DEFAULT);

                        // Write audio data to AudioTrack
                        writeAudioData(audioData);
                    }
                    break;

                case "response.audio.done":
                    // Audio response is done
                    // Release AudioTrack resources
                    releaseAudioTrack();
                    break;
                case "input_audio_buffer.speech_started":
                    Log.d("WebSocketEvent", "Speech started detected.");
                    // Handle as needed
                    break;

                case "input_audio_buffer.speech_stopped":
                    Log.d("WebSocketEvent", "Speech stopped detected.");
                    // Handle as needed
                    break;
                // Handle other event types if necessary
                default:
                    Log.d("WebSocketEvent", "Unhandled event type: " + eventType);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void handleUserTranscription(JSONObject event) {
        try {
            String itemId = event.getString("item_id");
            int contentIndex = event.getInt("content_index");
            String transcript = event.getString("transcript");

            Log.d("Transcription", "Item ID: " + itemId + ", Transcript: " + transcript);

            // Display the transcribed text in the message window
            runOnUiThread(() -> {
                Message userMessage = new Message("User", transcript);
                myDataset.add(userMessage);
                mAdapter.notifyItemInserted(myDataset.size() - 1);
                layoutManager.scrollToPosition(myDataset.size() - 1);
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void displayAIResponse(String text) {
        Message aiMessage = new Message("AI", text);
        myDataset.add(aiMessage);
        mAdapter.notifyItemInserted(myDataset.size() - 1);
        recyclerView.scrollToPosition(myDataset.size() - 1);
    }


    private void sendAudioDataToWebSocket(byte[] audioData, int length) {
        // Get the actual read data
        byte[] actualData = Arrays.copyOf(audioData, length);

        // Base64 encode the audio data
        String base64Audio = Base64.encodeToString(actualData, Base64.NO_WRAP);

        // Create the JSON event
        try {
            JSONObject event = new JSONObject();
            event.put("type", "input_audio_buffer.append");
            event.put("audio", base64Audio); // Move 'audio' directly under 'event'

            // Send the event as a string
            webSocket.send(event.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void sendInitialResponseCreate() {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "response.create");

            JSONObject response = new JSONObject();
            JSONArray modalities = new JSONArray();
            modalities.put("text");
            response.put("modalities", modalities);
            response.put("instructions", "Please assist the user.");

            event.put("response", response);

            webSocket.send(event.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void initWebSocket() {
        webSocketClient = new OkHttpClient();

        String url = "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-10-01";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + gptApiKey)
                .addHeader("OpenAI-Beta", "realtime=v1")
                .build();

        webSocket = webSocketClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                Log.d("WebSocket", "Connected");
                // Send initial session.update event
                sendSessionUpdate();
                // No need to send response.create immediately; server_vad will handle it
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d("WebSocket", "Received: " + text);
                handleWebSocketMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d("WebSocket", "Closing: " + code + " / " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                Log.e("WebSocket", "Error: " + t.getMessage());
                handleWebSocketFailure(t, response);
            }
        });
    }
    private void handleWebSocketFailure(Throwable t, okhttp3.Response response) {
        runOnUiThread(() -> {
            if (response != null && response.code() == 429) {
                // Rate limit exceeded
                Toast.makeText(MainActivity.this, "Rate limit exceeded. Please try again later.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "WebSocket Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    private void sendSessionUpdate() {
        try {
            JSONObject sessionConfig = new JSONObject();

            // Configure server-side VAD with custom parameters
            JSONObject turnDetection = new JSONObject();
            turnDetection.put("type", "server_vad");
            turnDetection.put("threshold", 0.5); // Lower threshold for higher sensitivity
            turnDetection.put("silence_duration_ms", 200); // Adjust as needed -- settings
            sessionConfig.put("turn_detection", turnDetection);

            // Enable input audio transcription
            JSONObject inputAudioTranscription = new JSONObject();
            inputAudioTranscription.put("enabled", true); // Ensure transcription is enabled
            inputAudioTranscription.put("model", "whisper-1");
            sessionConfig.put("input_audio_transcription", inputAudioTranscription);
            sessionConfig.put("temperature", 0.6);
            sessionConfig.put("max_response_output_tokens", 250);
            // Set other session parameters as needed
            sessionConfig.put("voice", "alloy");
            sessionConfig.put("input_audio_format", "pcm16");
            sessionConfig.put("output_audio_format", "pcm16");
            JSONArray modalities = new JSONArray();
            modalities.put("text");
            modalities.put("audio");
            sessionConfig.put("modalities", modalities);

            // Prepare the session.update event
            JSONObject event = new JSONObject();
            event.put("type", "session.update");
            event.put("session", sessionConfig);

            webSocket.send(event.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void readAudioData() {
        if (audioRecord == null) {
            Log.e("MainActivity", "AudioRecord is null");
            return;
        }

        byte[] audioBuffer = new byte[bufferSize];
        try {
            while (isOverlayRecording) {
                int readResult = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (readResult > 0) {
                    sendAudioDataToWebSocket(audioBuffer, readResult);
                    // Remove any Thread.sleep() calls here -- settings
                    //try {
                    //    Thread.sleep(50); // Adjust the delay as needed
                    //} catch (InterruptedException e) {
                    //    e.printStackTrace();
                    //}
                } else {
                    Log.e("MainActivity", "AudioRecord read error: " + readResult);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Exception in readAudioData: " + e.getMessage());
        }
    }

    private void initAudioRecorder() {
        int sampleRate = 24000; // As per Realtime API requirements
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelConfig, audioFormat, bufferSize);

        // Enable Acoustic Echo Canceler
        int audioSessionId = audioRecord.getAudioSessionId();
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler aec = AcousticEchoCanceler.create(audioSessionId);
            if (aec != null) {
                aec.setEnabled(true); //- settings
                Log.d("AudioRecord", "Acoustic Echo Canceler enabled");
            }
        } else {
            Log.d("AudioRecord", "Acoustic Echo Canceler not available");
        }

        // Enable Noise Suppressor
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor ns = NoiseSuppressor.create(audioSessionId);
            if (ns != null) {
                ns.setEnabled(true);
                Log.d("AudioRecord", "Noise Suppressor enabled");
            }
        } else {
            Log.d("AudioRecord", "Noise Suppressor not available");
        }

        // Enable Automatic Gain Control
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl agc = AutomaticGainControl.create(audioSessionId);
            if (agc != null) {
                agc.setEnabled(true);
                Log.d("AudioRecord", "Automatic Gain Control enabled");
            }
        } else {
            Log.d("AudioRecord", "Automatic Gain Control not available");
        }
    }



    private void startOverlayRecording() {
        // Check if already recording
        if (isOverlayRecording) {
            Log.w("MainActivity", "Recording is already in progress");
            return;
        }

        // Check for RECORD_AUDIO permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Set the flag before requesting permission
            isRecordingPermissionRequestedByUser = true;
            // Request the permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        // Proceed with recording

        // Set audio mode to communication to prevent mic from picking up speaker output
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);
        }

        initAudioRecorder();
        if (audioRecord != null) {
            audioRecord.startRecording();
            isOverlayRecording = true;

            // Start WebSocket connection
            initWebSocket();

            // Start reading audio data in a separate thread
            new Thread(() -> readAudioData()).start();
        } else {
            Log.e("MainActivity", "Failed to initialize AudioRecord");
        }
    }
    private void stopOverlayRecording() {
        // Stop recording
        isOverlayRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        // Close WebSocket connection
        if (webSocket != null) {
            webSocket.close(1000, null);
            webSocket = null;
        }

        // Reset audio mode
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(true);
        }

        // Keep the overlay visible so the user can start recording again
    }
    private void showOverlay() {
        // Add the overlay to the root layout
        FrameLayout rootLayout = findViewById(android.R.id.content);
        if (overlayView.getParent() == null) {
            // Adjust the height to 3/4 of the screen
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.25));
            params.gravity = Gravity.TOP;
            overlayView.setLayoutParams(params);

            rootLayout.addView(overlayView);
        }
    }

    private void hideOverlay() {
        // Remove the overlay from the root layout
        FrameLayout rootLayout = findViewById(android.R.id.content);
        rootLayout.removeView(overlayView);

        // Stop recording if it's ongoing
        if (isOverlayRecording) {
            stopOverlayRecording();
        }
        releaseAudioTrack();
    }

    private void initOverlay() {
        // Inflate the overlay layout
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.overlay_recording, null);
        audioToggleSwitch = overlayView.findViewById(R.id.audio_toggle_switch);

        if (audioToggleSwitch != null) {
            audioToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isAudioOutputEnabled = isChecked;
                if (!isChecked) {
                    // Stop audio playback if audio is being played
                    releaseAudioTrack();
                }
            });
        } else {
            Log.e("MainActivity", "audioToggleSwitch is null. Check layout IDs.");
        }


        // Find buttons in the overlayView
        recordButtonOverlay = overlayView.findViewById(R.id.record_button_overlay);
        stopButtonOverlay = overlayView.findViewById(R.id.stop_button_overlay);
        closeButtonOverlay = overlayView.findViewById(R.id.close_button_overlay);

        // Ensure buttons are not null
        if (recordButtonOverlay == null || stopButtonOverlay == null || closeButtonOverlay == null) {
            Log.e("MainActivity", "One or more buttons are null. Check layout IDs.");
            return; // Exit the method if buttons are not found
        }

        // Set click listeners
        recordButtonOverlay.setOnClickListener(v -> {
            if (!isOverlayRecording) {
                // Set the flag to indicate that the user initiated the recording
                isRecordingPermissionRequestedByUser = true;
                startOverlayRecording();
            } else {
                Toast.makeText(this, "Already recording", Toast.LENGTH_SHORT).show();
            }
        });

        stopButtonOverlay.setOnClickListener(v -> {
            if (isOverlayRecording) {
                stopOverlayRecording();
            } else {
                Toast.makeText(this, "Not currently recording", Toast.LENGTH_SHORT).show();
            }
        });

        closeButtonOverlay.setOnClickListener(v -> {
            // Hide the overlay when close button is pressed
            hideOverlay();
        });
    }



    private void getAudioResponseForMediaPlayer(String text, String elevenLabsApiKey, String voiceId) {
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
                        // Save the audio to a file
                        File audioFile = new File(getCacheDir(), "response.mp3");
                        FileOutputStream fos = new FileOutputStream(audioFile);
                        InputStream is = new BufferedInputStream(response.body().byteStream());
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                        fos.close();
                        is.close();

                        // Show the media player dialog
                        runOnUiThread(() -> showMediaPlayerDialogWithFile(audioFile));

                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error playing audio", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    Log.e("ElevenLabsError", "Unsuccessful response: " + response.code());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get audio response", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ElevenLabsError", "Request failed: ", t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get audio response", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showMediaPlayerDialog(Message message) {
        getAudioResponseForMediaPlayer(message.getContent(), elevenLabsApiKey, voiceId);
    }
    private void showPopupMenu(View view, Message message) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.copy_button:
                    copyTextToClipboard(message.getContent());
                    return true;
                case R.id.read_aloud_button:
                    showMediaPlayerDialog(message);
                    return true;
                case R.id.share_audio_button:
                    shareAudioMessage(message);
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    // Implement copyTextToClipboard method
    private void copyTextToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AI Response", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    // Implement readAloudMessage method
    private void readAloudMessage(Message message) {
        // Fetch and play the audio for the message
        getAudioResponseFromElevenLabs(message.getContent(), elevenLabsApiKey, voiceId);
    }

    // Implement shareAudioMessage method
    private void shareAudioMessage(Message message) {
        // Fetch the audio and then share it
        getAudioResponseFromElevenLabsForSharing(message.getContent(), elevenLabsApiKey, voiceId);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (webSocket != null) {
            webSocket.close(1000, null);
            webSocket = null;
        }

        // Release AudioRecord if recording
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        // Shutdown OkHttpClient
        if (webSocketClient != null) {
            webSocketClient.dispatcher().executorService().shutdown();
        }
        releaseAudioTrack();
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
                settingsLauncher.launch(settingsIntent);
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
                    // Set send button icon to 'send' icon and update tag
                    sendButton.setIconResource(R.drawable.send);
                    sendButton.setTag("send");
                } else {
                    restoreInitialLayout();
                    // Set send button icon to 'gpt3_button' icon and update tag
                    sendButton.setIconResource(R.drawable.gpt3_button);
                    sendButton.setTag("gpt3");
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

        // Ensure sendButton icon is set to 'send'
        sendButton.setIconResource(R.drawable.send);
    }



    private void restoreInitialLayout() {
        importMaterialButton.setVisibility(View.VISIBLE);
        importAudioButton.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.GONE);

        if (userInput.getText().length() == 0) {
            recordButton.setVisibility(View.VISIBLE);
            // Set send button icon to 'gpt3_button' icon
            sendButton.setIconResource(R.drawable.gpt3_button);
        } else {
            // Input field has text, set send button icon to 'send' icon
            sendButton.setIconResource(R.drawable.send);
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
        importAudioLauncher.launch(intent);
    }

    private void importImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        importImageLauncher.launch(intent);
    }

    private TranscriptionService createTranscriptionService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(TranscriptionService.class);
    }

    private void transcribeAudioWithUri(Uri audioUri, String gptApiKey) {
        showProgressDialog("Transcribing audio...");

        try {
            File tempFile = createTempFileFromUri(audioUri);
            RequestBody requestFile = RequestBody.create(tempFile, MediaType.parse("audio/mpeg"));
            String model = "whisper-1";
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", tempFile.getName(), requestFile);
            RequestBody modelPart = RequestBody.create(model, MediaType.parse("text/plain"));

            transcriptionService.transcribeAudio("Bearer " + gptApiKey, modelPart, body).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
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
                    dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
                    Log.e("TranscriptionError", "Request failed: ", t);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to transcribe the audio", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (IOException e) {
            dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
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

        showProgressDialog("Analyzing images...");
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
                dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
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
                dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
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
        // Null or empty checks for the inputs
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(MainActivity.this, "Text input is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (gptApiKey == null || gptApiKey.trim().isEmpty()) {
            Toast.makeText(MainActivity.this, "API Key is missing", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog after validations
        showProgressDialog("Sending to AI...");

        // Handle default values
        if (maxTokens == null || maxTokens.trim().isEmpty()) {
            maxTokens = "500"; // Default value
        }

        if (n == null || n.trim().isEmpty()) {
            n = "1"; // Default value
        }

        if (temperature == null || temperature.trim().isEmpty()) {
            temperature = "0.1"; // Default value
        }

        // Build JSON request
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
                dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
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
                dismissProgressDialog(); // Use dismissProgressDialog() instead of hideProgressBar()
                String errorMessage = t instanceof HttpException ? ((HttpException) t).response().errorBody().toString() : t.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        });
    }
    private void getAudioResponseFromElevenLabs(String text, String elevenLabsApiKey, String voiceId) {
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
                        // Overwrite the existing audio file
                        File audioFile = new File(getCacheDir(), "response.mp3");
                        FileOutputStream fos = new FileOutputStream(audioFile);
                        InputStream is = new BufferedInputStream(response.body().byteStream());
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                        fos.close();
                        is.close();

                        // Play the audio
                        playAudio(audioFile.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error playing audio", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    Log.e("ElevenLabsError", "Unsuccessful response: " + response.code());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get audio response", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ElevenLabsError", "Request failed: ", t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get audio response", Toast.LENGTH_SHORT).show());
            }
        });
    }
    private void shareAudioFile(File audioFile) {
        Uri audioUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", audioFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("audio/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, audioUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share Audio via"));
    }

    private void getAudioResponseFromElevenLabsForSharing(String text, String elevenLabsApiKey, String voiceId) {
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
                        // Overwrite the existing audio file
                        File audioFile = new File(getCacheDir(), "response.mp3");
                        FileOutputStream fos = new FileOutputStream(audioFile);
                        InputStream is = new BufferedInputStream(response.body().byteStream());
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                        fos.close();
                        is.close();

                        // Share the audio file
                        shareAudioFile(audioFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error preparing audio for sharing", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    Log.e("ElevenLabsError", "Unsuccessful response: " + response.code());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get audio response", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ElevenLabsError", "Request failed: ", t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get audio response", Toast.LENGTH_SHORT).show());
            }
        });
    }
    private void playAudio(String audioFilePath) {
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
            Toast.makeText(MainActivity.this, "Error playing audio", Toast.LENGTH_SHORT).show();
        }

        // Optional: Set up on completion listener
        mediaPlayer.setOnCompletionListener(mp -> {
            // Handle completion if needed
        });

        // Optional: Set up error listener
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(MainActivity.this, "Audio playback error", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

}
