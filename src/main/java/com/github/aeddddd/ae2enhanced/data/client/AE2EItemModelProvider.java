package com.github.aeddddd.ae2enhanced.data.client;

import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.ModItems;

/**
 * 物品模型数据生成器。
 * <p>为所有注册物品生成默认手持模型。</p>
 */
public class AE2EItemModelProvider extends ItemModelProvider {

    public AE2EItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AE2Enhanced.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.ASSEMBLY_PARALLEL_UPGRADE.get());
        basicItem(ModItems.ASSEMBLY_SPEED_UPGRADE.get());
        basicItem(ModItems.ASSEMBLY_CAPACITY_UPGRADE.get());
        basicItem(ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE.get());
    }
}
