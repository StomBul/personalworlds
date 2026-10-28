package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.personalworlds.perm.CommandGuard;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;

/** Селекторы (@s, @a, @e) разрешены админу личного мира уже при разборе команды. */
@Mixin(EntitySelectorParser.class)
public abstract class EntitySelectorParserMixin {
    @Inject(method = "allowSelectors", at = @At("HEAD"), cancellable = true)
    private static void personalworlds$allowForWorldAdmins(Object provider, CallbackInfoReturnable<Boolean> cir) {
        if (provider instanceof CommandSourceStack s && CommandGuard.elevated(s)) cir.setReturnValue(true);
    }
}
