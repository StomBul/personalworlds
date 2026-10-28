package com.personalworlds.compat;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;

/** Загружается только при установленном Curios. */
final class CuriosHook {
    static Tag save(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).map(inv -> (Tag) inv.saveInventory(false)).orElse(null);
    }

    static void load(ServerPlayer player, ListTag tag) {
        CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
            inv.saveInventory(true); // очистить текущие слоты
            if (!tag.isEmpty()) inv.loadInventory(tag);
        });
    }

    private CuriosHook() {}
}
