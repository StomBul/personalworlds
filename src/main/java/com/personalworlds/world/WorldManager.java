package com.personalworlds.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.personalworlds.PersonalWorlds;
import com.personalworlds.config.PWConfig;
import com.personalworlds.gen.FlatPresets;
import com.personalworlds.gen.LayeredFlatGenerator;
import com.personalworlds.player.PlayerFlow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Хранилище личных миров и управление их измерениями: создание ServerLevel на лету, выгрузка пустых миров,
 * удаление папок. Данные: &lt;мир&gt;/personalworlds/worlds.dat, сами миры: &lt;мир&gt;/dimensions/personalworlds/.
 */
public final class WorldManager {
    private static WorldManager instance;

    private final MinecraftServer server;
    private final Path dir;
    private final Map<UUID, PersonalWorld> byOwner = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, PersonalWorld> byKey = new HashMap<>();
    private final Map<UUID, Integer> limits = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<ResourceKey<Level>, Long> emptySince = new HashMap<>();
    private final List<PendingDelete> pendingDeletes = new ArrayList<>();
    private boolean dirty;
    private int tickCounter;

    private record PendingDelete(Path path, long notBefore, int attempt) {}

    private WorldManager(MinecraftServer server) {
        this.server = server;
        this.dir = server.getWorldPath(LevelResource.ROOT).resolve(PersonalWorlds.MODID);
    }

    public static void start(MinecraftServer server) {
        instance = new WorldManager(server);
        instance.loadData();
    }

    public static void stop() {
        if (instance != null) {
            instance.saveData();
            instance = null;
        }
    }

    public static WorldManager get() {
        return instance;
    }

    public MinecraftServer server() {
        return server;
    }

    public Path dataDir() {
        return dir;
    }

    public static boolean isPersonal(@Nullable Level level) {
        return level instanceof PersonalServerLevel;
    }

    public static boolean isPersonalKey(ResourceKey<Level> key) {
        return key.location().getNamespace().equals(PersonalWorlds.MODID) && key.location().getPath().startsWith("w_");
    }

    // ---------------------------------------------------------------- данные

    @Nullable
    public PersonalWorld ofOwner(UUID owner) {
        return byOwner.get(owner);
    }

    @Nullable
    public PersonalWorld ofKey(ResourceKey<Level> key) {
        return byKey.get(key);
    }

    public Collection<PersonalWorld> all() {
        return byOwner.values();
    }

    public void markDirty() {
        dirty = true;
    }

    public int maxBorder(UUID owner) {
        return Math.max(PWConfig.MAX_BORDER.get(), limits.getOrDefault(owner, 0));
    }

    public void setLimit(UUID owner, int size) {
        limits.put(owner, size);
        markDirty();
    }

    public void rememberName(UUID id, String name) {
        if (!name.equals(names.put(id, name))) {
            PersonalWorld w = byOwner.get(id);
            if (w != null) w.ownerName = name;
            markDirty();
        }
    }

    public String nameOf(UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        String n = names.get(id);
        if (n != null) return n;
        if (server.getProfileCache() != null) {
            Optional<GameProfile> gp = server.getProfileCache().get(id);
            if (gp.isPresent()) return gp.get().getName();
        }
        return id.toString().substring(0, 8);
    }

    /** Ник → UUID: онлайн-игроки, известные моду ники, кэш профилей сервера. */
    @Nullable
    public UUID resolve(String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) return p.getUUID();
        for (var e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> gp = server.getProfileCache().get(name);
            if (gp.isPresent()) return gp.get().getId();
        }
        return null;
    }

    public Collection<String> knownNames() {
        return names.values();
    }

    private void index(PersonalWorld w) {
        byOwner.put(w.owner, w);
        byKey.put(w.levelKey(), w);
    }

    private void loadData() {
        Path file = dir.resolve("worlds.dat");
        if (!Files.exists(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            for (Tag t : root.getList("Worlds", Tag.TAG_COMPOUND)) {
                index(PersonalWorld.load((CompoundTag) t));
            }
            for (Tag t : root.getList("Limits", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                limits.put(c.getUUID("Id"), c.getInt("Size"));
            }
            for (Tag t : root.getList("Names", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                names.put(c.getUUID("Id"), c.getString("Name"));
            }
            PersonalWorlds.LOG.info("Загружено личных миров: {}", byOwner.size());
        } catch (IOException e) {
            PersonalWorlds.LOG.error("Не удалось прочитать {}", file, e);
            return;
        }
        scheduleOrphanCleanup();
    }

    /**
     * Папки удалённых/пересозданных миров, которые не успели удалиться до остановки сервера.
     * Вызывается только после успешного чтения worlds.dat, чтобы при повреждённом файле ничего не стереть.
     */
    private void scheduleOrphanCleanup() {
        Path root = server.storageSource.getDimensionPath(PersonalWorld.levelKey(new UUID(0, 0), 0)).getParent();
        if (root == null || !Files.isDirectory(root)) return;
        Set<String> known = new HashSet<>();
        byOwner.values().forEach(w -> known.add(w.levelKey().location().getPath()));
        try (Stream<Path> list = Files.list(root)) {
            for (Path p : list.toList()) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p) && name.startsWith("w_") && !known.contains(name)) {
                    PersonalWorlds.LOG.info("Найдена папка удалённого мира {}, будет удалена", p);
                    pendingDeletes.add(new PendingDelete(p, 0, 0));
                }
            }
        } catch (IOException e) {
            PersonalWorlds.LOG.warn("Не удалось просмотреть {}: {}", root, e.toString());
        }
    }

    public void saveData() {
        for (PersonalWorld w : byOwner.values()) {
            PersonalServerLevel level = loaded(w);
            if (level != null) w.levelData = level.personalData().save();
        }
        CompoundTag root = new CompoundTag();
        ListTag worlds = new ListTag();
        byOwner.values().forEach(w -> worlds.add(w.save()));
        root.put("Worlds", worlds);
        ListTag lim = new ListTag();
        limits.forEach((id, size) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", id);
            c.putInt("Size", size);
            lim.add(c);
        });
        root.put("Limits", lim);
        ListTag nm = new ListTag();
        names.forEach((id, name) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", id);
            c.putString("Name", name);
            nm.add(c);
        });
        root.put("Names", nm);
        writeAtomic(dir.resolve("worlds.dat"), root);
        dirty = false;
    }

    public static void writeAtomic(Path file, CompoundTag tag) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(tag, tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            PersonalWorlds.LOG.error("Не удалось записать {}", file, e);
        }
    }

    // ---------------------------------------------------------------- создание / удаление

    public PersonalWorld create(ServerPlayer owner, WorldType type, @Nullable FlatPresets.Preset preset, @Nullable String customLayers) {
        PersonalWorld w = new PersonalWorld();
        w.id = UUID.randomUUID();
        w.owner = owner.getUUID();
        w.ownerName = owner.getGameProfile().getName();
        w.displayName = w.ownerName;
        w.borderSize = Math.min(PWConfig.DEFAULT_BORDER.get(), maxBorder(w.owner));
        w.createdAt = System.currentTimeMillis();
        applyType(w, type, preset, customLayers);
        index(w);
        saveData();
        return w;
    }

    private void applyType(PersonalWorld w, WorldType type, @Nullable FlatPresets.Preset preset, @Nullable String customLayers) {
        w.type = type;
        w.seed = server.overworld().getRandom().nextLong();
        if (type == WorldType.SEED) w.seed = server.getWorldData().worldGenOptions().seed();
        if (type == WorldType.FLAT) {
            if (customLayers != null) {
                w.layers = customLayers;
                w.grid = null;
            } else {
                FlatPresets.Preset p = preset != null ? preset : FlatPresets.defaultPreset();
                w.layers = p.layers();
                w.grid = p.grid();
            }
        } else {
            w.layers = "";
            w.grid = null;
        }
        w.initialized = false;
    }

    /** Пересоздать мир с новым типом. Доступы, роли и правила игры сохраняются. */
    public void reset(PersonalWorld w, WorldType type, @Nullable FlatPresets.Preset preset, @Nullable String customLayers) {
        closeAndDeleteFolder(w, "tp.world_closed");
        byKey.remove(w.levelKey());
        w.generation++;
        CompoundTag keep = new CompoundTag();
        if (w.levelData.contains("GameRules")) keep.put("GameRules", w.levelData.get("GameRules"));
        w.levelData = keep;
        w.lastResetAt = System.currentTimeMillis();
        applyType(w, type, preset, customLayers);
        index(w);
        saveData();
    }

    public void delete(PersonalWorld w) {
        closeAndDeleteFolder(w, "tp.world_closed");
        byKey.remove(w.levelKey());
        byOwner.remove(w.owner);
        saveData();
    }

    public void transfer(PersonalWorld w, UUID newOwner, String newOwnerName) {
        byOwner.remove(w.owner);
        UUID old = w.owner;
        w.members.remove(newOwner);
        w.members.put(old, Role.ADMIN);
        w.owner = newOwner;
        w.ownerName = newOwnerName;
        if (w.displayName.equals(nameOf(old))) w.displayName = newOwnerName;
        byOwner.put(newOwner, w);
        saveData();
    }

    private void closeAndDeleteFolder(PersonalWorld w, String reasonKey) {
        PersonalServerLevel level = loaded(w);
        if (level != null) {
            evacuate(level, reasonKey);
            unload(level, false);
        }
        Path folder = server.storageSource.getDimensionPath(w.levelKey());
        pendingDeletes.add(new PendingDelete(folder, System.currentTimeMillis() + 2000, 0));
    }

    /** Переносит всех игроков из мира в основной мир. */
    public void evacuate(ServerLevel level, String reasonKey) {
        PersonalWorld w = level instanceof PersonalServerLevel p ? p.world() : null;
        for (ServerPlayer p : List.copyOf(level.players())) {
            PlayerFlow.leave(p, true);
            com.personalworlds.text.Msg.send(p, reasonKey, w != null ? w.displayName : "?");
        }
    }

    // ---------------------------------------------------------------- загрузка измерений

    @Nullable
    public PersonalServerLevel loaded(PersonalWorld w) {
        return server.getLevel(w.levelKey()) instanceof PersonalServerLevel p ? p : null;
    }

    /** Возвращает загруженный мир или загружает его (создавая при первом входе). */
    @Nullable
    public PersonalServerLevel getOrLoad(PersonalWorld w) {
        PersonalServerLevel level = loaded(w);
        if (level != null) return level;
        try {
            return load(w);
        } catch (Exception e) {
            PersonalWorlds.LOG.error("Не удалось загрузить личный мир {} ({})", w.displayName, w.levelKey().location(), e);
            return null;
        }
    }

    /** Для входа игрока на сервер: загрузить мир по ключу измерения из его сохранения. */
    @Nullable
    public ServerLevel loadForLogin(ResourceKey<Level> key) {
        PersonalWorld w = byKey.get(key);
        return w == null ? null : getOrLoad(w);
    }

    private PersonalServerLevel load(PersonalWorld w) throws Exception {
        RegistryAccess ra = server.registryAccess();
        LevelStem stem;
        long seed = w.seed;
        LayeredFlatGenerator flat = null;
        if (w.type.usesBuildDimension()) {
            Holder<DimensionType> dimType = buildDimensionType(ra);
            Holder<Biome> biome = ra.registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(w.type == WorldType.VOID ? Biomes.THE_VOID : Biomes.PLAINS);
            if (w.type == WorldType.VOID) {
                flat = new LayeredFlatGenerator(biome, List.of(), 0, null);
            } else {
                FlatPresets.Parsed parsed = FlatPresets.parse(w.layers, w.grid);
                int surface = PWConfig.FLAT_SURFACE_Y.get();
                int minY = dimType.value().minY();
                int baseY = Math.max(minY, surface - parsed.thickness());
                flat = new LayeredFlatGenerator(biome, parsed.layers(), baseY, parsed.grid());
            }
            stem = new LevelStem(dimType, flat);
        } else {
            LevelStem overworld = ra.registryOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD);
            stem = new LevelStem(overworld.type(), overworld.generator());
            if (w.type == WorldType.SEED) seed = server.getWorldData().worldGenOptions().seed();
        }

        PersonalLevelData data = new PersonalLevelData(server.getWorldData(), server.getWorldData().overworldData(),
                "personalworlds:" + w.id, w.levelData);
        ResourceKey<Level> key = w.levelKey();
        PersonalServerLevel level;
        PersonalServerLevel.PENDING_SEED.set(seed);
        try {
            level = new PersonalServerLevel(server, server.executor, server.storageSource, data, key, stem, NO_PROGRESS,
                    BiomeManager.obfuscateSeed(seed), w, seed);
        } finally {
            PersonalServerLevel.PENDING_SEED.remove();
        }

        // Своя граница мира: ванильные измерения повторяют границу основного мира, здесь — нет.
        WorldBorder border = level.getWorldBorder();
        border.addListener(new DimensionBorderListener(server, key));
        border.setCenter(0, 0);
        border.setSize(w.borderSize);

        server.forgeGetWorldMap().put(key, level);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));

        if (!w.initialized) initialize(w, level, flat);
        PersonalWorlds.LOG.info("Загружен личный мир {} ({}), тип {}", w.displayName, key.location(), w.type.id());
        return level;
    }

    private Holder<DimensionType> buildDimensionType(RegistryAccess ra) {
        var reg = ra.registryOrThrow(Registries.DIMENSION_TYPE);
        ResourceLocation id = ResourceLocation.tryParse(PWConfig.BUILD_DIMENSION_TYPE.get());
        if (id != null) {
            var h = reg.getHolder(ResourceKey.create(Registries.DIMENSION_TYPE, id));
            if (h.isPresent()) return h.get();
            PersonalWorlds.LOG.warn("Тип измерения {} не найден, используется minecraft:overworld", id);
        }
        return reg.getHolderOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD);
    }

    /** Первое создание: точка спавна, платформа для пустоты, правила игры по умолчанию. */
    private void initialize(PersonalWorld w, PersonalServerLevel level, @Nullable LayeredFlatGenerator flat) {
        BlockPos spawn;
        if (w.type == WorldType.VOID) {
            int y = 64;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(new BlockPos(dx, y - 1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
            spawn = new BlockPos(0, y, 0);
        } else if (flat != null) {
            spawn = new BlockPos(0, flat.surfaceY(), 0);
        } else {
            level.getChunk(0, 0, ChunkStatus.FULL, true);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            if (y <= level.getMinBuildHeight()) {
                y = 100;
                level.setBlockAndUpdate(new BlockPos(0, y - 1, 0), Blocks.SMOOTH_STONE.defaultBlockState());
            }
            spawn = new BlockPos(0, y, 0);
        }
        level.personalData().setSpawn(spawn, 0f);
        w.initialized = true;
        w.levelData = level.personalData().save();
        saveData();
    }

    /** Сохраняет и выгружает мир из памяти. */
    public void unload(PersonalServerLevel level, boolean evacuateFirst) {
        if (evacuateFirst) evacuate(level, "tp.world_closed");
        PersonalWorld w = level.world();
        ResourceKey<Level> key = level.dimension();
        w.levelData = level.personalData().save();
        try {
            level.save(null, true, false);
        } catch (Exception e) {
            PersonalWorlds.LOG.error("Ошибка сохранения личного мира {}", key.location(), e);
        }
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        server.forgeGetWorldMap().remove(key);
        server.markWorldsDirty();
        try {
            level.close();
        } catch (IOException e) {
            PersonalWorlds.LOG.error("Ошибка закрытия личного мира {}", key.location(), e);
        }
        emptySince.remove(key);
        markDirty();
        PersonalWorlds.LOG.info("Выгружен личный мир {} ({})", w.displayName, key.location());
    }

    public List<PersonalServerLevel> loadedLevels() {
        List<PersonalServerLevel> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (l instanceof PersonalServerLevel p) out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- тик

    public void tick() {
        if (++tickCounter % 100 != 0) return;
        long now = System.currentTimeMillis();
        int minutes = PWConfig.AUTO_UNLOAD_MINUTES.get();
        if (minutes > 0) {
            for (PersonalServerLevel level : loadedLevels()) {
                if (!level.players().isEmpty()) {
                    emptySince.remove(level.dimension());
                    continue;
                }
                long since = emptySince.computeIfAbsent(level.dimension(), k -> now);
                if (now - since >= minutes * 60_000L) unload(level, false);
            }
        }
        processDeletes(now);
        if (dirty) saveData();
    }

    private void processDeletes(long now) {
        Iterator<PendingDelete> it = pendingDeletes.iterator();
        List<PendingDelete> retry = new ArrayList<>();
        while (it.hasNext()) {
            PendingDelete d = it.next();
            if (d.notBefore() > now) continue;
            it.remove();
            if (!deleteRecursively(d.path()) && d.attempt() < 10) {
                retry.add(new PendingDelete(d.path(), now + 5000, d.attempt() + 1));
            }
        }
        pendingDeletes.addAll(retry);
    }

    private static boolean deleteRecursively(Path path) {
        if (!Files.exists(path)) return true;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
            PersonalWorlds.LOG.info("Удалена папка мира {}", path);
            return true;
        } catch (IOException e) {
            PersonalWorlds.LOG.warn("Не удалось удалить {} (повторим позже): {}", path, e.toString());
            return false;
        }
    }

    // ---------------------------------------------------------------- вспомогательное

    private static final ChunkProgressListener NO_PROGRESS = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos center) {
        }

        @Override
        public void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    /** Рассылает изменения границы только игрокам этого измерения. */
    private record DimensionBorderListener(MinecraftServer server, ResourceKey<Level> dim) implements BorderChangeListener {
        @Override
        public void onBorderSizeSet(WorldBorder border, double size) {
            server.getPlayerList().broadcastAll(new ClientboundSetBorderSizePacket(border), dim);
        }

        @Override
        public void onBorderSizeLerping(WorldBorder border, double oldSize, double newSize, long time) {
            server.getPlayerList().broadcastAll(new ClientboundSetBorderLerpSizePacket(border), dim);
        }

        @Override
        public void onBorderCenterSet(WorldBorder border, double x, double z) {
            server.getPlayerList().broadcastAll(new ClientboundSetBorderCenterPacket(border), dim);
        }

        @Override
        public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
            server.getPlayerList().broadcastAll(new ClientboundSetBorderWarningDelayPacket(border), dim);
        }

        @Override
        public void onBorderSetWarningBlocks(WorldBorder border, int warningBlocks) {
            server.getPlayerList().broadcastAll(new ClientboundSetBorderWarningDistancePacket(border), dim);
        }

        @Override
        public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {
        }

        @Override
        public void onBorderSetDamageSafeZOne(WorldBorder border, double damageSafeZone) {
        }
    }

    public static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
