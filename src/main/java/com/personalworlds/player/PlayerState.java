package com.personalworlds.player;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Состояние игрока относительно личных миров. Хранится в &lt;мир&gt;/personalworlds/players/&lt;uuid&gt;.dat. */
public class PlayerState {
    /** Игрок сейчас в личных мирах (с «творческим» инвентарём). */
    public boolean inside;
    /** Инвентарь основного мира, пока игрок в личных мирах. */
    @Nullable
    public CompoundTag mainSnapshot;
    /** Инвентарь личных миров (один на все личные миры). */
    @Nullable
    public CompoundTag buildSnapshot;
    /** Куда вернуть игрока в основном мире. */
    @Nullable
    public Location returnTo;
    /** Последняя позиция в каждом личном мире (ключ — id измерения). */
    public final Map<String, Location> lastPositions = new HashMap<>();

    public record Location(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", dimension.location().toString());
            t.putDouble("X", pos.x);
            t.putDouble("Y", pos.y);
            t.putDouble("Z", pos.z);
            t.putFloat("Yaw", yRot);
            t.putFloat("Pitch", xRot);
            return t;
        }

        @Nullable
        static Location load(CompoundTag t) {
            ResourceLocation rl = ResourceLocation.tryParse(t.getString("Dim"));
            if (rl == null) return null;
            return new Location(ResourceKey.create(Registries.DIMENSION, rl),
                    new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")), t.getFloat("Yaw"), t.getFloat("Pitch"));
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Inside", inside);
        if (mainSnapshot != null) t.put("Main", mainSnapshot);
        if (buildSnapshot != null) t.put("Build", buildSnapshot);
        if (returnTo != null) t.put("Return", returnTo.save());
        CompoundTag last = new CompoundTag();
        lastPositions.forEach((k, v) -> last.put(k, v.save()));
        t.put("Last", last);
        return t;
    }

    public static PlayerState load(CompoundTag t) {
        PlayerState s = new PlayerState();
        s.inside = t.getBoolean("Inside");
        s.mainSnapshot = t.contains("Main") ? t.getCompound("Main") : null;
        s.buildSnapshot = t.contains("Build") ? t.getCompound("Build") : null;
        s.returnTo = t.contains("Return") ? Location.load(t.getCompound("Return")) : null;
        CompoundTag last = t.getCompound("Last");
        for (String k : last.getAllKeys()) {
            Location l = Location.load(last.getCompound(k));
            if (l != null) s.lastPositions.put(k, l);
        }
        return s;
    }
}
