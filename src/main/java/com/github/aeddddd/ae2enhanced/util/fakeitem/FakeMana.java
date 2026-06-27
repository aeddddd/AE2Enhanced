package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemManaDrop;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import net.minecraft.item.ItemStack;

/**
 * Botania Mana 假物品的 FakeItemHandler 实现与工具方法.
 *
 * 在模组初始化时调用 FakeMana.init() 注册 handler.
 */
public final class FakeMana {

    public static void init() {
        FakeItemRegister.registerHandler(ItemManaDrop.class, new FakeItemHandler<Long, IAEManaStack>() {

            @Override
            public Long getStack(ItemStack stack) {
                return ItemManaDrop.isManaDrop(stack) ? (long) stack.getCount() : null;
            }

            @Override
            public Long getStack(IAEItemStack stack) {
                return stack == null ? null : stack.getStackSize();
            }

            @Override
            public IAEManaStack getAEStack(ItemStack stack) {
                return getAEStack(AEItemStack.fromItemStack(stack));
            }

            @Override
            public IAEManaStack getAEStack(IAEItemStack stack) {
                if (stack == null) return null;
                if (!ItemManaDrop.isManaDrop(stack.createItemStack())) return null;
                return AEManaStack.create(stack.getStackSize());
            }

            @Override
            public ItemStack packStack(Long amount) {
                return ItemManaDrop.createStack();
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
            public IAEItemStack packAEStackLong(IAEManaStack mana) {
                if (mana == null || mana.getStackSize() <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(ItemManaDrop.createStack());
                if (stack == null) return null;
                stack.setStackSize(mana.getStackSize());
                return stack;
            }
        });
    }

    public static boolean isManaFakeItem(ItemStack stack) {
        return ItemManaDrop.isManaDrop(stack);
    }

    public static IAEItemStack packMana(IAEManaStack mana) {
        return FakeItemRegister.packAEStackLong(mana, ItemRegistry.MANA_DROP);
    }

    public static IAEManaStack unpackMana(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack stack = itemStack.createItemStack();
        if (ItemManaDrop.isManaDrop(stack)) {
            return FakeItemRegister.getAEStack(itemStack);
        }
        return null;
    }
}
