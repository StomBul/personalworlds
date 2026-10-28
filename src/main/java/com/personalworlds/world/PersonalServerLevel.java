package com.personalworlds.world;

import java.util.List;
import java.util.concurrent.Executor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;

/** Уровень личного мира: свой сид генерации и своя точка спавна, не задевающая остальных игроков. */
public class PersonalServerLevel extends ServerLevel {
    /**
     * Сид нужен уже внутри конструктора ServerLevel (ChunkMap создаёт RandomState до того,
     * как поля подкласса проинициализированы), поэтому передаётся через ThreadLocal.
     */
    public static final ThreadLocal<Long> PENDING_SEED = new ThreadLocal<>();

    private final PersonalWorld world;
    private final PersonalLevelData data;
    private final long seed;
    private final boolean ready;

    public PersonalServerLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess storage,
                               PersonalLevelData data, ResourceKey<Level> key, LevelStem stem, ChunkProgressListener listener,
                               long biomeZoomSeed, PersonalWorld world, long seed) {
        super(server, executor, storage, data, key, stem, listener, false, biomeZoomSeed, List.of(), true, null);
        this.world = world;
        this.data = data;
        this.seed = seed;
        this.ready = true;
    }

    public PersonalWorld world() {
        return world;
    }

    public PersonalLevelData personalData() {
        return data;
    }

    @Override
    public long getSeed() {
        if (!ready) {
            Long pending = PENDING_SEED.get();
            if (pending != null) return pending;
            return super.getSeed();
        }
        return seed;
    }

    @Override
    public void setDefaultSpawnPos(BlockPos pos, float angle) {
        // Ванильная версия рассылает пакет всем игрокам сервера и держит спавн-чанки загруженными.
        data.setSpawn(pos, angle);
        ClientboundSetDefaultSpawnPositionPacket packet = new ClientboundSetDefaultSpawnPositionPacket(pos, angle);
        for (ServerPlayer p : players()) {
            p.connection.send(packet);
        }
    }
}
