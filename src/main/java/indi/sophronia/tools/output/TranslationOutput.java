package indi.sophronia.tools.output;

import indi.sophronia.tools.endpoint.TranslationApiEndpoint;
import indi.sophronia.tools.util.PackageScan;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TranslationOutput extends OutputStream {
    public TranslationOutput(Properties properties) throws IOException {
        Class<?>[] classes = PackageScan.getClassesByPackageName(
                TranslationApiEndpoint.class.getPackageName(),
                klass ->
                        !klass.isInterface() &&
                                TranslationApiEndpoint.class.isAssignableFrom(klass)
        );

        List<TranslationApiEndpoint> translationApiEndpoints = new ArrayList<>(classes.length);
        for (Class<?> klass : classes) {
            TranslationApiEndpoint endpoint;
            try {
                endpoint = (TranslationApiEndpoint) klass.getConstructor().newInstance();
                if (endpoint.onLoadingValidate(properties)) {
                    translationApiEndpoints.add(endpoint);
                } else {
                    System.err.printf("fail to initialize endpoint %s\n", klass.getName());
                }
            } catch (InvocationTargetException e) {
                e.getTargetException().printStackTrace();
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
        endpoints = translationApiEndpoints.toArray(new TranslationApiEndpoint[0]);
    }

    private final StringBuilder buffer = new StringBuilder(1024);

    private final TranslationApiEndpoint[] endpoints;

    @Override
    public void write(int b) {
        buffer.appendCodePoint(b);
    }

    @Override
    public void flush() {
        String data = buffer.toString();
        buffer.delete(0, buffer.length());

        // todo load from cache first

        String translated = null;
        for (TranslationApiEndpoint endpoint : endpoints) {
            try {
                translated = endpoint.translate(data);
                if (translated != null) {
                    break;
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
        }

        if (translated != null) {
            // todo save results

            System.out.println(translated);
        } else {
            System.out.println(data);
        }
    }
}
