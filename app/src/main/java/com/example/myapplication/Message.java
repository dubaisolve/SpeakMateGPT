package com.example.myapplication;
public class Message {
    public enum Role {
        USER,
        ASSISTANT
    }

    private String text;
    private long timestamp;
    private Role role;

    public Message(String text, long timestamp, Role role) {
        this.text = text;
        this.timestamp = timestamp;
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
