package com.dubaisolve.speakmate;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.ViewHolder> {

    private List<Voice> voices;
    private OnItemClickListener onItemClickListener;
    public interface OnItemClickListener {
        void onItemClick(String previewUrl);
    }
    public VoiceAdapter(List<Voice> voices, OnItemClickListener onItemClickListener) {
        this.voices = voices;
        this.onItemClickListener = onItemClickListener;

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
        holder.itemView.setOnClickListener(v -> onItemClickListener.onItemClick(voice.getPreviewUrl()));
        holder.voiceId.setText(voice.getVoiceId());
    }
    @Override
    public int getItemCount() {
        return voices.size();
    }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView voiceId;
        TextView voiceName;

        ViewHolder(View itemView) {
            super(itemView);
            voiceId = itemView.findViewById(R.id.voice_id);
            voiceName = itemView.findViewById(R.id.voice_name);
        }
    }
}
