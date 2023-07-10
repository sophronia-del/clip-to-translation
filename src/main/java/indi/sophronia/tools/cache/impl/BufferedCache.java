package indi.sophronia.tools.cache.impl;

import com.google.common.collect.Sets;
import indi.sophronia.tools.cache.CacheFacade;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class BufferedCache extends DefaultMemoryCache {
    private static final long EXPIRE_FOR_BUFFER = TimeUnit.SECONDS.toMillis(5);

    private CacheFacade upstream;

    public void setUpstream(CacheFacade upstream) {
        this.upstream = upstream;
    }

    @Override
    public void init() {
        upstream.init();
    }

    @Override
    public void destroy() {
        upstream.destroy();
    }

    @Override
    public Set<String> keys(String pattern) {
        return upstream.keys(pattern);
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
        Set<String> missing = Sets.difference(inKeys, inMemory.keySet()).immutableCopy();

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
            upstream.save(key, pair[0], expireMillis);
        }
        return pair;
    }

    @Override
    public <T> void save(String key, T value, long expireMillis) {
        super.save(key, value, expireMillis);
        upstream.save(key, value, expireMillis);
    }

    @Override
    public <T> void savePersist(String key, T value) {
        super.savePersist(key, value);
        upstream.savePersist(key, value);
    }

    @Override
    public <T> void saveBatch(Map<String, T> data, long expireMillis) {
        super.saveBatch(data, expireMillis);
        upstream.saveBatch(data, expireMillis);
    }

    @Override
    public <T> void saveBatchPersist(Map<String, T> data) {
        super.saveBatchPersist(data);
        upstream.saveBatchPersist(data);
    }

    @Override
    public void invalidate(String key) {
        super.invalidate(key);
        upstream.invalidate(key);
    }

    @Override
    public void invalidateBatch(Collection<String> keys) {
        super.invalidateBatch(keys);
        upstream.invalidateBatch(keys);
    }
}
