package com.dubaisolve.speakmate;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class Voice implements Parcelable {
    @SerializedName("voice_id")
    private String voiceId;

    @SerializedName("name")
    private String name;

    @SerializedName("preview_url")
    private String previewUrl;

    public Voice(String voiceId, String name, String previewUrl) {
        this.voiceId = voiceId;
        this.name = name;
        this.previewUrl = previewUrl;
    }

    protected Voice(Parcel in) {
        voiceId = in.readString();
        name = in.readString();
        previewUrl = in.readString();
    }

    public static final Creator<Voice> CREATOR = new Creator<Voice>() {
        @Override
        public Voice createFromParcel(Parcel in) {
            return new Voice(in);
        }

        @Override
        public Voice[] newArray(int size) {
            return new Voice[size];
        }
    };

    public String getVoiceId() {
        return voiceId;
    }

    public String getName() {
        return name;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(voiceId);
        parcel.writeString(name);
        parcel.writeString(previewUrl);
    }
}
