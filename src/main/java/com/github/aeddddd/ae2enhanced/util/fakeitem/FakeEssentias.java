package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.item.ItemStack;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

/**
 * 源质假物品的 Thaumic Energistics 依赖方法.
 *
 * <p>此类包含 {@link #init()} 与 {@link #unpackEssentia},它们直接引用
 * Thaumic Energistics 类型(IAEEssentiaStack 等).因此本类<strong>只能</strong>
 * 在 Thaumic Energistics 已安装时被加载(由 AE2Enhanced.preInit() 控制).</p>
 *
 * <p>无需 Thaumic Energistics 的安全方法已移至 {@link FakeEssentiaSafe},
 * 可供无条件加载的类安全导入.</p>
 */
public class FakeEssentias {

    /**
     * 在模组初始化时注册源质 FakeItemHandler.
     * 仅在 Thaumcraft 存在时调用(由 AE2Enhanced.preInit() 控制).
     */
    public static void init() {
        FakeItemRegister.registerHandler(ItemEssentiaDrop.class, new FakeItemHandler<IAEEssentiaStack, IAEEssentiaStack>() {

            @Override
            public IAEEssentiaStack getStack(ItemStack stack) {
                if (stack.isEmpty()) return null;
                String aspectTag = ItemEssentiaDrop.getAspectTag(stack);
                if (aspectTag == null) return null;
                EssentiaStack essStack = new EssentiaStack(aspectTag, 1);
                IAEEssentiaStack result = AEEssentiaStack.fromEssentiaStack(essStack);
                if (result != null) {
                    result.setStackSize(stack.getCount());
                }
                return result;
            }

            @Override
            public IAEEssentiaStack getStack(IAEItemStack stack) {
                return stack == null ? null : getStack(stack.createItemStack());
            }

            @Override
            public IAEEssentiaStack getAEStack(ItemStack stack) {
                return getStack(stack);
            }

            @Override
            public IAEEssentiaStack getAEStack(IAEItemStack stack) {
                return getStack(stack);
            }

            @Override
            public ItemStack packStack(IAEEssentiaStack essentia) {
                if (essentia == null || essentia.getAspect() == null) return null;
                return ItemEssentiaDrop.createStack(essentia.getAspect().getTag(), (int) essentia.getStackSize());
            }

            @Override
            public IAEItemStack packAEStack(IAEEssentiaStack essentia) {
                if (essentia == null || essentia.getAspect() == null) return null;
                ItemStack fakeItem = ItemEssentiaDrop.createStack(essentia.getAspect().getTag(), 1);
                IAEItemStack result = AEItemStack.fromItemStack(fakeItem);
                if (result != null) {
                    result.setStackSize(essentia.getStackSize());
                }
                return result;
            }

            @Override
            public IAEItemStack packAEStackLong(IAEEssentiaStack essentia) {
                return packAEStack(essentia);
            }
        });
    }

    /**
     * 从 IAEItemStack 中解析 IAEEssentiaStack.
     * 支持 ItemEssentiaDrop 和 Thaumic Energistics 的 ItemDummyAspect.
     */
    public static IAEEssentiaStack unpackEssentia(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack stack = itemStack.createItemStack();
        if (FakeEssentiaSafe.isEssentiaFakeItem(stack)) {
            return FakeItemRegister.getAEStack(itemStack);
        }
        // 兼容 Thaumic Energistics 的 ItemDummyAspect
        if ("thaumicenergistics.item.ItemDummyAspect".equals(stack.getItem().getClass().getName())) {
            try {
                if (stack.hasTagCompound()) {
                    net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
                    if (tag.hasKey("aspect", 8)) {
                        String aspectTag = tag.getString("aspect");
                        EssentiaStack essStack = new EssentiaStack(aspectTag, (int) itemStack.getStackSize());
                        IAEEssentiaStack result = AEEssentiaStack.fromEssentiaStack(essStack);
                        return result;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
