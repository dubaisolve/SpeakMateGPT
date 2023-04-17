package com.example.myapplication;

public class Voice {
    private String voiceId;
    private String name;
    private String previewUrl;

    public Voice(String voiceId, String name, String previewUrl) {
        this.voiceId = voiceId;
        this.name = name;
        this.previewUrl = previewUrl;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public String getName() {
        return name;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }
}

