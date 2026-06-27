package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import net.minecraft.item.ItemStack;

/**
 * RF 能量假物品的 FakeItemHandler 实现与工具方法.
 *
 * 在模组初始化时调用 FakeEnergies.init() 注册 handler.
 */
public final class FakeEnergies {

    public static void init() {
        FakeItemRegister.registerHandler(ItemEnergyDrop.class, new FakeItemHandler<Long, IAEEnergyStack>() {

            @Override
            public Long getStack(ItemStack stack) {
                return ItemEnergyDrop.isEnergyDrop(stack) ? (long) stack.getCount() : null;
            }

            @Override
            public Long getStack(IAEItemStack stack) {
                return stack == null ? null : stack.getStackSize();
            }

            @Override
            public IAEEnergyStack getAEStack(ItemStack stack) {
                return getAEStack(AEItemStack.fromItemStack(stack));
            }

            @Override
            public IAEEnergyStack getAEStack(IAEItemStack stack) {
                if (stack == null) return null;
                if (!ItemEnergyDrop.isEnergyDrop(stack.createItemStack())) return null;
                return AEEnergyStack.create(stack.getStackSize());
            }

            @Override
            public ItemStack packStack(Long amount) {
                return ItemEnergyDrop.createStack();
            }

            @Override
            public IAEItemStack packAEStack(Long amount) {
                if (amount == null || amount <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(packStack(amount));
                if (stack == null) return null;
                stack.setStackSize(amount);
                return stack;
            }

            @Override
            public IAEItemStack packAEStackLong(IAEEnergyStack energy) {
                if (energy == null || energy.getStackSize() <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(ItemEnergyDrop.createStack());
                if (stack == null) return null;
                stack.setStackSize(energy.getStackSize());
                return stack;
            }
        });
    }

    public static boolean isEnergyFakeItem(ItemStack stack) {
        return ItemEnergyDrop.isEnergyDrop(stack);
    }

    public static IAEItemStack packEnergy(IAEEnergyStack energy) {
        return FakeItemRegister.packAEStackLong(energy, ItemRegistry.ENERGY_DROP);
    }

    public static IAEEnergyStack unpackEnergy(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack stack = itemStack.createItemStack();
        if (ItemEnergyDrop.isEnergyDrop(stack)) {
            return FakeItemRegister.getAEStack(itemStack);
        }
        return null;
    }
}
