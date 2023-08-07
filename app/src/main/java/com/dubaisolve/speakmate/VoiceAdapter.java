package com.dubaisolve.speakmate;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.IOException;
import java.util.List;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.ViewHolder> {

    private static final String TAG = "VoiceAdapter";
    private MediaPlayer mediaPlayer;
    private List<Voice> voices;


    public VoiceAdapter(List<Voice> voices) {
        this.voices = voices;
        this.mediaPlayer = new MediaPlayer();
    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.voice_item, parent, false);
        return new ViewHolder(view);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Voice voice = voices.get(position);
        holder.voiceName.setText(voice.getName());
        holder.voiceId.setText(voice.getVoiceId());
        holder.voiceId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) holder.itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Voice ID", voice.getVoiceId());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(holder.itemView.getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle the play/pause functionality
        holder.playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    holder.playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    try {
                        mediaPlayer.reset();
                        mediaPlayer.setDataSource(voice.getPreviewUrl());
                        mediaPlayer.prepare();
                        mediaPlayer.setOnCompletionListener(mp -> {
                            holder.playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                            mediaPlayer.reset();
                        });
                        mediaPlayer.start();
                        holder.playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
    @Override
    public int getItemCount() {
        return voices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView voiceId;
        TextView voiceName;
        ImageButton playPauseButton;
        ViewHolder(View itemView) {
            super(itemView);
            voiceId = itemView.findViewById(R.id.voice_id);
            voiceName = itemView.findViewById(R.id.voice_name);
            playPauseButton = itemView.findViewById(R.id.play_pause_button);
        }
    }
}
