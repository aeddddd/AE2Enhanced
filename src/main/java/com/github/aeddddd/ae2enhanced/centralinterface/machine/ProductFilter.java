package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerUtils;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * 框架层产物过滤器：决定"从目标机器里取出来的东西"能不能进 AE 网络.
 *
 * <p>三条接受规则（按顺序判定）：</p>
 * <ol>
 *   <li><b>本批输入快照内的物品一律不接受</b>——它还在机器里没被消耗，取回网络等于凭空多出材料；</li>
 *   <li>与样板声明的预期产物匹配（item+meta 相同；expected 带 NBT 时要求 NBT 相同）→ 接受；</li>
 *   <li>角色未知时接受其余物品（<b>降级</b>）；
 *       角色已知时只接受 {@link SlotRole#OUTPUT} 槽中的物品。</li>
 * </ol>
 *
 * <p><b>降级不是偷懒</b>：对只有笼统 {@code IItemHandler} 的机器，无法区分"产物"与"玩家预置的
 * 燃料/工具"，猜错会把玩家的物品收走。</p>
 */
public final class ProductFilter {

    private final IAEItemStack[] expectedOutputs;
    private final List<ItemStack> batchInputs;
    private final boolean rolesKnown;

    private ProductFilter(IAEItemStack[] expectedOutputs, List<ItemStack> batchInputs, boolean rolesKnown) {
        this.expectedOutputs = expectedOutputs;
        this.batchInputs = batchInputs;
        this.rolesKnown = rolesKnown;
    }

    public static ProductFilter of(IAEItemStack[] expectedOutputs, List<ItemStack> batchInputs, boolean rolesKnown) {
        return new ProductFilter(expectedOutputs, batchInputs, rolesKnown);
    }

    /**
     * 是否接受该槽位中的物品作为产物.
     *
     * @param stack 槽位当前内容（非空）
     * @param role  该槽位的角色
     */
    public boolean accepts(ItemStack stack, SlotRole role) {
        if (stack.isEmpty()) {
            return false;
        }
        if (HandlerUtils.isInputMaterial(stack, this.batchInputs)) {
            return false;
        }
        if (matchesExpected(stack, this.expectedOutputs)) {
            return true;
        }
        if (!this.rolesKnown) {
            return true;
        }
        return role != null && role.producesOutput();
    }

    public boolean rolesKnown() {
        return this.rolesKnown;
    }

    /**
     * 与样板声明的预期产物匹配.
     *
     * <p>匹配放宽：item + meta 必须相同；
     * 仅当 expected 带 NBT 时才要求 NBT 相同（配方产物 NBT 常由机器后补，严格比较会漏收）。</p>
     */
    public static boolean matchesExpected(ItemStack actual, IAEItemStack[] expectedOutputs) {
        if (actual == null || actual.isEmpty() || expectedOutputs == null) {
            return false;
        }
        for (IAEItemStack expected : expectedOutputs) {
            if (expected == null || expected.getStackSize() <= 0) {
                continue;
            }
            ItemStack template = expected.createItemStack();
            if (!template.isEmpty() && HandlerUtils.matchesLoosely(actual, template)) {
                return true;
            }
        }
        return false;
    }
}
