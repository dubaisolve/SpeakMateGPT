package com.example.myapplication;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import android.os.Handler;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final long WARNING_THRESHOLD_BYTES = (long) (MAX_FILE_SIZE_BYTES * 0.9); // 90% of 25 MB

    private static String fileName = null;
    private MediaRecorder recorder = null;

    private ProgressDialog progressDialog;

    private Button recordButton, stopButton, transcribeButton , translateButton;
    private TextView transcriptionTextView;

    private Handler fileSizeCheckHandler = new Handler();
    private void checkFileSizeAndWarn() {
        File audioFile = new File(fileName);
        if (audioFile.length() > WARNING_THRESHOLD_BYTES) {
            Toast.makeText(this, "Recording is reaching the maximum size limit. Please wrap up soon.", Toast.LENGTH_LONG).show();
        }
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

        recordButton = findViewById(R.id.recordButton);
        stopButton = findViewById(R.id.stopButton);
        transcribeButton = findViewById(R.id.transcribeButton);
        translateButton = findViewById(R.id.translateButton); // Added this line
        transcriptionTextView = findViewById(R.id.transcriptionTextView);

        fileName = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/audio.mp3";
        transcriptionService = createTranscriptionService();
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_RECORD_AUDIO_PERMISSION);

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

        transcribeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transcribeAudio();
            }
        });

        translateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                translateAudio();
            }
        });
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



    private void transcribeAudio() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Transcribing audio...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String token = "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"; // Replace with your OpenAI API key
        String model = "whisper-1";

        File audioFile = new File(fileName);
        RequestBody requestFile = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);

        RequestBody modelPart = RequestBody.create(model, MediaType.parse("text/plain"));

        transcriptionService.transcribeAudio("Bearer " + token, modelPart, body).enqueue(new Callback<ResponseBody>() {
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
    private void translateAudio() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Translating audio...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String token = "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"; // Replace with your OpenAI API key
        String model = "whisper-1";

        File audioFile = new File(fileName);
        RequestBody requestFile = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);

        RequestBody modelPart = RequestBody.create(model, MediaType.parse("text/plain"));

        transcriptionService.translateAudio("Bearer " + token, modelPart, body).enqueue(new Callback<ResponseBody>() {
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

}
