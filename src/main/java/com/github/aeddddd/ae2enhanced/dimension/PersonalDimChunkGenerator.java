package com.github.aeddddd.ae2enhanced.dimension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;

/**
 * 个人维度 ChunkGenerator：空世界 + 按预设单元平铺地板 + 地板下 2 层基岩.
 */
public class PersonalDimChunkGenerator extends ChunkGenerator {

    public static final MapCodec<PersonalDimChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                    Codec.INT.optionalFieldOf("floor_y", 64).forGetter(generator -> generator.floorY))
            .apply(instance, PersonalDimChunkGenerator::new));

    private final int floorY;

    public PersonalDimChunkGenerator(BiomeSource biomeSource, int floorY) {
        super(biomeSource);
        this.floorY = floorY;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC.codec();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
            StructureManager structureManager, ChunkAccess chunk) {
        // 空世界：不填充任何噪声地形
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // 无雕刻
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random,
            ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        IFloorPreset preset = PresetLoader.getPreset();
        // 按维度所有者的颜色方案替换样式占位色
        FloorColorScheme scheme = resolveColorScheme(level.getLevel());
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = chunkPos.getBlockX(lx);
                int worldZ = chunkPos.getBlockZ(lz);
                BlockState state = preset.getState(worldX, worldZ);
                if (state == null) {
                    state = bedrock;
                }
                if (scheme != null) {
                    state = scheme.apply(state);
                }
                chunk.setBlockState(pos.set(lx, floorY, lz), state, false);
                // 在地板下方生成 2 层基岩,防止玩家意外破坏地板后坠入虚空
                for (int by = 1; by <= 2 && floorY - by >= 0; by++) {
                    chunk.setBlockState(pos.set(lx, floorY - by, lz), bedrock, false);
                }
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        // 个人维度不生成任何生物
    }

    /**
     * 获取生成目标维度的所有者颜色方案;非个人维度或无条目时返回 null.
     */
    @Nullable
    private static FloorColorScheme resolveColorScheme(ServerLevel level) {
        if (!PersonalDimensionManager.isPersonalDimension(level.dimension())) {
            return null;
        }
        PlayerDimEntry entry = PersonalDimensionManager.getEntryByDimension(level.getServer(), level.dimension());
        return entry != null ? entry.colorScheme : null;
    }

    // ==================== 禁止地物/结构/生物自然生成(对齐 1.12 主分支) ====================

    /**
     * 禁止结构起点生成,对齐 1.12 的 generateStructures() = false;
     * 不覆写时基类会按平原群系匹配村庄/前哨站/矿井/要塞等原版结构.
     */
    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state,
            StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {
        // 个人维度不生成任何结构
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        // 无结构引用
    }

    @Override
    @Nullable
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> structure,
            BlockPos pos, int searchRadius, boolean skipKnownStructures) {
        // 结构定位(如 /locate)在个人维度永远无结果
        return null;
    }

    /**
     * 禁止地物(树木/草/花/矿石等 biome decoration)自然生成,对齐 1.12 的空 populate
     * 与 MixinGameRegistry 对 IWorldGenerator 的拦截(Forge 的 BiomeModifier 同样走此入口).
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // 个人维度不生成任何地物或装饰
    }

    /**
     * 禁止任何类别生物自然生成,对齐 1.12 的 getPossibleCreatures() 恒空;
     * 不覆写时平原群系的动物(牛/羊/猪/鸡/马)会自然刷出.
     */
    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome,
            StructureManager structureManager, MobCategory category, BlockPos pos) {
        return WeightedRandomList.create();
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    @Deprecated
    public int getGenDepth() {
        return 256;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return floorY + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        return new NoiseColumn(level.getMinBuildHeight(), new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return floorY + 1;
    }

    @Override
    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return floorY + 1;
    }

    @Override
    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
            RandomState random) {
        return floorY;
    }
}
