package com.personalworlds.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Измерение из файла игрока при входе — чтобы отличить «мир пропал» от «устаревшей записи после сбоя». */
public final class LoginTracker {
    private static final Map<UUID, ResourceKey<Level>> LOGIN_DIM = new HashMap<>();

    public static void record(UUID id, ResourceKey<Level> key) {
        LOGIN_DIM.put(id, key);
    }

    @Nullable
    public static ResourceKey<Level> take(UUID id) {
        return LOGIN_DIM.remove(id);
    }

    private LoginTracker() {}
}
