package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.me.helpers.AENetworkProxy;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * 中枢接口 NBT 兼容性锁（重构计划 P0 · §3.1）。
 *
 * <p>用户约束：本次 handler 层重构<b>不允许破坏现有存档</b>。本测试是这条约束的唯一机器化保证：
 * 它把 {@link DualityCentralInterface#writeToNBT}/{@link DualityCentralInterface#readFromNBT}
 * 的键名、键类型与嵌套结构钉死。任何阶段跑红即说明该阶段破坏了兼容性，必须回退该步。</p>
 *
 * <p>键集合的构成（已核对 AE2-UEL 反编译源）：</p>
 * <ul>
 *   <li>{@code config} / {@code patterns} / {@code storage}：AE2 库存以<b>嵌套 NBTTagCompound</b>
 *       写出（{@code AppEngInternalInventory.serializeNBT()} 形如 {@code {Items:[...], Size:n}}），
 *       <b>不是</b>扁平列表——{@code NBTTagCompound#getCompoundTag} 在类型不符时静默返回空
 *       compound，写错类型不会报错、只会静默丢内容，因此必须断言 tag 类型；</li>
 *   <li>{@code BLOCK} / {@code INTERFACE_TERMINAL} / {@code UNLOCK}：{@code ConfigManager} 以
 *       {@code Settings.name()} 写扁平字符串键（{@code appeng/util/ConfigManager.java:61-65}）；</li>
 *   <li>{@code priority} / {@code boundBlockId} / {@code bindings} / {@code pendingVirtualProducts}。</li>
 * </ul>
 */
public class CentralInterfaceNbtCompatTest {

    private static final BlockPos POS_A = new BlockPos(10, 64, 20);
    private static final BlockPos POS_B = new BlockPos(11, 64, 20);

    /** 除 ConfigManager 三键与 boundBlockId（可缺省）之外的恒定键集合。 */
    private static final Set<String> FIXED_KEYS = new HashSet<>(Arrays.asList(
            "config", "patterns", "storage", "priority", "bindings"));

    /** ConfigManager 写出的扁平键（Settings.name()）。 */
    private static final Set<String> CONFIG_MANAGER_KEYS = new HashSet<>(Arrays.asList(
            "BLOCK", "INTERFACE_TERMINAL", "UNLOCK"));

    private DualityCentralInterface duality;

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        ICentralInterfaceHost host = mock(ICentralInterfaceHost.class);
        TileEntity tile = mock(TileEntity.class);
        when(host.getTileEntity()).thenReturn(tile);
        when(tile.getWorld()).thenReturn(mock(World.class));
        // 非 null 代理：Binding 增删会经 NetworkAccess.connection(host.getProxy()) 发包，
        // mock 的 isReady()/isActive() 默认 false，使该路径成为安全空操作。
        when(host.getProxy()).thenReturn(mock(AENetworkProxy.class));
        this.duality = new DualityCentralInterface(host);
    }

    // ---- 老存档加载 ----

    /**
     * 手写一份"老存档"格式的 tag（缺 {@code pendingVirtualProducts}，且故意不含三个库存键），
     * 验证旧存档仍可加载、且缺失键被容忍。
     */
    @Test
    public void legacyScalarAndBindingTagsStillLoad() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("BLOCK", "NO");
        legacy.setString("INTERFACE_TERMINAL", "YES");
        legacy.setString("UNLOCK", "NONE");
        legacy.setInteger("priority", 4);
        legacy.setString("boundBlockId", "minecraft:furnace");
        NBTTagList bindings = new NBTTagList();
        bindings.appendTag(bindingTag(POS_A, 0, "minecraft:furnace"));
        bindings.appendTag(bindingTag(POS_B, -1, "minecraft:furnace"));
        legacy.setTag("bindings", bindings);

        this.duality.readFromNBT(legacy);

        assertThat(this.duality.getBindings()).hasSize(2);
        assertThat(this.duality.getBindings().get(0).pos).isEqualTo(POS_A);
        assertThat(this.duality.getBindings().get(0).dimension).isZero();
        assertThat(this.duality.getBindings().get(1).pos).isEqualTo(POS_B);
        assertThat(this.duality.getBindings().get(1).dimension).isEqualTo(-1);
        assertThat(this.duality.getBindings().get(1).blockId).isEqualTo("minecraft:furnace");
    }

    // ---- 键集合与键类型锁定 ----

    @Test
    public void writeToNbtEmitsExactlyTheLegacyKeySet() {
        NBTTagCompound out = new NBTTagCompound();
        this.duality.writeToNBT(out);

        Set<String> expected = new HashSet<>(FIXED_KEYS);
        expected.addAll(CONFIG_MANAGER_KEYS);
        assertThat(out.getKeySet()).isEqualTo(expected);

        // boundBlockId 仅非空时出现；pendingVirtualProducts 仅队列非空时出现（保持"空则删键"现行为）
        assertThat(out.hasKey("boundBlockId")).isFalse();
        assertThat(out.hasKey("pendingVirtualProducts")).isFalse();
    }

    @Test
    public void configManagerDefaultsAreWrittenWithEnumNames() {
        NBTTagCompound out = new NBTTagCompound();
        this.duality.writeToNBT(out);

        // YesNo / LockCraftingMode 均未覆写 toString()，ConfigManager 写出枚举名
        assertThat(out.getString("BLOCK")).isEqualTo("NO");
        assertThat(out.getString("INTERFACE_TERMINAL")).isEqualTo("YES");
        assertThat(out.getString("UNLOCK")).isEqualTo("NONE");
    }

    @Test
    public void inventoriesAreWrittenAsNestedCompoundsNotLists() {
        NBTTagCompound out = new NBTTagCompound();
        this.duality.writeToNBT(out);

        // 写成列表会被 getCompoundTag 静默读成空 compound：不报错、只丢内容
        // （getTagId 返回 byte，Constants.NBT.* 是 int，故显式转型比较）
        for (String key : Arrays.asList("config", "patterns", "storage")) {
            assertThat(out.getTagId(key))
                    .as("%s 必须是嵌套 compound（AE2 库存序列化格式）", key)
                    .isEqualTo((byte) Constants.NBT.TAG_COMPOUND);
        }
        assertThat(out.getTagId("bindings")).isEqualTo((byte) Constants.NBT.TAG_LIST);
        assertThat(out.getTagId("priority")).isEqualTo((byte) Constants.NBT.TAG_INT);
    }

    @Test
    public void bindingEntriesKeepExactlyThreeKeys() {
        this.duality.addBinding(new TargetBinding(POS_A, 0, "minecraft:furnace"));

        NBTTagCompound out = new NBTTagCompound();
        this.duality.writeToNBT(out);

        assertThat(out.getString("boundBlockId")).isEqualTo("minecraft:furnace");
        NBTTagList bindings = out.getTagList("bindings", Constants.NBT.TAG_COMPOUND);
        assertThat(bindings.tagCount()).isEqualTo(1);
        // 多一键即失败：保证后续阶段不会把在途 session 状态偷塞进 bindings
        assertThat(bindings.getCompoundTagAt(0).getKeySet())
                .isEqualTo(new HashSet<>(Arrays.asList("pos", "dim", "blockId")));
    }

    // ---- 内容往返 ----

    @Test
    public void roundTripPreservesContents() {
        this.duality.getConfig().setStackInSlot(0, new ItemStack(Items.STICK, 5));
        this.duality.getStorage().setStackInSlot(4, new ItemStack(Items.COAL, 9));
        this.duality.getPatterns().setStackInSlot(0, new ItemStack(Items.STICK, 1));
        this.duality.addBinding(new TargetBinding(POS_A, 0, "minecraft:furnace"));
        this.duality.pendingProducts.addAll(Collections.singletonList(new ItemStack(Items.DIAMOND, 2)));

        NBTTagCompound out = new NBTTagCompound();
        this.duality.writeToNBT(out);
        assertThat(out.hasKey("pendingVirtualProducts")).isTrue();

        DualityCentralInterface restored = new DualityCentralInterface(mock(ICentralInterfaceHost.class));
        restored.readFromNBT(out);

        assertThat(restored.getConfig().getStackInSlot(0).getItem()).isEqualTo(Items.STICK);
        assertThat(restored.getConfig().getStackInSlot(0).getCount()).isEqualTo(5);
        assertThat(restored.getStorage().getStackInSlot(4).getItem()).isEqualTo(Items.COAL);
        assertThat(restored.getStorage().getStackInSlot(4).getCount()).isEqualTo(9);
        assertThat(restored.getPatterns().getStackInSlot(0).getItem()).isEqualTo(Items.STICK);
        assertThat(restored.getBindings()).hasSize(1);
        assertThat(restored.getBindings().get(0).pos).isEqualTo(POS_A);
        assertThat(restored.getBindings().get(0).blockId).isEqualTo("minecraft:furnace");
        assertThat(restored.pendingProducts.isEmpty()).isFalse();
        assertThat(restored.pendingProducts.drainAll().get(0).getCount()).isEqualTo(2);
    }

    // ---- helpers ----

    private static NBTTagCompound bindingTag(BlockPos pos, int dim, String blockId) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos.toLong());
        tag.setInteger("dim", dim);
        tag.setString("blockId", blockId);
        return tag;
    }
}
