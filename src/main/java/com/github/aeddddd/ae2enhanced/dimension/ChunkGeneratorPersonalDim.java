package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 个人维度 ChunkGenerator：按预设单元平铺地板。
 */
public class ChunkGeneratorPersonalDim implements IChunkGenerator {

    private final World world;
    private final FloorPreset preset;
    private final int floorY;

    public ChunkGeneratorPersonalDim(World world) {
        this.world = world;
        this.preset = PresetLoader.getPreset();
        this.floorY = AE2EnhancedConfig.personalDimension.floorY;
    }

    @Override
    public Chunk generateChunk(int x, int z) {
        ChunkPrimer primer = new ChunkPrimer();
        int baseX = x << 4;
        int baseZ = z << 4;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                IBlockState state = preset.getState(worldX, worldZ);
                if (state == null) {
                    state = Blocks.BEDROCK.getDefaultState();
                }
                primer.setBlockState(lx, floorY, lz, state);
            }
        }
        Chunk chunk = new Chunk(world, primer, x, z);
        chunk.generateSkylightMap();
        chunk.checkLight();
        chunk.setLightPopulated(true);
        return chunk;
    }

    @Override
    public void populate(int x, int z) {
        // 个人维度不生成任何结构或装饰
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        return false;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        return java.util.Collections.emptyList();
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        return null;
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int x, int z) {
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        return false;
    }
}
