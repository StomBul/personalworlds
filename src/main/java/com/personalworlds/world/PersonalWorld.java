package com.personalworlds.world;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.personalworlds.PersonalWorlds;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/** Сохраняемое описание личного мира: владелец, тип генерации, доступы, настройки. */
public class PersonalWorld {
    /** Постоянный идентификатор мира (не зависит от владельца, чтобы мир можно было передать). */
    public UUID id;
    public UUID owner;
    public String ownerName;
    public String displayName;
    public WorldType type = WorldType.FLAT;
    /** Слои плоского мира в текстовом виде (для FLAT). */
    public String layers = "";
    @Nullable
    public String grid;
    public long seed;
    /** Номер поколения: при пересоздании мир получает новый ключ измерения, старая папка удаляется. */
    public int generation;
    public int borderSize;
    public boolean initialized;

    public final Map<UUID, Role> members = new LinkedHashMap<>();
    public final Set<UUID> banned = new LinkedHashSet<>();

    public GameType builderMode = GameType.CREATIVE;
    public GameType guestMode = GameType.ADVENTURE;
    public boolean pvp = false;
    public boolean offlineAccess = true;

    /** Время, погода, правила игры, спавн (см. PersonalLevelData). */
    public CompoundTag levelData = new CompoundTag();
    public long createdAt;
    public long lastResetAt;

    public ResourceKey<Level> levelKey() {
        return levelKey(id, generation);
    }

    public static ResourceKey<Level> levelKey(UUID id, int generation) {
        String path = "w_" + id.toString().replace("-", "") + "_" + generation;
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(PersonalWorlds.MODID, path));
    }

    @Nullable
    public Role roleOf(UUID player) {
        if (player.equals(owner)) return Role.OWNER;
        return members.get(player);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putUUID("Owner", owner);
        t.putString("OwnerName", ownerName);
        t.putString("Name", displayName);
        t.putString("Type", type.name());
        t.putString("Layers", layers);
        if (grid != null) t.putString("Grid", grid);
        t.putLong("Seed", seed);
        t.putInt("Generation", generation);
        t.putInt("Border", borderSize);
        t.putBoolean("Initialized", initialized);
        ListTag mem = new ListTag();
        members.forEach((uuid, role) -> {
            CompoundTag m = new CompoundTag();
            m.putUUID("Id", uuid);
            m.putString("Role", role.name());
            mem.add(m);
        });
        t.put("Members", mem);
        ListTag ban = new ListTag();
        banned.forEach(uuid -> {
            CompoundTag m = new CompoundTag();
            m.putUUID("Id", uuid);
            ban.add(m);
        });
        t.put("Banned", ban);
        t.putInt("BuilderMode", builderMode.getId());
        t.putInt("GuestMode", guestMode.getId());
        t.putBoolean("Pvp", pvp);
        t.putBoolean("OfflineAccess", offlineAccess);
        t.put("LevelData", levelData);
        t.putLong("Created", createdAt);
        t.putLong("LastReset", lastResetAt);
        return t;
    }

    public static PersonalWorld load(CompoundTag t) {
        PersonalWorld w = new PersonalWorld();
        w.id = t.getUUID("Id");
        w.owner = t.getUUID("Owner");
        w.ownerName = t.getString("OwnerName");
        w.displayName = t.getString("Name");
        try {
            w.type = WorldType.valueOf(t.getString("Type"));
        } catch (IllegalArgumentException e) {
            w.type = WorldType.FLAT;
        }
        w.layers = t.getString("Layers");
        w.grid = t.contains("Grid") ? t.getString("Grid") : null;
        w.seed = t.getLong("Seed");
        w.generation = t.getInt("Generation");
        w.borderSize = t.getInt("Border");
        w.initialized = t.getBoolean("Initialized");
        for (Tag e : t.getList("Members", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) e;
            w.members.put(m.getUUID("Id"), Role.byName(m.getString("Role"), Role.GUEST));
        }
        for (Tag e : t.getList("Banned", Tag.TAG_COMPOUND)) {
            w.banned.add(((CompoundTag) e).getUUID("Id"));
        }
        w.builderMode = GameType.byId(t.getInt("BuilderMode"));
        w.guestMode = GameType.byId(t.getInt("GuestMode"));
        w.pvp = t.getBoolean("Pvp");
        w.offlineAccess = t.getBoolean("OfflineAccess");
        w.levelData = t.getCompound("LevelData");
        w.createdAt = t.getLong("Created");
        w.lastResetAt = t.getLong("LastReset");
        return w;
    }
}
