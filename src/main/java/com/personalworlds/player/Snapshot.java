package com.personalworlds.player;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.personalworlds.compat.Compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameType;

/**
 * Снимок «игрового» состояния игрока: инвентарь, броня, эндер-сундук, слоты Curios, опыт, здоровье, голод,
 * эффекты и режим игры. Основной мир и личные миры имеют по своему снимку, чтобы предметы не переходили между ними.
 */
public final class Snapshot {
    public static CompoundTag capture(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.put("Inventory", p.getInventory().save(new ListTag()));
        t.putInt("Selected", p.getInventory().selected);
        t.put("EnderChest", p.getEnderChestInventory().createTag(p.registryAccess()));
        Tag curios = Compat.saveCurios(p);
        if (curios != null) t.put("Curios", curios);
        t.putInt("XpLevel", p.experienceLevel);
        t.putFloat("XpProgress", p.experienceProgress);
        t.putInt("XpTotal", p.totalExperience);
        t.putFloat("Health", p.getHealth());
        t.putFloat("Absorption", p.getAbsorptionAmount());
        CompoundTag food = new CompoundTag();
        p.getFoodData().addAdditionalSaveData(food);
        t.put("Food", food);
        ListTag effects = new ListTag();
        for (MobEffectInstance e : p.getActiveEffects()) effects.add(e.save());
        t.put("Effects", effects);
        t.putInt("GameMode", p.gameMode.getGameModeForPlayer().getId());
        return t;
    }

    /** Применяет снимок; null — «пустой» игрок (первый вход в личные миры). */
    public static void apply(ServerPlayer p, @Nullable CompoundTag t, GameType fallbackMode) {
        p.stopUsingItem();
        p.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        if (t == null) t = new CompoundTag();

        if (t.contains("Inventory")) {
            p.getInventory().load(t.getList("Inventory", Tag.TAG_COMPOUND));
        } else {
            p.getInventory().clearContent();
        }
        p.getInventory().selected = Math.max(0, Math.min(8, t.getInt("Selected")));
        if (t.contains("EnderChest")) {
            p.getEnderChestInventory().fromTag(t.getList("EnderChest", Tag.TAG_COMPOUND), p.registryAccess());
        } else {
            p.getEnderChestInventory().clearContent();
        }
        Compat.loadCurios(p, t.get("Curios"));

        p.experienceLevel = t.getInt("XpLevel");
        p.experienceProgress = t.getFloat("XpProgress");
        p.totalExperience = t.getInt("XpTotal");
        p.setExperienceLevels(p.experienceLevel); // принудительная отправка опыта клиенту

        p.removeAllEffects();
        List<MobEffectInstance> effects = new ArrayList<>();
        for (Tag e : t.getList("Effects", Tag.TAG_COMPOUND)) {
            MobEffectInstance inst = MobEffectInstance.load((CompoundTag) e);
            if (inst != null) effects.add(inst);
        }
        effects.forEach(p::addEffect);

        if (t.contains("Food")) {
            p.getFoodData().readAdditionalSaveData(t.getCompound("Food"));
        } else {
            FoodData fresh = new FoodData();
            CompoundTag f = new CompoundTag();
            fresh.addAdditionalSaveData(f);
            p.getFoodData().readAdditionalSaveData(f);
        }
        float health = t.contains("Health") ? t.getFloat("Health") : p.getMaxHealth();
        p.setHealth(Math.max(1f, Math.min(health, p.getMaxHealth())));
        p.setAbsorptionAmount(t.getFloat("Absorption"));

        p.clearFire();
        p.fallDistance = 0;
        p.setGameMode(t.contains("GameMode") ? GameType.byId(t.getInt("GameMode")) : fallbackMode);
        p.inventoryMenu.broadcastChanges();
    }

    private Snapshot() {}
}
