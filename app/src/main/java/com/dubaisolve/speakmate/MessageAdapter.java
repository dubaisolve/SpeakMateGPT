package com.dubaisolve.speakmate;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private List<Message> messages;
    private OnItemClickListener onItemClickListener;
    private Context context;

    // Define the interface for item clicks
    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    // Constructor
    public MessageAdapter(Context context, List<Message> myDataset, OnItemClickListener listener) {
        this.context = context;
        this.messages = myDataset;
        this.onItemClickListener = listener;
    }

    // ViewHolder class
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView senderTextView;
        public TextView contentTextView;

        public ViewHolder(View v) {
            super(v);
            senderTextView = v.findViewById(R.id.sender);
            contentTextView = v.findViewById(R.id.content);
        }
    }

    @Override
    public MessageAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflate the message item layout
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        // Get the message
        Message message = messages.get(position);

        // Set sender and content
        holder.senderTextView.setText(message.getSender());
        holder.contentTextView.setText(message.getContent());

        // Adjust layout based on sender
        holder.itemView.post(new Runnable() {
            @Override
            public void run() {
                int maxContentWidth = (int) (holder.itemView.getWidth() * 0.8);
                holder.contentTextView.setMaxWidth(maxContentWidth);
                holder.contentTextView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;

                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.contentTextView.getLayoutParams();

                if ("User".equals(message.getSender())) {
                    // Align to the right for user messages
                    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    holder.contentTextView.setGravity(Gravity.END);
                    holder.contentTextView.setBackgroundResource(R.drawable.rounded_user_message);
                    holder.contentTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.button_icon_color));
                } else {
                    // Align to the left for AI messages
                    params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    holder.contentTextView.setGravity(Gravity.START);
                    holder.contentTextView.setBackgroundResource(R.drawable.rounded_other_message);
                    holder.contentTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.button_icon_color));
                }
                holder.contentTextView.setLayoutParams(params);
            }
        });

        // Set click listener on AI messages
        if ("AI".equals(message.getSender())) {
            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(v, holder.getAdapterPosition());
                }
            });
        } else {
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
}
