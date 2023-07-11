package indi.sophronia.tools.endpoint;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public abstract class TranslationApiEndpoint {
    private long invalidateUntil;

    public abstract boolean onLoadingValidate(Properties properties);

    public final String translate(String source) throws IOException {
        if (invalidateUntil > System.currentTimeMillis()) {
            return null;
        } else {
            return doTranslate(source);
        }
    }

    protected abstract String doTranslate(String source) throws IOException;

    public void onFail() {
        invalidateUntil = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
    }
}
