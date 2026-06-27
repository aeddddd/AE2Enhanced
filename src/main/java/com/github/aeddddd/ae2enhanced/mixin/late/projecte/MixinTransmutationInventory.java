package com.github.aeddddd.ae2enhanced.mixin.late.projecte;

import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEBigEmcHelper;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.PlayerHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.math.BigInteger;

/**
 * 改写 ProjectE 转换桌的 EMC 加减逻辑，使其不再受 long 上限限制。
 */
@Mixin(value = TransmutationInventory.class, remap = false)
public abstract class MixinTransmutationInventory {

    private static final BigInteger LONG_MAX_BI = BigInteger.valueOf(Long.MAX_VALUE);

    @Shadow
    public EntityPlayer player;

    @Shadow
    public moze_intel.projecte.api.capabilities.IKnowledgeProvider provider;

    @Shadow
    private IItemHandlerModifiable inputLocks;

    @Shadow
    public abstract void updateClientTargets();

    @Shadow
    public abstract void handleKnowledge(ItemStack stack);

    /**
     * @author AE2Enhanced
     * @reason 改用 BigInteger 支持超过 Long.MAX_VALUE 的 EMC。
     */
    @Overwrite(remap = false)
    public void addEmc(long value) {
        if (value == 0L) return;
        if (value < 0L) {
            this.ae2e$removeEmc(-value);
            return;
        }

        for (int i = 0; i < this.inputLocks.getSlots(); ++i) {
            if (i == 8) continue;
            ItemStack stack = this.inputLocks.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof IItemEmc)) continue;

            IItemEmc itemEmc = (IItemEmc) stack.getItem();
            long needed = itemEmc.getMaximumEmc(stack) - itemEmc.getStoredEmc(stack);
            if (needed <= 0) continue;

            long add = Math.min(value, needed);
            itemEmc.addEmc(stack, add);
            value -= add;
            if (value == 0L) return;
        }

        if (value > 0L) {
            ProjectEBigEmcHelper.addEmc(this.provider, value);
        }

        if (!this.player.world.isRemote) {
            PlayerHelper.updateScore((EntityPlayerMP) this.player, PlayerHelper.SCOREBOARD_EMC,
                    MathHelper.floor((float) this.provider.getEmc()));
        }
    }

    /**
     * @author AE2Enhanced
     * @reason 改用 BigInteger 支持超过 Long.MAX_VALUE 的 EMC。
     */
    @Overwrite(remap = false)
    public void removeEmc(long value) {
        this.ae2e$removeEmc(value);
    }

    @Unique
    private void ae2e$removeEmc(long value) {
        if (value == 0L) return;
        if (value < 0L) {
            this.addEmc(-value);
            return;
        }

        BigInteger balance = ProjectEBigEmcHelper.getEmcBig(this.provider);
        BigInteger valueBI = BigInteger.valueOf(value);
        BigInteger removedFromLocks = BigInteger.ZERO;

        if (valueBI.compareTo(balance) > 0) {
            BigInteger deficit = valueBI.subtract(balance);
            for (int i = 0; i < this.inputLocks.getSlots() && deficit.signum() > 0; ++i) {
                if (i == 8) continue;
                ItemStack stack = this.inputLocks.getStackInSlot(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof IItemEmc)) continue;

                IItemEmc itemEmc = (IItemEmc) stack.getItem();
                long stored = itemEmc.getStoredEmc(stack);
                if (stored <= 0) continue;

                long maxRemove = deficit.compareTo(LONG_MAX_BI) >= 0 ? Long.MAX_VALUE : deficit.longValue();
                long remove = Math.min(stored, maxRemove);
                itemEmc.extractEmc(stack, remove);
                deficit = deficit.subtract(BigInteger.valueOf(remove));
                removedFromLocks = removedFromLocks.add(BigInteger.valueOf(remove));
            }
            valueBI = balance.add(removedFromLocks);
        }

        BigInteger toRemove = valueBI.min(balance);
        if (toRemove.signum() > 0) {
            if (toRemove.compareTo(LONG_MAX_BI) > 0) {
                toRemove = LONG_MAX_BI;
            }
            ProjectEBigEmcHelper.subtractEmc(this.provider, toRemove.longValue());
        }

        if (!this.player.world.isRemote) {
            PlayerHelper.updateScore((EntityPlayerMP) this.player, PlayerHelper.SCOREBOARD_EMC,
                    ae2e$scoreboardEmc());
        }
    }

    @Unique
    private int ae2e$scoreboardEmc() {
        BigInteger emc = ProjectEBigEmcHelper.getEmcBig(this.provider);
        BigInteger max = BigInteger.valueOf(Integer.MAX_VALUE);
        return emc.compareTo(max) >= 0 ? Integer.MAX_VALUE : emc.intValue();
    }

    /**
     * @author AE2Enhanced
     * @reason BigInteger 账户不再有上限，永远返回 false。
     */
    @Overwrite(remap = false)
    public boolean hasMaxedEmc() {
        return false;
    }

    /**
     * @author AE2Enhanced
     * @reason 按 BigInteger 计算后 clamp 到 long 返回值。
     */
    @Overwrite(remap = false)
    public long getAvailableEMC() {
        BigInteger total = ProjectEBigEmcHelper.getEmcBig(this.provider);
        for (int i = 0; i < this.inputLocks.getSlots(); ++i) {
            ItemStack stack = this.inputLocks.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof IItemEmc)) continue;
            IItemEmc itemEmc = (IItemEmc) stack.getItem();
            total = total.add(BigInteger.valueOf(itemEmc.getStoredEmc(stack)));
        }
        if (total.compareTo(LONG_MAX_BI) >= 0) {
            return Long.MAX_VALUE;
        }
        return total.longValue();
    }

    /**
     * 供 GUI 使用的精确 BigInteger 可用 EMC。
     */
    @SuppressWarnings("unused")
    public BigInteger ae2e$getAvailableEMCBig() {
        BigInteger total = ProjectEBigEmcHelper.getEmcBig(this.provider);
        for (int i = 0; i < this.inputLocks.getSlots(); ++i) {
            ItemStack stack = this.inputLocks.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof IItemEmc)) continue;
            IItemEmc itemEmc = (IItemEmc) stack.getItem();
            total = total.add(BigInteger.valueOf(itemEmc.getStoredEmc(stack)));
        }
        return total;
    }
}
