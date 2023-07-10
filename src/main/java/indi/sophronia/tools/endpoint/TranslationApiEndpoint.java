package indi.sophronia.tools.endpoint;

import java.io.IOException;
import java.util.Properties;

public interface TranslationApiEndpoint {
    boolean onLoadingValidate(Properties properties);

    String translate(String source) throws IOException;
}
