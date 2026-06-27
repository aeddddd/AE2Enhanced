package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ControllerIndex extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_controller_index";
    private final Set<BlockPos> controllers = new HashSet<>();

    public ControllerIndex() {
        super(DATA_NAME);
    }

    public ControllerIndex(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        controllers.clear();
        NBTTagList list = nbt.getTagList("controllers", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound posTag = list.getCompoundTagAt(i);
            controllers.add(new BlockPos(posTag.getInteger("x"), posTag.getInteger("y"), posTag.getInteger("z")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (BlockPos pos : controllers) {
            NBTTagCompound posTag = new NBTTagCompound();
            posTag.setInteger("x", pos.getX());
            posTag.setInteger("y", pos.getY());
            posTag.setInteger("z", pos.getZ());
            list.appendTag(posTag);
        }
        compound.setTag("controllers", list);
        return compound;
    }

    public static ControllerIndex get(World world) {
        if (world.isRemote) return null;
        MapStorage storage = world.getPerWorldStorage();
        ControllerIndex index = (ControllerIndex) storage.getOrLoadData(ControllerIndex.class, DATA_NAME);
        if (index == null) {
            index = new ControllerIndex();
            storage.setData(DATA_NAME, index);
        }
        return index;
    }

    public void add(BlockPos pos) {
        if (controllers.add(pos)) {
            markDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (controllers.remove(pos)) {
            markDirty();
        }
    }

    public Set<BlockPos> getAll() {
        return Collections.unmodifiableSet(controllers);
    }
}
