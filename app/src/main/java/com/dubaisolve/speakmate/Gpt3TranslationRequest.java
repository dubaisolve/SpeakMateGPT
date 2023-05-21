package com.dubaisolve.speakmate;
public class Gpt3TranslationRequest {
    private String text;
    private String source_lang;
    private String target_lang;
    private String prompt;
    private int max_tokens;

    public Gpt3TranslationRequest(String text, String source_lang, String target_lang, String prompt, int max_tokens) {
        this.text = text;
        this.source_lang = source_lang;
        this.target_lang = target_lang;
        this.prompt = prompt;
        this.max_tokens = max_tokens;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getMax_tokens() {
        return max_tokens;
    }

    public void setMax_tokens(int max_tokens) {
        this.max_tokens = max_tokens;
    }
}

