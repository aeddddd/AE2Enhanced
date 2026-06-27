package com.github.aeddddd.ae2enhanced.recycler;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 管理 ME 网络回收节点绑定的目标集合.
 */
public class TargetManager {

    private final Set<TargetRef> targets = new HashSet<>();

    public int getTargetCount() {
        return targets.size();
    }

    public Set<TargetRef> getTargets() {
        return Collections.unmodifiableSet(targets);
    }

    public boolean addTarget(TargetRef target) {
        return targets.add(target);
    }

    public boolean removeTarget(TargetRef target) {
        return targets.remove(target);
    }

    public void clear() {
        targets.clear();
    }

    public NBTTagList serializeNBT() {
        NBTTagList list = new NBTTagList();
        for (TargetRef target : targets) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Dim", target.dimId);
            tag.setLong("Pos", target.pos.toLong());
            tag.setByte("Face", (byte) target.face.ordinal());
            list.appendTag(tag);
        }
        return list;
    }

    public void deserializeNBT(NBTTagList list) {
        targets.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int dimId = tag.getInteger("Dim");
            BlockPos pos = BlockPos.fromLong(tag.getLong("Pos"));
            EnumFacing face = EnumFacing.byIndex(tag.getByte("Face"));
            targets.add(new TargetRef(dimId, pos, face));
        }
    }

    /**
     * 目标引用：维度 + 位置 + 面.
     */
    public static final class TargetRef {
        public final int dimId;
        public final BlockPos pos;
        public final EnumFacing face;

        public TargetRef(int dimId, BlockPos pos, EnumFacing face) {
            this.dimId = dimId;
            this.pos = pos;
            this.face = face;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TargetRef)) return false;
            TargetRef other = (TargetRef) o;
            return dimId == other.dimId && pos.equals(other.pos) && face == other.face;
        }

        @Override
        public int hashCode() {
            int result = dimId;
            result = 31 * result + pos.hashCode();
            result = 31 * result + face.hashCode();
            return result;
        }
    }
}
