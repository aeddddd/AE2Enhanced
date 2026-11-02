package baubles.api;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

public interface IBauble {
    BaubleType getBaubleType(ItemStack stack);

    boolean canEquip(ItemStack stack, EntityLivingBase player);

    boolean willAutoSync(ItemStack stack, EntityLivingBase player);
}
