package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack;
import net.minecraft.item.ItemStack;

/**
 * Astral Sorcery Starlight 假物品的 FakeItemHandler 实现与工具方法.
 *
 * 在模组初始化时调用 FakeStarlight.init() 注册 handler.
 */
public final class FakeStarlight {

    public static void init() {
        FakeItemRegister.registerHandler(ItemStarlightDrop.class, new FakeItemHandler<Long, IAEStarlightStack>() {

            @Override
            public Long getStack(ItemStack stack) {
                return ItemStarlightDrop.isStarlightDrop(stack) ? (long) stack.getCount() : null;
            }

            @Override
            public Long getStack(IAEItemStack stack) {
                return stack == null ? null : stack.getStackSize();
            }

            @Override
            public IAEStarlightStack getAEStack(ItemStack stack) {
                return getAEStack(AEItemStack.fromItemStack(stack));
            }

            @Override
            public IAEStarlightStack getAEStack(IAEItemStack stack) {
                if (stack == null) return null;
                if (!ItemStarlightDrop.isStarlightDrop(stack.createItemStack())) return null;
                return AEStarlightStack.create(stack.getStackSize());
            }

            @Override
            public ItemStack packStack(Long amount) {
                return ItemStarlightDrop.createStack();
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
            public IAEItemStack packAEStackLong(IAEStarlightStack starlight) {
                if (starlight == null || starlight.getStackSize() <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(ItemStarlightDrop.createStack());
                if (stack == null) return null;
                stack.setStackSize(starlight.getStackSize());
                return stack;
            }
        });
    }

    public static boolean isStarlightFakeItem(ItemStack stack) {
        return ItemStarlightDrop.isStarlightDrop(stack);
    }

    public static IAEItemStack packStarlight(IAEStarlightStack starlight) {
        return FakeItemRegister.packAEStackLong(starlight, ItemRegistry.STARLIGHT_DROP);
    }

    public static IAEStarlightStack unpackStarlight(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack stack = itemStack.createItemStack();
        if (ItemStarlightDrop.isStarlightDrop(stack)) {
            return FakeItemRegister.getAEStack(itemStack);
        }
        return null;
    }
}
