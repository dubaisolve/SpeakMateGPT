package com.example.myapplication;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
public interface Gpt3Service {
    @POST("chat/completions")
    Call<ResponseBody> chatCompletion(
            @Header("Authorization") String authorization,
            @Body RequestBody requestBody
    );
}
