package com.personalworlds.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.core.RegistryAccess;

/**
 * Свой плоский генератор. В отличие от ванильного суперплоского мира не зависит от модов генерации
 * (Tectonic, Terralith, увеличенная высота мира): слои ставятся по абсолютным координатам Y,
 * без структур, пещер, растительности и мобов. Пустые слои = мир-пустота.
 * Необязательная «сетка»: верхний слой на границах чанков заменяется другим блоком — удобно для разметки.
 */
public class LayeredFlatGenerator extends ChunkGenerator {
    public record Layer(BlockState state, int height) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Layer::state),
                Codec.intRange(1, 4096).fieldOf("height").forGetter(Layer::height)
        ).apply(i, Layer::new));
    }

    public static final MapCodec<LayeredFlatGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            Layer.CODEC.listOf().fieldOf("layers").forGetter(g -> g.layers),
            Codec.INT.fieldOf("base_y").forGetter(g -> g.baseY),
            BlockState.CODEC.optionalFieldOf("grid").forGetter(g -> Optional.ofNullable(g.grid))
    ).apply(i, (biome, layers, baseY, grid) -> new LayeredFlatGenerator(biome, layers, baseY, grid.orElse(null))));

    private final Holder<Biome> biome;
    private final List<Layer> layers;
    private final int baseY;
    @Nullable
    private final BlockState grid;
    /** column[i] — блок на высоте baseY + i. */
    private final BlockState[] column;

    public LayeredFlatGenerator(Holder<Biome> biome, List<Layer> layers, int baseY, @Nullable BlockState grid) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.layers = List.copyOf(layers);
        this.baseY = baseY;
        this.grid = grid;
        List<BlockState> col = new ArrayList<>();
        for (Layer l : layers) {
            for (int k = 0; k < l.height(); k++) col.add(l.state());
        }
        this.column = col.toArray(BlockState[]::new);
    }

    /** Первый блок воздуха над слоями. */
    public int surfaceY() {
        return baseY + column.length;
    }

    public boolean isEmpty() {
        return column.length == 0;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        // Никаких структур (деревень, крепостей) и колец крепостей.
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, Stream.empty());
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        for (int i = 0; i < column.length; i++) {
            int y = baseY + i;
            if (y < minY || y >= maxY) continue;
            boolean top = i == column.length - 1;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = (top && grid != null && (x == 0 || z == 0)) ? grid : column[i];
                    chunk.setBlockState(pos.set(x, y, z), state, false);
                    ocean.update(x, y, z, state);
                    surface.update(x, y, z, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        for (int i = column.length - 1; i >= 0; i--) {
            int y = baseY + i;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            if (type.isOpaque().test(column[i])) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[level.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        for (int i = 0; i < column.length; i++) {
            int idx = baseY + i - level.getMinBuildHeight();
            if (idx >= 0 && idx < states.length) states[idx] = column[i];
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return surfaceY();
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // Без деревьев, травы, руд и озёр: биом-модификаторы других модов сюда не попадают.
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager,
                                 ChunkAccess chunk, StructureTemplateManager structureTemplateManager) {
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return baseY;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }
}
