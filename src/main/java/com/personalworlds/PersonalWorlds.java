package com.personalworlds;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.personalworlds.config.PWConfig;
import com.personalworlds.event.PWEvents;
import com.personalworlds.gen.LayeredFlatGenerator;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Personal Worlds — личные миры игроков на одном сервере. */
@Mod(PersonalWorlds.MODID)
public class PersonalWorlds {
    public static final String MODID = "personalworlds";
    public static final Logger LOG = LogUtils.getLogger();

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    static {
        CHUNK_GENERATORS.register("layered_flat", () -> LayeredFlatGenerator.CODEC);
    }

    public PersonalWorlds(IEventBus modBus, ModContainer container) {
        CHUNK_GENERATORS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, PWConfig.SPEC);
        NeoForge.EVENT_BUS.register(PWEvents.class);
    }
}
