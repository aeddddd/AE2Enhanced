package com.github.aeddddd.ae2enhanced.mixin.late.projecte;

import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * 将 ProjectE 的玩家 EMC 存储从 long 扩展为 BigInteger。
 *
 * <p>目标类：{@code moze_intel.projecte.impl.KnowledgeImpl.DefaultImpl}</p>
 */
@Mixin(targets = "moze_intel.projecte.impl.KnowledgeImpl$DefaultImpl", remap = false)
public class MixinKnowledgeImpl {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final String NBT_EMC_BIG = "ae2e.transmutationEmcBig";

    @Unique
    private BigInteger ae2e$emcBig = BigInteger.ZERO;

    @Shadow
    private long emc;

    @Unique
    private void ae2e$syncEmcField() {
        this.emc = ae2e$emcBig.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : ae2e$emcBig.longValue();
    }

    @Inject(method = "getEmc()J", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$onGetEmc(CallbackInfoReturnable<Long> cir) {
        if (ae2e$emcBig.compareTo(LONG_MAX) >= 0) {
            cir.setReturnValue(Long.MAX_VALUE);
        } else {
            cir.setReturnValue(ae2e$emcBig.longValue());
        }
    }

    @Inject(method = "setEmc(J)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$onSetEmc(long emc, CallbackInfo ci) {
        ae2e$emcBig = emc < 0 ? BigInteger.ZERO : BigInteger.valueOf(emc);
        ae2e$syncEmcField();
        ci.cancel();
    }

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/NBTTagCompound;", at = @At("RETURN"), remap = false)
    private void ae2e$onSerialize(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound tag = cir.getReturnValue();
        if (tag != null) {
            tag.setByteArray(NBT_EMC_BIG, ae2e$emcBig.toByteArray());
        }
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("TAIL"), remap = false)
    private void ae2e$onDeserialize(NBTTagCompound tag, CallbackInfo ci) {
        if (tag.hasKey(NBT_EMC_BIG, 7)) { // NBT.TAG_BYTE_ARRAY
            ae2e$emcBig = new BigInteger(tag.getByteArray(NBT_EMC_BIG));
        } else if (tag.hasKey("transmutationEmc", 4)) { // fallback old long
            ae2e$emcBig = BigInteger.valueOf(tag.getLong("transmutationEmc"));
        } else {
            ae2e$emcBig = BigInteger.ZERO;
        }
        ae2e$syncEmcField();
    }

    /**
     * 外部反射访问接口：获取 BigInteger 余额。
     */
    @SuppressWarnings("unused")
    public BigInteger ae2e$getEmcBig() {
        return ae2e$emcBig;
    }

    /**
     * 外部反射访问接口：设置 BigInteger 余额。
     */
    @SuppressWarnings("unused")
    public void ae2e$setEmcBig(BigInteger emc) {
        ae2e$emcBig = emc == null ? BigInteger.ZERO : emc;
        ae2e$syncEmcField();
    }

    /**
     * 外部反射访问接口：增加 EMC。
     */
    @SuppressWarnings("unused")
    public void ae2e$addEmc(long value) {
        if (value > 0) {
            ae2e$emcBig = ae2e$emcBig.add(BigInteger.valueOf(value));
            ae2e$syncEmcField();
        }
    }

    /**
     * 外部反射访问接口：减少 EMC。
     */
    @SuppressWarnings("unused")
    public void ae2e$subtractEmc(long value) {
        if (value > 0) {
            ae2e$emcBig = ae2e$emcBig.subtract(BigInteger.valueOf(value));
            if (ae2e$emcBig.signum() < 0) {
                ae2e$emcBig = BigInteger.ZERO;
            }
            ae2e$syncEmcField();
        }
    }
}
