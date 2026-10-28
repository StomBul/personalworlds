package com.personalworlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.personalworlds.world.PersonalServerLevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldOptions;

/** Сид личного мира для проверки структур (StructureCheck и т.п.), вместо сида сервера. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Redirect(method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long personalworlds$seed(WorldOptions options) {
        Long pending = PersonalServerLevel.PENDING_SEED.get();
        return pending != null ? pending : options.seed();
    }
}
