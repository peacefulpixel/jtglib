package com.example.lib.internals;

import java.util.*;

/**
 * Core points of this collection:
 *  1. Have a HashMap-like structure with limited size
 *  2. If the size reaches its limit - we just remove the oldest item from collection
 *  3. ID generating responsibility is on collection
 */
public class LimitedWrappedMap<T> {
    private final LinkedList<UUID> list = new LinkedList<>();
    private final HashMap<UUID, T> map = new HashMap<>();
    private final int maxCapacity;

    public LimitedWrappedMap(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public synchronized String add(T item) {
        final var newId = UUID.randomUUID();
        list.add(newId);
        map.put(newId, item);

        if (list.size() > maxCapacity) {
            final var itemToRemove = list.removeFirst();
            map.remove(itemToRemove);
        }

        return newId.toString();
    }

    /**
     * nullable
     * neverThrows
     */
    public synchronized T get(String id) {
        try {
            return map.get(UUID.fromString(id));
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized T remove(String id) {
        return map.remove(UUID.fromString(id));
    }

    public synchronized List<T> remove(Collection<String> ids) {
        return ids.stream()
                .map(UUID::fromString)
                .map(map::remove)
                .toList();
    }
}
