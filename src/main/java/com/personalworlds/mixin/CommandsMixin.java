package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.brigadier.ParseResults;
import com.personalworlds.perm.CommandGuard;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Запоминаем, какая команда и от кого сейчас выполняется (для селекторов и проверки телепортов). */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "performCommand", at = @At("HEAD"))
    private void personalworlds$push(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        CommandGuard.push(parse);
    }

    @Inject(method = "performCommand", at = @At("RETURN"))
    private void personalworlds$pop(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        CommandGuard.pop();
    }
}
