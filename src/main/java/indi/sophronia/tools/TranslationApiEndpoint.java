package indi.sophronia.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class TranslationApiEndpoint extends OutputStream {
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build();
    private final StringBuilder buffer = new StringBuilder(1024);

    @Override
    public void write(int b) {
        buffer.appendCodePoint(b);
    }

    @Override
    public void flush() throws IOException {
        // TODO: 2023/7/10 call api

        String data = buffer.toString();
        buffer.delete(0, buffer.length());

        CompletionRequest completionRequest = new CompletionRequest();
        completionRequest.setPrompt(prompt);

        String reqJson = moshi.adapter(CompletionRequest.class).toJson(completionRequest);
        System.out.println("reqJson: " + reqJson);
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/completions")
                // 将 API_KEY 替换成你自己的 API_KEY
                .header("Authorization", "Bearer " + API_KEY)
                .post(RequestBody.create(MEDIA_TYPE_JSON, reqJson))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);
            System.out.println(response.body().string());
        }
    }
}
