package indi.sophronia.tools.endpoint;

import indi.sophronia.tools.util.Language;
import indi.sophronia.tools.util.RPC;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class GptEndpoint extends TranslationApiEndpoint {
    private String apiKey;

    @Override
    public boolean onLoadingValidate(Properties properties) {
        apiKey = properties.getProperty("api.key.gpt");
        return apiKey != null;
    }

    @Override
    protected String doTranslate(String source, Language sourceLanguage, Language targetLanguage) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "text-davinci-001");
        requestBody.put("max_tokens", 256);
        requestBody.put("temperature", 0.5f);
        requestBody.put("top_p", 1);
        requestBody.put("n", 1);
        requestBody.put("stream", false);
        requestBody.put("prompt", String.format("please translate the following text to %s: %s", targetLanguage, source));
        Map<String, Object> response = RPC.post(
                "https://api.openai.com/v1/completions",
                Map.of("Authorization", "Bearer " + apiKey),
                requestBody,
                Map.class
        );

        return (String) ((Map<?, ?>) ((List<?>) response.get("choices")).get(0)).get("text");
    }
}
