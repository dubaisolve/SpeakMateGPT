package com.dubaisolve.speakmate;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private List<Message> messages;

    // Provide a reference to the views for each data item
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView senderTextView;
        public TextView contentTextView;

        public ViewHolder(View v) {
            super(v);
            senderTextView = v.findViewById(R.id.sender);
            contentTextView = v.findViewById(R.id.content);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MessageAdapter(List<Message> myDataset) {
        messages = myDataset;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public MessageAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        // Get element from your dataset at this position
        Message message = messages.get(position);

        // Replace the contents of the view with that element
        holder.senderTextView.setText(message.getSender());
        holder.contentTextView.setText(message.getContent());

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
                    // Align to the left for other messages
                    params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    holder.contentTextView.setGravity(Gravity.START);
                    holder.contentTextView.setBackgroundResource(R.drawable.rounded_other_message);
                    holder.contentTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.button_icon_color));
                }
                holder.contentTextView.setLayoutParams(params);
            }
        });
    }
    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return messages.size();
    }
}
