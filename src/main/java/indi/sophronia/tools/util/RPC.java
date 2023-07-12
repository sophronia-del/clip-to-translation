package indi.sophronia.tools.util;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class RPC {
    private static final Gson gson = new Gson();

    private static OkHttpClient okHttpClient;

    public static void init(Properties properties) {
        Proxy proxy;
        String proxyPort = properties.getProperty("proxy.port");
        if (proxyPort != null) {
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(proxyPort)));
        } else {
            proxy = null;
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .callTimeout(1, TimeUnit.SECONDS);

        if (proxy != null) {
            builder.proxy(proxy);
        }

        okHttpClient = builder.build();
    }

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

    public static <T> T form(String url,
                             Map<String, String> headers,
                             Map<String, String> body,
                             Type responseType) throws IOException {
        Request.Builder builder = new Request.Builder();
        builder.url(url);
        headers.forEach(builder::addHeader);

        FormBody.Builder bodyBuilder = new FormBody.Builder();
        body.forEach(bodyBuilder::add);

        builder.post(bodyBuilder.build());

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
