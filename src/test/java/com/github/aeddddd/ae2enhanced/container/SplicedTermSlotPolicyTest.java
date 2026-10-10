package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;

import org.junit.jupiter.api.Test;

/**
 * 阶段 2a 的 shift-click / 槽位清扫策略：只有"有面板承接"的槽位能参与交互。
 *
 * <p>覆盖两条硬约束：</p>
 * <ul>
 *   <li>合成台/样板/右侧存储/升级槽（本阶段无面板）既不能作为 shift-click 来源，也不能作为目标；</li>
 *   <li>清扫只搬动这些槽位（移到屏幕外），有面板承接的槽位坐标保持面板摆放的结果。</li>
 * </ul>
 */
class SplicedTermSlotPolicyTest {

    private static Slot slot(int x, int y) {
        return new Slot(new InventoryBasic("test", false, 1), 0, x, y);
    }

    private static SplicedTermSlotPolicy policy(List<Slot> player, List<Slot> viewCells) {
        return new SplicedTermSlotPolicy(player, viewCells);
    }

    @Test
    void hostedSlotsAreExactlyPlayerInventoryAndViewCells() {
        Slot playerSlot = slot(10, 10);
        Slot viewCell = slot(20, 20);
        Slot crafting = slot(30, 30);
        Slot storage = slot(40, 40);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(playerSlot), Arrays.asList(viewCell));

        assertTrue(policy.isPlayerSlot(playerSlot));
        assertTrue(policy.isViewCellSlot(viewCell));
        assertTrue(policy.isHosted(playerSlot));
        assertTrue(policy.isHosted(viewCell));
        assertFalse(policy.isHosted(crafting));
        assertFalse(policy.isHosted(storage));
        assertEquals(1, policy.playerSlots().size());
        assertEquals(1, policy.viewCellSlots().size());
    }

    @Test
    void membershipIsByIdentityNotByCoordinates() {
        Slot hosted = slot(10, 10);
        Slot impostor = slot(10, 10);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(hosted), new ArrayList<Slot>());

        assertTrue(policy.isHosted(hosted));
        assertFalse(policy.isHosted(impostor), "同坐标的不同槽位对象不属于面板");
    }

    @Test
    void onlyTransferableModulesCanReceiveAShiftClick() {
        Slot playerSlot = slot(10, 10);
        Slot viewCell = slot(20, 20);
        Slot crafting = slot(30, 30);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(playerSlot), Arrays.asList(viewCell));

        // 来源：玩家背包 / view cell（角色 BOTH）都可以
        assertTrue(policy.canShiftClickFrom(playerSlot));
        assertTrue(policy.canShiftClickFrom(viewCell));
        assertFalse(policy.canShiftClickFrom(crafting), "无面板承接的槽位不能作为来源");

        // 目标：玩家背包与 view cell 都接收（与旧终端 AE2 原生语义一致），未承接的槽位拒绝
        assertTrue(policy.canShiftClickInto(playerSlot));
        assertTrue(policy.canShiftClickInto(viewCell));
        assertFalse(policy.canShiftClickInto(crafting), "禁止物品被 shift-click 进不可见槽位");
    }

    @Test
    void shiftClickTargetsDependOnTheSourceSide() {
        Slot playerSlot = slot(10, 10);
        Slot viewCell = slot(20, 20);
        Slot storage = slot(30, 30);
        Slot craftingMatrix = slot(40, 40);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(playerSlot), Arrays.asList(viewCell));
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, Arrays.asList(storage));
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX, Arrays.asList(craftingMatrix));

        // 玩家侧来源 → 除玩家外的 BOTH 模块（view cell / 存储），合成台矩阵不接收
        List<Slot> fromPlayer = policy.shiftClickTargetsFor(playerSlot);
        assertEquals(Arrays.asList(viewCell, storage), fromPlayer);

        // 非玩家侧来源 → 玩家背包
        assertEquals(Arrays.asList(playerSlot), policy.shiftClickTargetsFor(storage));
        // 合成台矩阵只能作为来源（SOURCE_ONLY），目标仍然是玩家背包
        assertEquals(Arrays.asList(playerSlot), policy.shiftClickTargetsFor(craftingMatrix));
    }

    @Test
    void unhostedSlotsAreNotTransferable() {
        Slot playerSlot = slot(10, 11);
        Slot viewCell = slot(20, 21);
        Slot crafting = slot(30, 31);
        Slot pattern = slot(40, 41);
        Slot storage = slot(50, 51);
        Slot upgrade = slot(60, 61);
        List<Slot> all = Arrays.asList(crafting, playerSlot, pattern, viewCell, storage, upgrade);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(playerSlot), Arrays.asList(viewCell));

        // 未登记的槽位不被承接（＝不参与交互；坐标由各面板自行 hideSlot）
        assertFalse(policy.isHosted(crafting));
        assertFalse(policy.isHosted(pattern));
        assertFalse(policy.isHosted(storage));
        assertFalse(policy.isHosted(upgrade));
        assertFalse(policy.canShiftClickFrom(crafting));
        assertFalse(policy.canShiftClickInto(crafting));
        assertTrue(policy.isHosted(playerSlot));
        assertTrue(policy.isHosted(viewCell));
        assertEquals(-9999, SplicedTermSlotPolicy.HIDDEN_SLOT_POSITION,
                "屏幕外坐标与 ModulePanel.hideSlot 保持一致");
    }

    /**
     * A17 修复的契约：view cell 是"双向"槽位组，且 {@code ContainerSplicedTerm.transferStackInSlot}
     * 只把它当作玩家侧 view cell 的唯一去处（不再先塞 ME 网络）。
     *
     * <p>这里固定两条前置：面板可见 → 组里就是那 5 个槽位且可作为目标；面板不可见（组为空）→
     * 玩家侧来源拿不到任何 view cell 目标，容器会拒收（与"关掉的面板不接收"一致）。</p>
     */
    @Test
    void viewCellGroupIsTheOnlyTargetForPlayerSideViewCells() {
        Slot playerSlot = slot(10, 12);
        Slot viewCell = slot(20, 22);
        Slot storage = slot(30, 32);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(playerSlot), Arrays.asList(viewCell));
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, Arrays.asList(storage));

        assertEquals(SplicedTermSlotPolicy.TransferRole.BOTH,
                policy.roleOf(viewCell), "view cell 槽必须既能进也能出");
        assertEquals(Arrays.asList(viewCell), policy.slotsOf(SplicedTermSlotPolicy.MODULE_VIEWCELL));
        // 取回方向：view cell → 玩家背包
        assertEquals(Arrays.asList(playerSlot), policy.shiftClickTargetsFor(viewCell));
        // 面板被关闭（组清空）时，玩家侧拿不到 view cell 目标 → 容器拒收而不是塞进别处
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_VIEWCELL, new ArrayList<Slot>());
        assertTrue(policy.slotsOf(SplicedTermSlotPolicy.MODULE_VIEWCELL).isEmpty());
        assertFalse(policy.canShiftClickInto(viewCell));
    }

    @Test
    void nullCollectionsAreTolerated() {
        SplicedTermSlotPolicy empty = policy(null, null);
        assertFalse(empty.isHosted(slot(0, 0)));
        assertFalse(empty.canShiftClickFrom(slot(1, 2)));
        assertFalse(empty.canShiftClickInto(slot(1, 2)));
        assertTrue(empty.shiftClickTargetsFor(slot(1, 2)).isEmpty());
    }

    @Test
    void repoSlotsAreHostedButNeverShiftClickable() {
        Slot playerSlot = slot(10, 10);
        Slot repoSlot = slot(20, 20);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(playerSlot), new ArrayList<Slot>());

        policy.setRepoSlots(Arrays.asList(repoSlot));

        assertTrue(policy.isRepoSlot(repoSlot));
        assertTrue(policy.isHosted(repoSlot));
        // 有面板承接的槽位坐标由面板 layoutSlots 负责，策略不碰
        assertEquals(20, repoSlot.xPos);

        // ME 展示槽位不走 ContainerSplicedTerm 的槽位转移：AE2 对 SlotME 的 shift-click
        // 由 handleMouseClick 发 InventoryAction，若放进来会变成"凭空复制进背包"
        assertFalse(policy.canShiftClickFrom(repoSlot));
        assertFalse(policy.canShiftClickInto(repoSlot));
    }

    @Test
    void repoSlotRegistrationReplacesThePreviousGeneration() {
        Slot first = slot(10, 10);
        Slot second = slot(20, 20);
        SplicedTermSlotPolicy policy = policy(null, null);

        policy.setRepoSlots(Arrays.asList(first, second));
        assertTrue(policy.isRepoSlot(first));

        // 形状变化后 ME 槽整批重建：旧引用必须立即失效
        Slot rebuilt = slot(30, 30);
        policy.setRepoSlots(Arrays.asList(rebuilt));
        assertFalse(policy.isRepoSlot(first));
        assertFalse(policy.isRepoSlot(second));
        assertTrue(policy.isRepoSlot(rebuilt));

        policy.setRepoSlots(null);
        assertFalse(policy.isRepoSlot(rebuilt));
    }

    @Test
    void moduleIdsMatchThePanelIds() {
        // 策略里的模块常量必须与 client/gui/panel/*Panel.MODULE_ID 逐一对应，
        // 否则面板登记了槽位、策略却认不出来（会掉进清扫）。
        assertEquals(com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel.MODULE_ID,
                SplicedTermSlotPolicy.MODULE_REPO);
        assertEquals(com.github.aeddddd.ae2enhanced.client.gui.panel.PlayerInvPanel.MODULE_ID,
                SplicedTermSlotPolicy.MODULE_PLAYER);
        assertEquals(com.github.aeddddd.ae2enhanced.client.gui.panel.ViewCellPanel.MODULE_ID,
                SplicedTermSlotPolicy.MODULE_VIEWCELL);
        assertEquals(com.github.aeddddd.ae2enhanced.client.gui.panel.CraftingPanel.MODULE_ID,
                SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX);
        assertEquals(com.github.aeddddd.ae2enhanced.client.gui.panel.StoragePanel.MODULE_ID,
                SplicedTermSlotPolicy.MODULE_STORAGE);
        assertEquals(com.github.aeddddd.ae2enhanced.client.gui.panel.UpgradePanel.MODULE_ID,
                SplicedTermSlotPolicy.MODULE_UPGRADE);
    }

    @Test
    void transferRolesFollowTheAe2NativeShiftClickSemantics() {
        assertEquals(SplicedTermSlotPolicy.TransferRole.BOTH,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_PLAYER));
        assertEquals(SplicedTermSlotPolicy.TransferRole.BOTH,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_VIEWCELL));
        assertEquals(SplicedTermSlotPolicy.TransferRole.BOTH,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_STORAGE));
        assertEquals(SplicedTermSlotPolicy.TransferRole.BOTH,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_UPGRADE));
        // 合成台矩阵：可收回、不被填充
        assertEquals(SplicedTermSlotPolicy.TransferRole.SOURCE_ONLY,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX));
        // 合成输出（AE2 的 CRAFT_SHIFT 处理）与 repo 的 ME 展示槽都不走槽位转移
        assertEquals(SplicedTermSlotPolicy.TransferRole.NONE,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_CRAFTING_OUTPUT));
        assertEquals(SplicedTermSlotPolicy.TransferRole.NONE,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_REPO));
        // 未知模块按安全默认处理
        assertEquals(SplicedTermSlotPolicy.TransferRole.NONE,
                SplicedTermSlotPolicy.roleFor("pattern"));
    }

    @Test
    void craftingSlotsAreSourceOnlyAndOutputIsInert() {
        Slot matrix = slot(10, 10);
        Slot output = slot(20, 20);
        Slot player = slot(30, 30);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(player), null);
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX, Arrays.asList(matrix));
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_CRAFTING_OUTPUT, Arrays.asList(output));

        // 矩阵可以 shift-click 收回背包，但不会被填充
        assertTrue(policy.canShiftClickFrom(matrix));
        assertFalse(policy.canShiftClickInto(matrix));
        assertEquals(Arrays.asList(player), policy.shiftClickTargetsFor(matrix));

        // 输出槽完全不参与（否则"产出进背包但材料不消耗"= 复制）
        assertFalse(policy.canShiftClickFrom(output));
        assertFalse(policy.canShiftClickInto(output));
        assertTrue(policy.shiftClickTargetsFor(output).isEmpty());
    }

    @Test
    void hiddenModuleSlotsStopBeingHostedAndTransferable() {
        Slot storage = slot(10, 10);
        Slot player = slot(20, 20);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(player), null);
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, Arrays.asList(storage));
        assertTrue(policy.isHosted(storage));
        assertTrue(policy.canShiftClickInto(storage));

        // 面板隐藏 → 面板注销自己的模块组：槽位不再被承接、也不再参与 shift-click
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, null);
        assertFalse(policy.isHosted(storage));
        assertFalse(policy.canShiftClickFrom(storage));
        assertFalse(policy.canShiftClickInto(storage));
        // 没有其它已承接的非玩家模块 → 玩家侧来源没有槽位目标（只剩 ME 网络那条路）
        assertTrue(policy.shiftClickTargetsFor(player).isEmpty());

    }

    @Test
    void patternSlotsAreInertButThePatternFeedSlotsTransfer() {
        Slot patternSlot = slot(10, 10);
        Slot feedIn = slot(11, 11);
        Slot feedOut = slot(12, 12);
        Slot player = slot(20, 20);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(player), null);
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_PATTERN, Arrays.asList(patternSlot));
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_PATTERN_FEED, Arrays.asList(feedIn, feedOut));

        // 编码区主体（SlotFake + SlotPatternTerm 产出槽）：AE2 客户端有专门分支，从不进槽位转移
        assertTrue(policy.isHosted(patternSlot));
        assertFalse(policy.canShiftClickFrom(patternSlot));
        assertFalse(policy.canShiftClickInto(patternSlot));
        assertTrue(policy.shiftClickTargetsFor(patternSlot).isEmpty());

        // 样板 in/out（SlotRestrictedInput）：与旧终端一致，可进可出
        assertTrue(policy.canShiftClickFrom(feedIn));
        assertTrue(policy.canShiftClickInto(feedIn));
        assertEquals(Arrays.asList(player), policy.shiftClickTargetsFor(feedIn));
        assertTrue(policy.shiftClickTargetsFor(player).contains(feedIn));
    }

    @Test
    void patternRolesMatchTheAe2SlotFamilySemantics() {
        assertEquals(SplicedTermSlotPolicy.TransferRole.NONE,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_PATTERN));
        assertEquals(SplicedTermSlotPolicy.TransferRole.BOTH,
                SplicedTermSlotPolicy.roleFor(SplicedTermSlotPolicy.MODULE_PATTERN_FEED));
    }

    @Test
    void moduleRegistrationKeepsItsOrderAcrossRegistrations() {
        Slot p = slot(1, 1);
        Slot v = slot(2, 2);
        Slot s = slot(3, 3);
        SplicedTermSlotPolicy policy = policy(Arrays.asList(p), Arrays.asList(v));
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, Arrays.asList(s));
        // 再次登记存储（换集合）不应改变模块顺序 → 玩家侧目标顺序仍是 view cell、存储
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, Arrays.asList(s));
        assertEquals(Arrays.asList(v, s), policy.shiftClickTargetsFor(p));
        assertEquals(3, policy.hostedCount());
    }
}
