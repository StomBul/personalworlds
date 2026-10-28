package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.brigadier.context.CommandContext;
import com.personalworlds.world.PersonalServerLevel;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.GameRules;

/** /gamerule в личном мире читает и меняет правила этого мира (у каждого личного мира они свои). */
@Mixin(GameRuleCommand.class)
public abstract class GameRuleCommandMixin {
    @Redirect(method = "setRule",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getGameRules()Lnet/minecraft/world/level/GameRules;"))
    private static GameRules personalworlds$setRules(MinecraftServer server, CommandContext<CommandSourceStack> ctx, GameRules.Key<?> key) {
        return ctx.getSource().getLevel() instanceof PersonalServerLevel l ? l.getGameRules() : server.getGameRules();
    }

    @Redirect(method = "queryRule",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getGameRules()Lnet/minecraft/world/level/GameRules;"))
    private static GameRules personalworlds$queryRules(MinecraftServer server, CommandSourceStack source, GameRules.Key<?> key) {
        return source.getLevel() instanceof PersonalServerLevel l ? l.getGameRules() : server.getGameRules();
    }
}
