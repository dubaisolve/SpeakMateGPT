package com.dubaisolve.speakmate;

public class Message {
    private String sender;
    private String content;

    // Constructor
    public Message(String sender, String content) {
        this.sender = sender;
        this.content = content;
    }

    // Getter methods
    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    // Setter method for content
    public void setContent(String content) {
        this.content = content;
    }
}
