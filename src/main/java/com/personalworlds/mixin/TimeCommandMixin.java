package com.personalworlds.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.personalworlds.world.PersonalServerLevel;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.level.ServerLevel;

/** /time в личном мире меняет время только этого мира, а /time в основном мире не трогает личные миры. */
@Mixin(TimeCommand.class)
public abstract class TimeCommandMixin {
    @Redirect(method = {"setTime", "addTime"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getAllLevels()Ljava/lang/Iterable;"))
    private static Iterable<ServerLevel> personalworlds$levels(MinecraftServer server, CommandSourceStack source, int time) {
        if (source.getLevel() instanceof PersonalServerLevel) return List.of(source.getLevel());
        List<ServerLevel> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (!(l instanceof PersonalServerLevel)) out.add(l);
        }
        return out;
    }
}
