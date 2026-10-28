package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.personalworlds.config.PWConfig;
import com.personalworlds.world.PersonalServerLevel;

import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;

/** Командные блоки (и вагонетки с ними) в личных мирах выключены, если не разрешены в настройках. */
@Mixin(BaseCommandBlock.class)
public abstract class BaseCommandBlockMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void personalworlds$blockInPersonalWorlds(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof PersonalServerLevel && !PWConfig.ALLOW_COMMAND_BLOCKS.get()) cir.setReturnValue(false);
    }
}
