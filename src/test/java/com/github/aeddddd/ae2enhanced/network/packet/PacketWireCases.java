package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.centralinterface.packet.PacketVirtualCraftingParticles;
import com.github.aeddddd.ae2enhanced.chamber.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.container.CraftingStatus;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayAction;
import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayTick;
import com.github.aeddddd.ae2enhanced.item.packet.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.recycler.packet.PacketRecyclerSync;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.specialcrafting.packet.PacketSpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import com.github.aeddddd.ae2enhanced.terminal.packet.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketCentralBindingOutline;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketChamberAction;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketChunkPowerNodeAction;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketChunkPowerNodeSync;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.*;

/** 每个 discriminator 唯一、固定且包含代表性非默认字段的线上样本。 */
final class PacketWireCases {

    private static final BlockPos POS = new BlockPos(123, 64, -456);
    private static final BlockPos OTHER_POS = new BlockPos(-789, 201, 42);

    private PacketWireCases() {
    }

    static Map<Integer, IMessage> createAll() {
        Bootstrap.register();
        Map<Integer, IMessage> cases = new LinkedHashMap<>();
        cases.put(0, new PacketRequestAssembly(POS));
        cases.put(1, new PacketPatternPage(POS, 7));
        cases.put(2, new PacketCraftRequestLong(5_000_000_000L, true));
        NBTTagCompound resource = new NBTTagCompound();
        resource.setString("kind", "mana");
        resource.setLong("amount", 4_000_000_000L);
        resource.setBoolean("shift", true);
        cases.put(3, new PacketMEMonitorableAction(PacketMEMonitorableAction.RESOURCE_ACTION, resource));
        cases.put(4, new PacketUniversalBusConfig(2));
        cases.put(5, new PacketStockingBusConfig(7, 5_000_000_000L));
        cases.put(6, new PacketCollectorConfig(POS, -1, true, 3, -2, 5,
                -8, -4, -2, 9, 11, 13, true));
        cases.put(7, new PacketUMCAction(PacketUMCAction.ActionType.BIND_TARGET, POS, EnumFacing.NORTH));
        cases.put(8, new PacketOmniTermAction("PanelVisibility", "37"));
        Map<Integer, ItemStack> inputs = new TreeMap<>();
        inputs.put(0, stack(Items.DYE, 2, 4));
        inputs.put(4, stack(Items.STICK, 3, 0));
        Map<Integer, ItemStack> outputs = new TreeMap<>();
        outputs.put(1, stack(Items.DIAMOND, 1, 0));
        cases.put(9, new PacketLoadOmniRecipe((byte) 2, true, 3, inputs, outputs));
        cases.put(10, new PacketSetSlotAmount(1, 8, 123456789));
        cases.put(11, new PacketOmniCraftingUpdate(Collections.singletonList(
                new CraftingStatus(ae(Items.APPLE, 7), 3_000_000_000L, 8_000_000_000L))));
        cases.put(15, new PacketOmniPageRequest("蓝·Ω", (byte) 1, (byte) 2, (byte) 1,
                (byte) 2, 90, 45, Collections.singletonList(
                        new ItemDescriptor(stack(Items.DYE, 1, 4)))));
        cases.put(16, new PacketOmniPageResult(137, 90, Collections.singletonList(
                new PacketOmniPageResult.Entry(130, ae(Items.DIAMOND, 1), 5_000_000_000L))));
        cases.put(17, new PacketOmniUpdateNotify());
        cases.put(18, new PacketOpenOmniTerminal());
        cases.put(19, new PacketToggleMagnet());
        IAEItemStack picked = ae(Items.DYE, 1, 4);
        picked.setStackSize(5_000_000_000L);
        picked.setCraftable(true);
        cases.put(20, new PacketPickerAction(picked));
        cases.put(21, new PacketSmartPatternToggle(POS, 17));
        cases.put(22, new PacketSmartPatternScroll(POS, 23));
        cases.put(23, new PacketSmartPatternEncode(POS));
        NBTTagCompound pattern = new NBTTagCompound();
        pattern.setString("targetBlockId", "minecraft:crafting_table");
        pattern.setLong("revision", 42L);
        cases.put(24, new PacketSmartPatternBind(POS, pattern, OTHER_POS, 7));
        cases.put(25, new PacketSmartPatternModify(POS, "lock", 11));
        cases.put(26, new PacketSmartPatternReplace(POS,
                stack(Items.DYE, 2, 4), stack(Items.REDSTONE, 3, 0)));
        cases.put(27, new PacketSmartPatternMiniGuiScroll(POS, 19));
        cases.put(28, new PacketOmniToolMode());
        cases.put(29, new PacketOmniToolSilkTouch());
        cases.put(30, new PacketOmniToolDropMode());
        cases.put(31, new PacketOpenOmniToolGui(1));
        NBTTagCompound enchantment = new NBTTagCompound();
        enchantment.setShort("id", (short) 16);
        enchantment.setShort("lvl", (short) 5);
        NBTTagList enchantments = new NBTTagList();
        enchantments.appendTag(enchantment);
        cases.put(32, new PacketOmniToolConfig(3, 2, true, 4, 12.75D, 9, 0x1234,
                true, false, true, true, 14, 6.5F, 3, enchantments));
        cases.put(33, new PacketChunkPowerHighlight(Arrays.asList(POS, OTHER_POS), 240));
        cases.put(34, new PacketVirtualCraftingParticles(Arrays.asList(
                new PacketVirtualCraftingParticles.ParticleTarget(POS, 42, 17, 0xFF336699),
                new PacketVirtualCraftingParticles.ParticleTarget(OTHER_POS, 7, 3, 0xFFAA55FF))));
        cases.put(35, new PacketRecyclerSync(POS, 13, 5_000_000_000L, true, false));
        cases.put(36, new PacketEMCInterfaceBind(POS));
        cases.put(37, new PacketPlacementUndo());
        cases.put(38, new PacketPlacementCablePlace(POS, OTHER_POS));
        cases.put(39, new PacketPlacementSelectPreset(9));
        cases.put(40, new PacketOmniToolPlacementSubMode(true));
        cases.put(41, new PacketPersonalDimensionRules(rules()));
        cases.put(42, new PacketPersonalDimensionRulesSync(rules()));
        cases.put(43, new PacketPersonalDimensionRegistrySync(Arrays.asList(-17, 0, 2048)));
        cases.put(44, new PacketEnergyStorageBusAction(PacketEnergyStorageBusAction.ACTION_PARTITION));
        PacketChamberSync chamber = new PacketChamberSync(POS, 4_000_000_000L,
                9_000_000_000L, 64, 17, 3, 2);
        chamber.addInput(stack(Items.DYE, 2, 4), 5_000_000_000L);
        chamber.addOutput(stack(Items.DIAMOND, 1, 0), 6_000_000_000L);
        chamber.addJob(new PacketChamberSync.JobView(stack(Items.APPLE, 1, 0),
                23, 47, 101));
        cases.put(45, chamber);
        cases.put(46, new PacketChamberAction(POS, PacketChamberAction.ACTION_CYCLE_REDSTONE, "next"));
        IAEItemStack planOutput = ae(Items.DIAMOND, 1);
        planOutput.setStackSize(4_000_000_000L);
        Map<IAEItemStack, SpecialPlanInfo.Entry> entries = new LinkedHashMap<>();
        entries.put(ae(Items.DYE, 1, 4), new SpecialPlanInfo.Entry(
                SpecialPlanInfo.KIND_CYCLE, 17, 101, 67, 0, 13));
        Map<IAEItemStack, Long> callCounts = new LinkedHashMap<>();
        callCounts.put(ae(Items.DIAMOND, 1), 5_000_000_000L);
        cases.put(48, new PacketSpecialPlanInfo(planOutput,
                new SpecialPlanInfo(entries, callCounts, 2)));
        cases.put(49, new PacketDisplayAction(POS, PacketDisplayAction.ACTION_SET_RANGE, 3));
        cases.put(50, new PacketDisplayTick(POS,
                new long[] { -1, 2, 300, 4_000_000_000L, 5, -6, 70, 800 }, 0xA5, true,
                9_000_000_000L));
        List<IAEItemStack> fillInputs = new ArrayList<>();
        fillInputs.add(ae(Items.DYE, 2, 4));
        fillInputs.add(null);
        fillInputs.add(ae(Items.STICK, 3));
        cases.put(51, new PacketSmartPatternFill(POS, fillInputs,
                Collections.singletonList(ae(Items.DIAMOND, 1))));
        cases.put(52, new PacketOpenRingGui());
        NBTTagCompound ring = new NBTTagCompound();
        ring.setBoolean("flight", true);
        ring.setFloat("reach", 7.25F);
        ring.setInteger("jumpPct", 175);
        cases.put(53, new PacketRingConfig(ring));
        cases.put(54, new PacketRingManualHeal());
        cases.put(55, new PacketRingToggleFlight());
        cases.put(56, new PacketChunkPowerNodeSync(POS, true, false, 5_000_000_000L,
                Arrays.asList(
                        new PacketChunkPowerNodeSync.TargetInfo(OTHER_POS,
                                stack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.FURNACE), 1, 0),
                                "tile.minecraft.furnace", true, 123456789L),
                        new PacketChunkPowerNodeSync.TargetInfo(POS, ItemStack.EMPTY,
                                "tile.example.machine", false, 987654321L))));
        cases.put(57, new PacketChunkPowerNodeAction(POS, OTHER_POS, true));
        cases.put(58, new PacketEMCInterfaceMode(POS));
        cases.put(59, new PacketCentralBindingOutline(-1, POS, Arrays.asList(
                new TargetBinding(OTHER_POS, 7, "minecraft:furnace"),
                new TargetBinding(POS, -3, "minecraft:crafting_table"))));
        cases.put(60, new PacketOmniLayout(
                "{\"version\":6,\"roots\":[{\"rootId\":\"root:repo\",\"bounds\":{\"x\":10,\"y\":20}}]}"));
        cases.put(63, new PacketSmartPatternEdit(POS, 42L, "addRecipe"));
        return Collections.unmodifiableMap(cases);
    }

    static byte[] encode(IMessage packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    static IMessage decode(Class<? extends IMessage> type, byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            IMessage packet = type.newInstance();
            packet.fromBytes(buffer);
            if (buffer.isReadable()) {
                throw new AssertionError(type.getSimpleName() + " 未消费全部 " + bytes.length
                        + " 字节，剩余 " + buffer.readableBytes());
            }
            return packet;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("无法无参构造 " + type.getName(), e);
        } finally {
            buffer.release();
        }
    }

    private static PersonalDimensionRules rules() {
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = true;
        rules.lockWeather = true;
        rules.lockTime = true;
        rules.daylightCycle = false;
        rules.timeValue = 13_579L;
        rules.flightEnabled = true;
        rules.movementSpeed = 0.35F;
        rules.noFlightInertia = true;
        return rules;
    }

    private static IAEItemStack ae(net.minecraft.item.Item item, int count) {
        return ae(item, count, 0);
    }

    private static IAEItemStack ae(net.minecraft.item.Item item, int count, int meta) {
        IAEItemStack stack = AEItemStack.fromItemStack(stack(item, count, meta));
        if (stack == null) {
            throw new IllegalStateException("AEItemStack 样本创建失败");
        }
        return stack;
    }

    private static ItemStack stack(net.minecraft.item.Item item, int count, int meta) {
        ItemStack stack = new ItemStack(item, count, meta);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("wireCase", "m0");
        tag.setInteger("meta", meta);
        stack.setTagCompound(tag);
        return stack;
    }
}
