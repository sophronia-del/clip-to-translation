package indi.sophronia.tools.endpoint;

import indi.sophronia.tools.util.Language;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public abstract class TranslationApiEndpoint {
    private long invalidateUntil;

    public abstract boolean onLoadingValidate(Properties properties);

    public final String translate(String source, Language sourceLanguage, Language targetLanguage) throws IOException {
        if (invalidateUntil > System.currentTimeMillis()) {
            return null;
        } else {
            return doTranslate(source, sourceLanguage, targetLanguage);
        }
    }

    protected abstract String doTranslate(String source, Language sourceLanguage, Language targetLanguage) throws IOException;

    public void onFail() {
        invalidateUntil = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
    }
}
