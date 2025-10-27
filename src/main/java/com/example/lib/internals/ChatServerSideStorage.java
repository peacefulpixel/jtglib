package com.example.lib.internals;

import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerSideStorage {

    private final ConcurrentHashMap<Integer, Pair<Long, Set<String>>> checkboxesByMessage = new ConcurrentHashMap<>();

    public Set<String> getCheckboxes(Integer msgId) {
        return checkboxesByMessage.computeIfAbsent(msgId, e ->
                Pair.of(System.currentTimeMillis(), new HashSet<>())).getRight();
    }

    /**
     * Cleans up all old objects if they could be considered expired.
     * This method should be invoked as often as possible
     */
    public void cleanUp() {
        final Long now = System.currentTimeMillis();
        checkboxesByMessage.entrySet().stream().filter(e -> now - e.getValue().getLeft() > 3*24*60*60*1000)
                .forEach(e -> checkboxesByMessage.remove(e.getKey()));
    }
}
