package com.example.myapplication;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface TranscriptionService {
    @Multipart
    @POST("audio/transcriptions")
    Call<ResponseBody> transcribeAudio(
            @Header("Authorization") String authorization,
            @Part("model") RequestBody model,
            @Part MultipartBody.Part file
    );
    @Multipart
    @POST("audio/translations")
    Call<ResponseBody> translateAudio(
            @Header("Authorization") String authorization,
            @Part("model") RequestBody model,
            @Part MultipartBody.Part file
    );
}



