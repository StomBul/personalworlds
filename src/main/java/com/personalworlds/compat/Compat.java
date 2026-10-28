package com.personalworlds.compat;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import com.personalworlds.PersonalWorlds;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Интеграции со сторонними модами сборки. Все необязательные: без мода код не вызывается. */
public final class Compat {
    private static Boolean curios;
    private static Method carryGet;
    private static Method carryIsCarrying;
    private static boolean carryResolved;

    public static boolean curios() {
        if (curios == null) curios = ModList.get().isLoaded("curios");
        return curios;
    }

    /** Слоты Curios сохраняются и меняются вместе с инвентарём. */
    @Nullable
    public static Tag saveCurios(ServerPlayer player) {
        if (!curios()) return null;
        try {
            return CuriosHook.save(player);
        } catch (Throwable t) {
            PersonalWorlds.LOG.error("Curios: не удалось сохранить слоты {}", player.getGameProfile().getName(), t);
            return null;
        }
    }

    public static void loadCurios(ServerPlayer player, @Nullable Tag tag) {
        if (!curios()) return;
        try {
            CuriosHook.load(player, tag instanceof ListTag l ? l : new ListTag());
        } catch (Throwable t) {
            PersonalWorlds.LOG.error("Curios: не удалось загрузить слоты {}", player.getGameProfile().getName(), t);
        }
    }

    /** Carry On: игрок несёт блок или существо в руках — переносить его между мирами нельзя. */
    public static boolean isCarrying(ServerPlayer player) {
        if (!carryResolved) {
            carryResolved = true;
            if (ModList.get().isLoaded("carryon")) {
                try {
                    Class<?> mgr = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
                    Class<?> data = Class.forName("tschipp.carryon.common.carry.CarryOnData");
                    carryGet = mgr.getMethod("getCarryData", net.minecraft.world.entity.player.Player.class);
                    carryIsCarrying = data.getMethod("isCarrying");
                } catch (ReflectiveOperationException e) {
                    PersonalWorlds.LOG.warn("Carry On найден, но его API изменилось: {}", e.toString());
                }
            }
        }
        if (carryGet == null) return false;
        try {
            Object d = carryGet.invoke(null, player);
            return d != null && (boolean) carryIsCarrying.invoke(d);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Compat() {}
}
