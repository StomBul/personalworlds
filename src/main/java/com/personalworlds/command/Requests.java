package com.personalworlds.command;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.personalworlds.config.PWConfig;

/** Запросы на вход в мир: владелец мира → (кто просится → когда истекает). Не сохраняются между перезапусками. */
public final class Requests {
    private static final Map<UUID, Map<UUID, Long>> PENDING = new HashMap<>();

    public static void add(UUID worldOwner, UUID requester) {
        long expires = System.currentTimeMillis() + PWConfig.REQUEST_TIMEOUT_SECONDS.get() * 1000L;
        PENDING.computeIfAbsent(worldOwner, k -> new LinkedHashMap<>()).put(requester, expires);
    }

    public static boolean take(UUID worldOwner, UUID requester) {
        Map<UUID, Long> m = PENDING.get(worldOwner);
        if (m == null) return false;
        Long exp = m.remove(requester);
        return exp != null && exp >= System.currentTimeMillis();
    }

    public static Map<UUID, Long> of(UUID worldOwner) {
        Map<UUID, Long> m = PENDING.get(worldOwner);
        if (m == null) return Map.of();
        long now = System.currentTimeMillis();
        m.values().removeIf(exp -> exp < now);
        return m;
    }

    public static void clear() {
        PENDING.clear();
    }

    private Requests() {}
}
