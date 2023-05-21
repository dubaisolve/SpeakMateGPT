package com.dubaisolve.speakmate;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ElevenLabsService {
    @POST("v1/text-to-speech/{voice_id}")
    Call<ResponseBody> textToSpeech(
            @Path("voice_id") String voiceId,
            @Header("xi-api-key") String apiKey,
            @Body RequestBody requestBody);
}

