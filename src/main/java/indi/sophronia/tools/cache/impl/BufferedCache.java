package indi.sophronia.tools.cache.impl;

import indi.sophronia.tools.cache.CacheFacade;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class BufferedCache extends DefaultMemoryCache {
    private static final long EXPIRE_FOR_BUFFER = TimeUnit.SECONDS.toMillis(5);

    private final ExecutorService asyncUpdate = Executors.newFixedThreadPool(4);

    private CacheFacade upstream;

    private boolean asyncUpdateUpstream;

    public void setUpstream(CacheFacade upstream) {
        this.upstream = upstream;
    }

    public void setAsyncUpdateUpstream(boolean asyncUpdateUpstream) {
        this.asyncUpdateUpstream = asyncUpdateUpstream;
    }

    @Override
    public void init() {
        super.init();
        upstream.init();
    }

    @Override
    public void destroy() {
        super.destroy();
        upstream.destroy();
        asyncUpdate.shutdown();
    }

    @Override
    public Set<String> keys(String pattern) {
        Set<String> cacheKeys = new HashSet<>();
        cacheKeys.addAll(super.keys(pattern));
        cacheKeys.addAll(upstream.keys(pattern));
        return cacheKeys;
    }

    @Override
    public <T> T load(String key) {
        return super.loadOrSave(
                key,
                () -> upstream.load(key),
                EXPIRE_FOR_BUFFER
        );
    }

    @Override
    public <T> Map<String, T> loadBatch(Collection<String> keys) {
        Map<String, T> inMemory = super.loadBatch(keys);

        Set<String> inKeys = keys instanceof Set ? (Set<String>) keys : new HashSet<>(keys);
        Set<String> missing = new HashSet<>(inKeys);
        missing.removeAll(inMemory.keySet());

        Map<String, T> fromRemote = upstream.loadBatch(missing);
        saveBatch(fromRemote, EXPIRE_FOR_BUFFER);

        Map<String, T> results = new HashMap<>(inMemory.size() + fromRemote.size());
        results.putAll(inMemory);
        results.putAll(fromRemote);
        return results;
    }

    @Override
    protected Object[] loadOrSaveInternal(String key, Supplier<?> defaultValue, long expireMillis) {
        Object[] pair = super.loadOrSaveInternal(key, defaultValue, expireMillis);
        if (Boolean.TRUE.equals(pair[1])) {
            if (asyncUpdateUpstream) {
                asyncUpdate.execute(() -> upstream.save(key, pair[0], expireMillis));
            } else {
                upstream.save(key, pair[0], expireMillis);
            }
        }
        return pair;
    }

    @Override
    public <T> void save(String key, T value, long expireMillis) {
        super.save(key, value, expireMillis);
        if (asyncUpdateUpstream) {
            asyncUpdate.execute(() -> upstream.save(key, value, expireMillis));
        } else {
            upstream.save(key, value, expireMillis);
        }
    }

    @Override
    public <T> void savePersist(String key, T value) {
        super.savePersist(key, value);
        if (asyncUpdateUpstream) {
            asyncUpdate.execute(() -> upstream.savePersist(key, value));
        } else {
            upstream.savePersist(key, value);
        }
    }

    @Override
    public <T> void saveBatch(Map<String, T> data, long expireMillis) {
        super.saveBatch(data, expireMillis);
        if (asyncUpdateUpstream) {
            asyncUpdate.execute(() -> upstream.saveBatch(data, expireMillis));
        } else {
            upstream.saveBatch(data, expireMillis);
        }
    }

    @Override
    public <T> void saveBatchPersist(Map<String, T> data) {
        super.saveBatchPersist(data);
        if (asyncUpdateUpstream) {
            asyncUpdate.execute(() -> upstream.saveBatchPersist(data));
        } else {
            upstream.saveBatchPersist(data);
        }
    }

    @Override
    public void invalidate(String key) {
        super.invalidate(key);
        if (asyncUpdateUpstream) {
            asyncUpdate.execute(() -> upstream.invalidate(key));
        } else {
            upstream.invalidate(key);
        }
    }

    @Override
    public void invalidateBatch(Collection<String> keys) {
        super.invalidateBatch(keys);
        upstream.invalidateBatch(keys);
    }
}
