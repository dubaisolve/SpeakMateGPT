package com.dubaisolve.speakmate;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class VoiceSamplesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_samples);
        List<Voice> voices = getIntent().getParcelableArrayListExtra("voices");
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        VoiceAdapter voiceAdapter = new VoiceAdapter(voices);
        recyclerView.setAdapter(voiceAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }
}
