package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.personalworlds.event.LoginTracker;
import com.personalworlds.world.PersonalServerLevel;
import com.personalworlds.world.WorldManager;

import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    /** Игрок вышел с сервера, стоя в личном мире: загружаем этот мир, чтобы он появился там же. */
    @Redirect(method = "placeNewPlayer",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel personalworlds$loadPersonalOnLogin(MinecraftServer server, ResourceKey<Level> key,
                                                           Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        LoginTracker.record(player.getUUID(), key);
        ServerLevel level = server.getLevel(key);
        if (level == null && WorldManager.isPersonalKey(key) && WorldManager.get() != null) {
            level = WorldManager.get().loadForLogin(key);
        }
        return level;
    }

    /** Клиенту отправляется граница его измерения, а не основного мира. */
    @Redirect(method = "sendLevelInfo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getWorldBorder()Lnet/minecraft/world/level/border/WorldBorder;"))
    private WorldBorder personalworlds$border(ServerLevel overworld, ServerPlayer player, ServerLevel level) {
        return level instanceof PersonalServerLevel ? level.getWorldBorder() : overworld.getWorldBorder();
    }
}
