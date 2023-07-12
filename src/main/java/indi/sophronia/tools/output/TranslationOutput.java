package indi.sophronia.tools.output;

import indi.sophronia.tools.cache.impl.BufferedCache;
import indi.sophronia.tools.cache.impl.FileCache;
import indi.sophronia.tools.endpoint.TranslationApiEndpoint;
import indi.sophronia.tools.util.PackageScan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class TranslationOutput extends OutputStream {
    public TranslationOutput(Properties properties, Charset charset) throws IOException {
        FileCache fileCache = new FileCache(
                properties.getProperty("cache.file", "cache/cache")
        );
        this.cache.setUpstream(fileCache);
        this.cache.setRecycleBin(fileCache);
        this.cache.setAsyncUpdateUpstream(true);
        this.cache.init();

        Class<?>[] classes = PackageScan.getClassesByPackageName(
                TranslationApiEndpoint.class.getPackageName(),
                klass ->
                        !Modifier.isAbstract(klass.getModifiers()) &&
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
        this.endpoints = translationApiEndpoints.toArray(new TranslationApiEndpoint[0]);

        this.charset = charset;
    }

    private final Charset charset;

    private final BufferedCache cache = new BufferedCache();

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);

    private final TranslationApiEndpoint[] endpoints;

    @Override
    public void close() throws IOException {
        this.cache.destroy();
        super.close();
    }

    @Override
    public void write(int b) {
        buffer.write(b);
    }

    @Override
    public void flush() {
        String data = buffer.toString(charset);
        buffer.reset();

        String cached = cache.load(data);
        if (cached != null) {
            System.out.println(cached);
            return;
        }

        String translated = null;
        int index = -1;
        for (int i = 0; i < endpoints.length; i++) {
            try {
                translated = endpoints[i].translate(data);
                if (translated != null) {
                    index = i;
                    break;
                }
            } catch (IOException e) {
                endpoints[i].onFail();
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
        }

        if (index > 0) {
            synchronized (endpoints) {
                TranslationApiEndpoint success = endpoints[index];
                System.arraycopy(endpoints, 0, endpoints, index, endpoints.length - index);
                endpoints[0] = success;
            }
        }

        if (translated != null) {
            cache.save(data, translated, TimeUnit.MINUTES.toMillis(1));
            System.out.println(translated);
        } else {
            System.err.println("fail to translate " + data);
            System.out.println(data);
        }
    }
}
