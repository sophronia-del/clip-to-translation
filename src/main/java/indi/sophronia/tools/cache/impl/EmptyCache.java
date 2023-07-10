package indi.sophronia.tools.cache.impl;

import indi.sophronia.tools.cache.CacheFacade;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class EmptyCache implements CacheFacade {
    @Override
    public Set<String> keys(String pattern) {
        return Collections.emptySet();
    }

    @Override
    public <T> void save(String key, T value, long expireMillis) {
    }

    @Override
    public <T> void savePersist(String key, T value) {
    }

    @Override
    public <T> void saveBatch(Map<String, T> data, long expireMillis) {
    }

    @Override
    public <T> void saveBatchPersist(Map<String, T> data) {
    }

    @Override
    public <T> T load(String key) {
        return null;
    }

    @Override
    public <T> Map<String, T> loadBatch(Collection<String> keys) {
        return Collections.emptyMap();
    }

    @Override
    public void invalidate(String key) {
    }

    @Override
    public void invalidateBatch(Collection<String> keys) {
    }
}
