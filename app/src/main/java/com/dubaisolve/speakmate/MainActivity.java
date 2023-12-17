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
import android.provider.OpenableColumns;
import android.util.Base64;
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
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import org.apache.commons.io.IOUtils;
import com.google.gson.JsonElement;
public class MainActivity extends AppCompatActivity {
    private static final int SETTINGS_REQUEST_CODE = 1;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final long WARNING_THRESHOLD_BYTES = (long) (MAX_FILE_SIZE_BYTES * 0.9); // 90% of 25 MB
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 1001;

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
    private static final int IMPORT_AUDIO_REQUEST_CODE = 42;
    private static final int IMPORT_IMAGE_REQUEST_CODE = 43;

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
    private Gpt4VisionService gpt4VisionService;
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with importing the audio file
                importAudioFile();
            } else {
                // Permission denied, show a message to the user
                Toast.makeText(this, "Storage permission is required to import audio files", Toast.LENGTH_SHORT).show();
            }
        }
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
    private BroadcastReceiver dataUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
            gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
            elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
            voiceId = sharedPreferences.getString("VOICE_ID", "");
            model = sharedPreferences.getString("MODEL","gpt-3.5-turbo");
            maxTokens = sharedPreferences.getString("MAX_TOKENS", "500"); // default to 500 if not set
            n = sharedPreferences.getString("N", "1"); // default to 1 if not set
            temperature = sharedPreferences.getString("TEMPERATURE", "0.5"); // default to 0.5 if not set
        }
    };
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
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);


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

        Retrofit retrofitGpt4Vision = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        gpt4VisionService = retrofitGpt4Vision.create(Gpt4VisionService.class);


        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_RECORD_AUDIO_PERMISSION);
        registerReceiver(dataUpdatedReceiver, new IntentFilter("com.dubaisolve.speakmate.ACTION_DATA_UPDATED"));
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister the receiver to avoid memory leaks
        unregisterReceiver(dataUpdatedReceiver);
    }
    private void initViews() {
        recordButton = findViewById(R.id.recordButton);
        // Remove other lines related to buttons that are now in the popup menu
    }

    private void initListeners() {
        // Retrieve the API key and voice ID from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");
        model = sharedPreferences.getString("MODEL","gpt-3.5-turbo");
        maxTokens = sharedPreferences.getString("MAX_TOKENS", "500");
        n = sharedPreferences.getString("N", "1");
        temperature = sharedPreferences.getString("TEMPERATURE", "0.5");

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
                                case R.id.import_image_button:
                                    importImages();
                                    return true;
                                case R.id.import_audio_button:
                                    importAudioFile();
                                    return true;
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
        } else if (item.getItemId() == R.id.action_help) {
            Intent helpIntent = new Intent(MainActivity.this, HelpActivity.class);
            startActivity(helpIntent);
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
        } else if (requestCode == IMPORT_AUDIO_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri audioUri = data.getData();
            if (audioUri != null) {
                // Call a modified transcribeAudio method that can handle a URI
                transcribeAudioWithUri(audioUri, gptApiKey);
            } else {
                Toast.makeText(this, "Failed to import audio file", Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == IMPORT_IMAGE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                // Multiple images selected
                sendImages(data.getClipData(), null, gptApiKey);
            } else if (data.getData() != null) {
                // Single image selected
                sendImages(null, data.getData(), gptApiKey);
            } else {
                Toast.makeText(this, "Failed to import images", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getFilePathFromUri(Context context, Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String documentId = DocumentsContract.getDocumentId(uri);
            String[] parts = new String[0];
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                parts = documentId.split(":");
                if ("primary".equalsIgnoreCase(parts[0])) {
                    return Environment.getExternalStorageDirectory() + "/" + parts[1];
                }
            } else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String selection = "_id=?";
                String[] selectionArgs = new String[]{parts[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String[] projection = {MediaStore.Audio.Media.DATA};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
                return cursor.getString(columnIndex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d("MainActivity", "onRestart called");
        SharedPreferences sharedPreferences = getSharedPreferences("API_KEYS", MODE_PRIVATE);
        gptApiKey = sharedPreferences.getString("GPT_API_KEY", "");
        elevenLabsApiKey = sharedPreferences.getString("ELEVEN_LABS_API_KEY", "");
        voiceId = sharedPreferences.getString("VOICE_ID", "");
        model = sharedPreferences.getString("MODEL","gpt-3.5-turbo");
        maxTokens = sharedPreferences.getString("MAX_TOKENS", "500"); // default to 500 if not set
        n = sharedPreferences.getString("N", "1"); // default to 1 if not set
        temperature = sharedPreferences.getString("TEMPERATURE", "0.5"); // default to 0.5 if not set
        Log.d("MainActivity", "Loaded data from SharedPreferences in onRestart:");
        Log.d("MainActivity", "gptApiKey: " + gptApiKey);
        Log.d("MainActivity", "elevenLabsApiKey: " + elevenLabsApiKey);
        Log.d("MainActivity", "voiceId: " + voiceId);
        Log.d("MainActivity", "model: " + model);
        Log.d("MainActivity", "maxTokens: " + maxTokens);
        Log.d("MainActivity", "n: " + n);
        Log.d("MainActivity", "temperature: " + temperature);
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
            }
        }
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
                "Describe these images and compare them, if you find any logical relevance between them then provide a short explanation if not just describe them listing 1 by 1." :
                "Describe what is in the picture, if you find a question (text) in the picture then try to answer it based on what you have captured in this picture in this case don't describe your reasoning just provide a short answer";
        textMessage.addProperty("text", promptText);
        messagesArray.add(textMessage);

        if (clipData != null) {
            // Multiple images selected
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri imageUri = clipData.getItemAt(i).getUri();
                addImageToMessagesArray(imageUri, messagesArray);
            }
        } else if (singleImageUri != null) {
            // Single image selected
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
            imageUrlObject.addProperty("detail", "high"); // Added detail for high-quality analysis
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
        requestBody.addProperty("model", "gpt-4-vision-preview");
        JsonArray newMessagesArray = new JsonArray();
        JsonObject messageObject = new JsonObject();
        messageObject.addProperty("role", "user");
        messageObject.add("content", messagesArray);
        newMessagesArray.add(messageObject);

        requestBody.add("messages", newMessagesArray);
        try {
            requestBody.addProperty("max_tokens", Integer.parseInt(maxTokens));
        } catch (NumberFormatException e) {
            requestBody.addProperty("max_tokens", 500); // Fallback to default if parsing fails
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
                        final String analysisResult = responseObject.getAsJsonArray("choices").get(0)
                                .getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
                        runOnUiThread(() -> {
                            Message aiMessage = new Message("AI", analysisResult);
                            myDataset.add(aiMessage);
                            mAdapter.notifyItemInserted(myDataset.size() - 1);
                            layoutManager.scrollToPosition(myDataset.size() - 1);
                        });
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
        String errorMessage = "Failed to analyze the images"; // Default error message
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
                        final String aiResponse = generatedText.replaceFirst("AI: ", "").replaceFirst("Assistant: ", "");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Message aiMessage = new Message("AI", aiResponse);
                                myDataset.add(aiMessage);
                                mAdapter.notifyItemInserted(myDataset.size() - 1);
                                layoutManager.scrollToPosition(myDataset.size() - 1);
                            }
                        });

                        // Call getAudioResponseFromElevenLabs if ELEVEN_LABS_API_KEY and VOICE_ID are not empty
                        if (!elevenLabsApiKey.isEmpty() && !voiceId.isEmpty()) {
                            getAudioResponseFromElevenLabs(aiResponse, elevenLabsApiKey, voiceId);
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
                            Toast.makeText(MainActivity.this, "Failed to get a response from AI: " + errorMessage, Toast.LENGTH_SHORT).show();
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
        jsonObject.addProperty("model_id", "eleven_multilingual_v2");
        JsonObject voiceSettings = new JsonObject();
        voiceSettings.addProperty("stability", 0.71);
        voiceSettings.addProperty("similarity_boost", 0.5);
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
