package com.personalworlds.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.personalworlds.perm.CommandGuard;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

@Mixin(EntitySelector.class)
public abstract class EntitySelectorMixin {
    /** Админ личного мира может пользоваться селекторами (@s, @a, @e) — результаты всё равно ограничены его миром. */
    @Inject(method = "checkPermissions", at = @At("HEAD"), cancellable = true)
    private void personalworlds$allowSelectors(CommandSourceStack source, CallbackInfo ci) {
        if (CommandGuard.elevated(source)) ci.cancel();
    }

    @Inject(method = "findEntities", at = @At("RETURN"), cancellable = true)
    private void personalworlds$filterEntities(CommandSourceStack source, CallbackInfoReturnable<List<? extends Entity>> cir) {
        @SuppressWarnings("unchecked")
        List<Entity> found = (List<Entity>) cir.getReturnValue();
        List<Entity> filtered = CommandGuard.filter(source, found);
        if (filtered != found) cir.setReturnValue(filtered);
    }

    @Inject(method = "findPlayers", at = @At("RETURN"), cancellable = true)
    private void personalworlds$filterPlayers(CommandSourceStack source, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        List<ServerPlayer> found = cir.getReturnValue();
        List<ServerPlayer> filtered = CommandGuard.filter(source, found);
        if (filtered != found) cir.setReturnValue(filtered);
    }
}
