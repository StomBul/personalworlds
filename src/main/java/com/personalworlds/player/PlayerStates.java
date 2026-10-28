package com.personalworlds.player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.personalworlds.PersonalWorlds;
import com.personalworlds.world.WorldManager;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Кэш и файлы состояний игроков. Каждый перенос инвентаря сразу пишется на диск. */
public final class PlayerStates {
    private static final Map<UUID, PlayerState> CACHE = new HashMap<>();

    private static Path file(UUID id) {
        return WorldManager.get().dataDir().resolve("players").resolve(id + ".dat");
    }

    public static PlayerState get(UUID id) {
        return CACHE.computeIfAbsent(id, PlayerStates::read);
    }

    private static PlayerState read(UUID id) {
        Path f = file(id);
        if (Files.exists(f)) {
            try {
                return PlayerState.load(NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()));
            } catch (IOException e) {
                PersonalWorlds.LOG.error("Не удалось прочитать {}", f, e);
            }
        }
        return new PlayerState();
    }

    public static void save(UUID id) {
        PlayerState s = CACHE.get(id);
        if (s != null) WorldManager.writeAtomic(file(id), s.save());
    }

    public static void unload(UUID id) {
        save(id);
        CACHE.remove(id);
    }

    public static void saveAll() {
        CACHE.keySet().forEach(PlayerStates::save);
    }

    public static void clear() {
        CACHE.clear();
    }

    private PlayerStates() {}
}
