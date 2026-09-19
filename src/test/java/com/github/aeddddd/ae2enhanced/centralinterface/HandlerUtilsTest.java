package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link HandlerUtils} 工具类测试。
 *
 * <p>覆盖 isInputMaterial / matchesLoosely 的确定性行为。</p>
 */
public class HandlerUtilsTest {

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack / NBT，需无头引导
        AE2TestBootstrap.boot();
    }

    // ------------------------------------------------------------------
    // isInputMaterial / matchesLoosely
    // ------------------------------------------------------------------

    /** isInputMaterial：空物品、null/空输入快照均为 false。 */
    @Test
    public void testIsInputMaterialTrivialFalse() {
        ItemStack apple = new ItemStack(Items.APPLE, 1);

        assertThat(HandlerUtils.isInputMaterial(ItemStack.EMPTY,
                Collections.singletonList(apple))).isFalse();
        assertThat(HandlerUtils.isInputMaterial(apple, null)).isFalse();
        assertThat(HandlerUtils.isInputMaterial(apple, Collections.emptyList())).isFalse();
    }

    /** isInputMaterial：物品 + NBT 均相同才匹配；不同物品/metadata 不匹配。 */
    @Test
    public void testIsInputMaterialMatching() {
        ItemStack apple = new ItemStack(Items.APPLE, 1);

        assertThat(HandlerUtils.isInputMaterial(apple, Collections.singletonList(apple))).isTrue();
        assertThat(HandlerUtils.isInputMaterial(new ItemStack(Items.COAL, 1),
                Collections.singletonList(apple))).isFalse();
        // metadata 不同不匹配
        assertThat(HandlerUtils.isInputMaterial(new ItemStack(Items.APPLE, 1, 1),
                Collections.singletonList(new ItemStack(Items.APPLE, 1, 0)))).isFalse();
    }

    /** isInputMaterial：NBT 必须一致。 */
    @Test
    public void testIsInputMaterialNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("k", 1);
        ItemStack withTag = new ItemStack(Items.APPLE, 1);
        withTag.setTagCompound(tag);

        assertThat(HandlerUtils.isInputMaterial(withTag,
                Collections.singletonList(new ItemStack(Items.APPLE, 1)))).isFalse();

        ItemStack sameTag = new ItemStack(Items.APPLE, 1);
        sameTag.setTagCompound(tag.copy());
        assertThat(HandlerUtils.isInputMaterial(withTag,
                Collections.singletonList(sameTag))).isTrue();
    }

    /** matchesLoosely：不同物品 false；expected 无 NBT 时忽略 actual 的 NBT。 */
    @Test
    public void testMatchesLooselyIgnoresActualNbtWhenExpectedHasNone() {
        ItemStack actual = new ItemStack(Items.APPLE, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("k", 1);
        actual.setTagCompound(tag);

        assertThat(HandlerUtils.matchesLoosely(actual, new ItemStack(Items.APPLE, 1))).isTrue();
        assertThat(HandlerUtils.matchesLoosely(actual, new ItemStack(Items.COAL, 1))).isFalse();
    }

    /** matchesLoosely：expected 带 NBT 时要求 NBT 完全一致。 */
    @Test
    public void testMatchesLooselyRequiresNbtWhenExpectedHasSome() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("k", 1);
        ItemStack expected = new ItemStack(Items.APPLE, 1);
        expected.setTagCompound(tag);

        ItemStack matching = new ItemStack(Items.APPLE, 1);
        matching.setTagCompound(tag.copy());
        ItemStack noTag = new ItemStack(Items.APPLE, 1);

        assertThat(HandlerUtils.matchesLoosely(matching, expected)).isTrue();
        assertThat(HandlerUtils.matchesLoosely(noTag, expected)).isFalse();
    }
}
