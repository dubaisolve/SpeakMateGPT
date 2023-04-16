package com.example.myapplication;

import com.google.gson.JsonObject;

public class Gpt3TranslationResponse {
    private String id;
    private String object;
    private JsonObject choices;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public JsonObject getChoices() {
        return choices;
    }

    public void setChoices(JsonObject choices) {
        this.choices = choices;
    }
}

