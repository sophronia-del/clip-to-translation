package indi.sophronia.tools.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public interface CacheFacade {
    default void init() {
    }

    default void destroy() {
    }


    Set<String> keys(String pattern);

    <T> void save(String key, T value, long expireMillis);

    <T> void savePersist(String key, T value);

    <T> void saveBatch(Map<String, T> data, long expireMillis);

    <T> void saveBatchPersist(Map<String, T> data);

    <T> T load(String key);

    default <T> T loadOrSave(String key, Supplier<T> defaultValue, long expireMillis) {
        T value = load(key);
        if (value != null) {
            return value;
        }

        value = defaultValue.get();
        save(key, value, expireMillis);
        return value;
    }

    default <T> T loadOrSavePersist(String key, Supplier<T> defaultValue) {
        T value = load(key);
        if (value != null) {
            return value;
        }

        value = defaultValue.get();
        savePersist(key, value);
        return value;
    }

    default <T> Map<String, T> loadBatch(Collection<String> keys) {
        Map<String, T> map = new HashMap<>(keys.size());
        for (String key : keys) {
            T value = load(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    void invalidate(String key);

    void invalidateBatch(Collection<String> keys);
}
