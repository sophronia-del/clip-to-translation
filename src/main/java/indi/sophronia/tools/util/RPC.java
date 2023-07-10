package indi.sophronia.tools.util;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RPC {
    private static final Gson gson = new Gson();

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build();

    public static <T> T post(String url,
                             Map<String, String> headers,
                             Object body,
                             Type responseType) throws IOException {
        Request.Builder builder = new Request.Builder();
        builder.url(url);
        builder.post(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")));
        headers.forEach(builder::addHeader);

        Request request = builder.build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String message = String.format(
                        "fail to request %s: %d %s",
                        url, response.code(), response.message()
                );
                throw new IOException(message);
            }

            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                return gson.fromJson(responseBody.charStream(), responseType);
            } else {
                String message = "fail to read response content from " + url;
                throw new IOException(message);
            }
        }
    }
}
