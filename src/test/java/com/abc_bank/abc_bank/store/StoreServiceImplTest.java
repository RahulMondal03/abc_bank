package com.abc_bank.abc_bank.store;

import com.abc_bank.abc_bank.store.service.StoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link StoreServiceImpl}. No Spring context and no
 * database are required, so these run fast and in isolation.
 */
class StoreServiceImplTest {

    private StoreServiceImpl store;

    @BeforeEach
    void setUp() {
        store = new StoreServiceImpl();
    }

    @Test
    @DisplayName("put stores a value that can be read back")
    void putThenGet() {
        Optional<String> previous = store.put("account:1", "ACTIVE");

        assertTrue(previous.isEmpty(), "no previous value expected on first put");
        assertEquals(Optional.of("ACTIVE"), store.get("account:1"));
        assertEquals(1, store.size());
        assertFalse(store.isEmpty());
    }

    @Test
    @DisplayName("put on an existing key updates and returns the previous value")
    void putUpdatesExisting() {
        store.put("k", "v1");

        Optional<String> previous = store.put("k", "v2");

        assertEquals(Optional.of("v1"), previous);
        assertEquals(Optional.of("v2"), store.get("k"));
        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("get returns empty for an unknown key")
    void getMissing() {
        assertTrue(store.get("missing").isEmpty());
    }

    @Test
    @DisplayName("getOrDefault falls back when the key is absent")
    void getOrDefault() {
        store.put("present", "yes");

        assertEquals("yes", store.getOrDefault("present", "fallback"));
        assertEquals("fallback", store.getOrDefault("absent", "fallback"));
    }

    @Test
    @DisplayName("containsKey reflects presence")
    void containsKey() {
        store.put("k", "v");

        assertTrue(store.containsKey("k"));
        assertFalse(store.containsKey("nope"));
    }

    @Test
    @DisplayName("delete removes the mapping and returns the removed value")
    void delete() {
        store.put("k", "v");

        Optional<String> removed = store.delete("k");

        assertEquals(Optional.of("v"), removed);
        assertFalse(store.containsKey("k"));
        assertTrue(store.isEmpty());
    }

    @Test
    @DisplayName("delete of an unknown key returns empty")
    void deleteMissing() {
        assertTrue(store.delete("ghost").isEmpty());
    }

    @Test
    @DisplayName("keys and entries return snapshots of the current state")
    void keysAndEntries() {
        store.put("a", "1");
        store.put("b", "2");

        Set<String> keys = store.keys();
        Map<String, String> entries = store.entries();

        assertEquals(Set.of("a", "b"), keys);
        assertEquals(Map.of("a", "1", "b", "2"), entries);
    }

    @Test
    @DisplayName("keys snapshot is immutable")
    void keysSnapshotIsImmutable() {
        store.put("a", "1");

        assertThrows(UnsupportedOperationException.class, () -> store.keys().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> store.entries().put("x", "y"));
    }

    @Test
    @DisplayName("clear removes every entry")
    void clear() {
        store.put("a", "1");
        store.put("b", "2");

        store.clear();

        assertEquals(0, store.size());
        assertTrue(store.isEmpty());
    }

    @Test
    @DisplayName("null key or value is rejected")
    void nullArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> store.put(null, "v"));
        assertThrows(IllegalArgumentException.class, () -> store.put("k", null));
        assertThrows(IllegalArgumentException.class, () -> store.get(null));
        assertThrows(IllegalArgumentException.class, () -> store.delete(null));
        assertThrows(IllegalArgumentException.class, () -> store.containsKey(null));
    }

    @Test
    @DisplayName("concurrent puts are all recorded (thread-safety smoke test)")
    void concurrentPuts() throws InterruptedException {
        int threads = 8;
        int perThread = 500;
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            workers[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    store.put("t" + id + ":" + i, "v");
                }
            });
        }
        for (Thread w : workers) {
            w.start();
        }
        for (Thread w : workers) {
            w.join();
        }
        assertEquals(threads * perThread, store.size());
    }
}
