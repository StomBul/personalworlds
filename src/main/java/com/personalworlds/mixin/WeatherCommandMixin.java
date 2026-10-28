package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.personalworlds.world.PersonalServerLevel;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.level.ServerLevel;

/** /weather в личном мире меняет погоду этого мира, а не основного. */
@Mixin(WeatherCommand.class)
public abstract class WeatherCommandMixin {
    @Redirect(method = {"setClear", "setRain", "setThunder"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private static ServerLevel personalworlds$level(MinecraftServer server, CommandSourceStack source, int time) {
        return source.getLevel() instanceof PersonalServerLevel ? source.getLevel() : server.overworld();
    }
}
