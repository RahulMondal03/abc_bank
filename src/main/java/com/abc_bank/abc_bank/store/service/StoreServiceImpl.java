package com.abc_bank.abc_bank.store.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory implementation of {@link StoreService} backed by a
 * {@link ConcurrentHashMap}.
 *
 * <p>All keys and values must be non-null; the backing map does not permit
 * nulls and a {@link IllegalArgumentException} is raised early to give callers a
 * clear error instead of a {@link NullPointerException} from deep inside the
 * map.</p>
 */
@Service
@Slf4j
public class StoreServiceImpl implements StoreService {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> put(String key, String value) {
        requireKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String previous = store.put(key, value);
        log.debug("store.put key='{}' (replaced={})", key, previous != null);
        return Optional.ofNullable(previous);
    }

    @Override
    public Optional<String> get(String key) {
        requireKey(key);
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        requireKey(key);
        return store.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(String key) {
        requireKey(key);
        return store.containsKey(key);
    }

    @Override
    public Optional<String> delete(String key) {
        requireKey(key);
        String removed = store.remove(key);
        log.debug("store.delete key='{}' (removed={})", key, removed != null);
        return Optional.ofNullable(removed);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public boolean isEmpty() {
        return store.isEmpty();
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(new HashMap<>(store).keySet());
    }

    @Override
    public Map<String, String> entries() {
        return Collections.unmodifiableMap(new HashMap<>(store));
    }

    @Override
    public void clear() {
        store.clear();
        log.debug("store.clear");
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
    }
}
