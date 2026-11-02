package com.github.aeddddd.ae2enhanced.item.crafting;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 智能样板合成事件处理(仅服务端生效).
 *
 * <ul>
 *   <li>合并：智能样板 + 已编码样板 → 将已编码样板的配方追加到智能样板数据中,
 *       保存存储文件并更新输出样板的 NBT 快照</li>
 *   <li>回收：仅回收物品，共享文件保留，其他同 UUID 副本继续有效</li>
 * </ul>
 */
public final class SmartPatternMergeHelper {

    /**
     * 合并：输出为智能样板且合成格中存在智能样板 + 已编码样板时,追加配方.
     */
    private static void handleMerge(PlayerEvent.ItemCraftedEvent event, @Nonnull World world) {
        ItemStack result = event.crafting;
        if (!(result.getItem() instanceof ItemSmartPattern)) return;

        ItemStack smartInput = ItemStack.EMPTY;
        List<ItemStack> encodedPatterns = new ArrayList<>();
        for (int i = 0; i < event.craftMatrix.getSizeInventory(); i++) {
            ItemStack stack = event.craftMatrix.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ItemSmartPattern) {
                smartInput = stack;
            } else if (stack.getItem() instanceof ICraftingPatternItem) {
                encodedPatterns.add(stack);
            }
        }
        if (smartInput.isEmpty() || encodedPatterns.isEmpty()) return;

        UUID dataId = ItemSmartPattern.getPatternDataId(smartInput);
        if (dataId == null) return;
        long expectedRevision = ItemSmartPattern.getRevision(smartInput);
        SmartPatternRepository.UpdateResult update = SmartPatternRepository.get(world).edit(
                dataId, expectedRevision, data -> {
                    int max = ServerConfig.current().smartPattern.maxRecipes;
                    for (ItemStack pattern : encodedPatterns) {
                        if (data.getRecipeCount() >= max) break;
                        try {
                            ICraftingPatternDetails details =
                                    ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world);
                            if (details == null) continue;
                            data.appendRecipe(new SmartRecipe(details.getInputs(), details.getOutputs(), false));
                        } catch (Exception e) {
                            AE2Enhanced.LOGGER.warn(
                                    "[AE2E] Failed to merge encoded pattern into smart pattern: {}", e.toString());
                        }
                    }
                    return data;
                });
        if (!update.committed()) return;

        SmartPatternData data = update.snapshot().data();
        ItemStack updated = ItemSmartPattern.createPattern(dataId, ItemSmartPattern.getDisabledMask(smartInput),
                data.getRecipeCount(), data.getTargetBlockId(), update.snapshot().revision());
        result.setTagCompound(updated.getTagCompound());
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        World world = event.player.world;
        if (world.isRemote) return;
        handleMerge(event, world);
    }
}
