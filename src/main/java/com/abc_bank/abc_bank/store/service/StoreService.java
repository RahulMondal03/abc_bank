package com.abc_bank.abc_bank.store.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A simple, thread-safe in-memory key/value store.
 *
 * <p>The store keeps {@code String} keys mapped to {@code String} values and
 * exposes the usual create/read/update/delete style operations. It is intended
 * as a lightweight, dependency-free store (no database or network required) that
 * can back caches, feature flags, short-lived tokens or other transient data
 * used across the application.</p>
 */
public interface StoreService {

    /**
     * Stores {@code value} under {@code key}, creating a new entry or replacing
     * the existing one (create / update).
     *
     * @param key   the non-null key
     * @param value the non-null value
     * @return the previous value associated with {@code key}, or an empty
     * {@link Optional} if there was no mapping
     */
    Optional<String> put(String key, String value);

    /**
     * Reads the value associated with {@code key}.
     *
     * @param key the non-null key
     * @return the value wrapped in an {@link Optional}, or empty if absent
     */
    Optional<String> get(String key);

    /**
     * Reads the value associated with {@code key}, falling back to
     * {@code defaultValue} when the key is absent.
     */
    String getOrDefault(String key, String defaultValue);

    /**
     * @return {@code true} if the store contains a mapping for {@code key}
     */
    boolean containsKey(String key);

    /**
     * Removes the mapping for {@code key} if present (delete).
     *
     * @return the value that was removed, or empty if the key was absent
     */
    Optional<String> delete(String key);

    /**
     * @return the number of entries currently held by the store
     */
    int size();

    /**
     * @return {@code true} when the store holds no entries
     */
    boolean isEmpty();

    /**
     * @return an immutable snapshot of the keys currently held by the store
     */
    Set<String> keys();

    /**
     * @return an immutable snapshot of every entry currently held by the store
     */
    Map<String, String> entries();

    /**
     * Removes every entry from the store.
     */
    void clear();
}
