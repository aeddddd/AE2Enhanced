package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link ISlotCatalog} 的构造器与默认实现.
 *
 * <p>各 mod 的"存储访问器"只需在此登记"哪个 handler 的哪些下标是什么角色"，
 * 不必再自己写遍历与提取循环。</p>
 *
 * <p>重建开销：目录是一次性构造的不可变快照，但槽位内容是实时读取的
 * （{@link SlotRef#peek()} 直接问 {@code IItemHandler}），因此目录可在一次
 * push/collect 期间复用，无需担心内容过期。</p>
 */
public final class CatalogBuilder {

    private final List<SlotRef> slots = new ArrayList<>();
    private boolean rolesKnown;

    CatalogBuilder() {
    }

    /** 登记单个槽位。 */
    public CatalogBuilder add(IItemHandler handler, int index, EnumFacing face, SlotRole role) {
        if (handler == null || index < 0 || index >= handler.getSlots()) {
            return this;
        }
        this.slots.add(new SlotRef(handler, index, face, role));
        if (role != null && role != SlotRole.UNKNOWN) {
            this.rolesKnown = true;
        }
        return this;
    }

    /** 登记整个 handler 为同一角色。 */
    public CatalogBuilder addAll(IItemHandler handler, EnumFacing face, SlotRole role) {
        if (handler == null) {
            return this;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            add(handler, i, face, role);
        }
        return this;
    }

    /**
     * 按槽位角色表登记整个 handler。
     *
     * <p>角色表比槽位数短时，其余槽位按 {@link SlotRole#UNKNOWN} 处理。</p>
     */
    public CatalogBuilder addAll(IItemHandler handler, EnumFacing face, SlotRole... roles) {
        if (handler == null) {
            return this;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            SlotRole role = i < roles.length ? roles[i] : SlotRole.UNKNOWN;
            add(handler, i, face, role);
        }
        return this;
    }

    /** 按显式下标集合登记为某角色（用于"输出槽是这几个下标"的机器）。 */
    public CatalogBuilder addIndices(IItemHandler handler, EnumFacing face, SlotRole role, int... indices) {
        if (handler == null || indices == null) {
            return this;
        }
        for (int index : indices) {
            add(handler, index, face, role);
        }
        return this;
    }

    /**
     * 构造目录.
     *
     * @param staleCheck 失效判定（可返回 null，表示永不失效）
     */
    public ISlotCatalog build(StaleCheck staleCheck) {
        return new SimpleSlotCatalog(this.slots, this.rolesKnown, staleCheck);
    }

    /** 构造一个永不失效的目录。 */
    public ISlotCatalog build() {
        return build(null);
    }

    /** 目录失效判定。 */
    public interface StaleCheck {
        boolean isStale();
    }

    private static final class SimpleSlotCatalog implements ISlotCatalog {

        private final List<SlotRef> slots;
        private final boolean rolesKnown;
        private final StaleCheck staleCheck;

        SimpleSlotCatalog(List<SlotRef> slots, boolean rolesKnown, StaleCheck staleCheck) {
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
            this.rolesKnown = rolesKnown;
            this.staleCheck = staleCheck;
        }

        @Override
        public List<SlotRef> slots() {
            return this.slots;
        }

        @Override
        public boolean rolesKnown() {
            return this.rolesKnown;
        }

        @Override
        public boolean isStale() {
            return this.staleCheck != null && this.staleCheck.isStale();
        }
    }

    // ---- 常用目录形状 ----

    /**
     * 六面通用容器目录.
     *
     * <p>六面各自的 {@code (handler, index)} 视图，全部 {@link SlotRole#UNKNOWN}；
     * <b>不使用 null 面</b>——只暴露 null 面 capability 的机器因此不被视为有效目标。</p>
     *
     * <p>有效性判定同时看 {@code hasCapability} 与 {@code getCapability}：真实 Forge 实现两者一致，
     * 但只看 {@code hasCapability} 会漏掉"能取到 handler 却未声明 capability"的机器，
     * 只看 {@code getCapability} 又无法表达"声明了能力但当前取不到"。</p>
     *
     * @return 目录；{@code te} 为 null、或六面既无能力也取不到 handler 时返回 {@code null}
     */
    public static ISlotCatalog facesOf(net.minecraft.tileentity.TileEntity te) {
        if (te == null) {
            return null;
        }
        CatalogBuilder builder = new CatalogBuilder();
        boolean valid = false;
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(
                    net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler != null || te.hasCapability(
                    net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face)) {
                valid = true;
            }
            if (handler != null) {
                builder.addAll(handler, face, SlotRole.UNKNOWN);
            }
        }
        return valid ? builder.build() : null;
    }

    /** 便捷：把一批物品快照转为列表。 */
    public static List<ItemStack> nonEmpty(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        if (stacks == null) {
            return result;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }
}
