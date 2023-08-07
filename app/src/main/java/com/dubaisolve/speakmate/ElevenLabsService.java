package com.dubaisolve.speakmate;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Part;
import retrofit2.Call;

public interface ElevenLabsService {
    @POST("v1/text-to-speech/{voice_id}")
    Call<ResponseBody> textToSpeech(
            @Path("voice_id") String voiceId,
            @Header("xi-api-key") String apiKey,
            @Body RequestBody requestBody);

    @Multipart
    @POST("v1/voices/add")
    Call<ResponseBody> addVoice(
            @Header("xi-api-key") String apiKey,
            @Part("name") RequestBody name,
            @Part MultipartBody.Part file);
    @GET("v1/voices")
    Call<SettingsActivity.VoicesResponse> getVoices(@Header("xi-api-key") String apiKey);
}

