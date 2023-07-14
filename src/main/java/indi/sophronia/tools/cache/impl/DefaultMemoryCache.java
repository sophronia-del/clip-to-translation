package indi.sophronia.tools.cache.impl;

import indi.sophronia.tools.cache.CacheFacade;
import indi.sophronia.tools.util.StringHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

public class DefaultMemoryCache implements CacheFacade {
    private static class CachedEntry {
        private final String key;
        private volatile Object value;
        private volatile long state;
        private final StampedLock lock;

        private CachedEntry(String key, Object value, long state) {
            this.key = key;
            this.value = value;
            this.state = state;
            this.lock = new StampedLock();
        }
    }

    public DefaultMemoryCache() {
        this.init = new AtomicBoolean(false);
        this.destroy = new AtomicBoolean(false);
        this.memoryCache = new ConcurrentHashMap<>();
        this.cacheCleaner = Executors.newSingleThreadScheduledExecutor();
    }

    private final AtomicBoolean init;
    private final AtomicBoolean destroy;
    private final ConcurrentMap<String, CachedEntry> memoryCache;
    private final ScheduledExecutorService cacheCleaner;

    @Override
    public void init() {
        if (init.compareAndSet(false, true)) {
            cacheCleaner.scheduleAtFixedRate(
                    () -> {
                        long timestamp = System.currentTimeMillis();
                        for (CachedEntry value : memoryCache.values()) {
                            if (value.state > 0 && value.state < timestamp) {
                                doRemoveCache(value);
                            }
                        }
                    },
                    5, 5, TimeUnit.SECONDS
            );
        }
    }

    @Override
    public void destroy() {
        if (destroy.compareAndSet(false, true)) {
            cacheCleaner.shutdown();
        }
    }

    @Override
    public Set<String> keys(String pattern) {
        if (!pattern.contains("*")) {
            Set<String> set = new HashSet<>(1);
            if (memoryCache.containsKey(pattern)) {
                set.add(pattern);
            }
            return set;
        }

        Set<String> keys = new HashSet<>(memoryCache.keySet());
        StringHelper.filterKeysByPattern(keys, pattern);
        return keys;
    }

    @Override
    public <T> void save(String key, T value, long expireMillis) {
        if (value == null) {
            invalidate(key);
            return;
        }

        long expireAt = expireMillis == 0 ?
                0 : System.currentTimeMillis() + expireMillis;
        CachedEntry cachedEntry = memoryCache.get(key);
        if (cachedEntry != null) {
            long stamp = cachedEntry.lock.writeLock();
            if (cachedEntry.state < 0) {
                memoryCache.put(key, new CachedEntry(key, value, expireAt));
            } else {
                cachedEntry.value = value;
                cachedEntry.state = expireAt;
            }
            cachedEntry.lock.unlockWrite(stamp);
        } else {
            memoryCache.put(key, new CachedEntry(key, value, expireAt));
        }
    }

    @Override
    public <T> void savePersist(String key, T value) {
        save(key, value, 0L);
    }

    @Override
    public <T> void saveBatch(Map<String, T> data, long expireMillis) {
        long expireAt = expireMillis == 0 ?
                0 : System.currentTimeMillis() + expireMillis;
        for (Map.Entry<String, T> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            save(key, value, expireAt);
        }
    }

    @Override
    public <T> void saveBatchPersist(Map<String, T> data) {
        saveBatch(data, 0L);
    }

    @Override
    public <T> T load(String key) {
        CachedEntry cachedEntry = memoryCache.get(key);
        return cachedEntry == null ? null : readValueFrom(cachedEntry);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T loadOrSave(String key, Supplier<T> defaultValue, long expireMillis) {
        return (T) loadOrSaveInternal(key, defaultValue, expireMillis)[0];
    }

    @Override
    public <T> T loadOrSavePersist(String key, Supplier<T> defaultValue) {
        return loadOrSave(key, defaultValue, 0L);
    }

    /**
     * @return [value, (true if value is supplied)]
     */
    protected Object[] loadOrSaveInternal(String key, Supplier<?> defaultValue, long expireMillis) {
        long expireAt = expireMillis == 0 ?
                0 : System.currentTimeMillis() + expireMillis;

        CachedEntry cachedEntry = memoryCache.get(key);
        if (cachedEntry == null) {
            Object value = defaultValue.get();
            if (value != null) {
                save(key, value, expireMillis);
                return new Object[]{value, true};
            } else {
                return new Object[]{null, false};
            }
        }

        Object value = readValueFrom(cachedEntry);
        if (value != null) {
            return new Object[]{value, false};
        }

        long wl = cachedEntry.lock.writeLock();
        try {
            if (cachedEntry.state < 0) {
                value = defaultValue.get();
                if (value != null) {
                    save(key, value, expireAt);
                }
                return new Object[]{value, true};
            }

            value = defaultValue.get();
            if (value == null) {
                cachedEntry.state = -1;
                memoryCache.remove(key);
                return new Object[]{null, false};
            }

            cachedEntry.state = expireAt;
            cachedEntry.value = value;
            return new Object[]{value, true};
        } finally {
            cachedEntry.lock.unlockWrite(wl);
        }
    }

    @Override
    public void invalidate(String key) {
        CachedEntry cachedEntry = memoryCache.get(key);
        if (cachedEntry != null) {
            doRemoveCache(cachedEntry);
        }
    }

    @Override
    public void invalidateBatch(Collection<String> keys) {
        for (String key : keys) {
            invalidate(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static  <T> T readValueFrom(CachedEntry cachedEntry) {
        long stamp = cachedEntry.lock.tryOptimisticRead();
        long state = cachedEntry.state;
        Object value = cachedEntry.value;

        if (!cachedEntry.lock.validate(stamp)) {
            stamp = cachedEntry.lock.readLock();
            state = cachedEntry.state;
            value = cachedEntry.value;
            cachedEntry.lock.unlockRead(stamp);
        }

        if (state == 0 || state > System.currentTimeMillis()) {
            return (T) value;
        } else {
            return null;
        }
    }

    private void doRemoveCache(CachedEntry cachedEntry) {
        long stamp = cachedEntry.lock.writeLock();
        try {
            if (cachedEntry.state >= 0) {
                cachedEntry.state = -1;
                memoryCache.remove(cachedEntry.key);
            }
        } finally {
            cachedEntry.lock.unlockWrite(stamp);
        }
    }
}
