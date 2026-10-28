package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.personalworlds.perm.CommandGuard;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;

/** Ограниченный источник (командный блок, функция) не может «выйти» из личного мира через execute in. */
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin {
    @Inject(method = "withLevel", at = @At("HEAD"), cancellable = true)
    private void personalworlds$stayInWorld(ServerLevel level, CallbackInfoReturnable<CommandSourceStack> cir) {
        CommandSourceStack self = (CommandSourceStack) (Object) this;
        if (level != self.getLevel() && CommandGuard.restricted(self)) cir.setReturnValue(self);
    }
}
