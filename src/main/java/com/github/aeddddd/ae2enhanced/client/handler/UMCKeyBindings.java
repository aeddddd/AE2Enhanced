package com.github.aeddddd.ae2enhanced.client.handler;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 通用内存卡(UMC)的可配置键位.
 *
 * <p>所有键位均注册进 Forge KeyBinding 体系,玩家可在 控制(Controls) 菜单中自由改绑,
 * 不再锁死 Ctrl/Alt/Shift.三个键的职责：</p>
 * <ul>
 *   <li>{@link #SELECT_MODIFIER}: 按住 + 右键 = 选取/取消选取目标(默认左 Ctrl)</li>
 *   <li>{@link #CLEAR_MODIFIER}: 按住 + 右键 = 清空中枢接口/回收节点绑定(默认左 Alt)</li>
 *   <li>{@link #TOOLTIP_DETAIL}: 在物品提示中按住显示完整详情(默认左 Shift)</li>
 * </ul>
 *
 * <p>前两个是"修饰键"用法,判定走 {@link #isDown(KeyBinding)} 直接查询底层键码,
 * 因此即使用户改绑到鼠标侧键也能正常工作.</p>
 */
@SideOnly(Side.CLIENT)
public final class UMCKeyBindings {

    public static final KeyBinding SELECT_MODIFIER = new KeyBinding(
            "key.ae2enhanced.umc_select",
            KeyConflictContext.IN_GAME,
            Keyboard.KEY_LCONTROL,
            "key.categories.ae2enhanced"
    );

    public static final KeyBinding CLEAR_MODIFIER = new KeyBinding(
            "key.ae2enhanced.umc_clear",
            KeyConflictContext.IN_GAME,
            Keyboard.KEY_LMENU,
            "key.categories.ae2enhanced"
    );

    public static final KeyBinding TOOLTIP_DETAIL = new KeyBinding(
            "key.ae2enhanced.umc_tooltip_detail",
            Keyboard.KEY_LSHIFT,
            "key.categories.ae2enhanced"
    );

    private UMCKeyBindings() {
    }

    public static void register() {
        ClientRegistry.registerKeyBinding(SELECT_MODIFIER);
        ClientRegistry.registerKeyBinding(CLEAR_MODIFIER);
        ClientRegistry.registerKeyBinding(TOOLTIP_DETAIL);
    }

    /**
     * 查询键位当前是否被按住(直接查底层键码,支持鼠标键;GUI 打开时同样有效).
     * 不依赖 {@link KeyBinding#isKeyDown()} 的 pressed 状态——该状态在 GUI 打开时会停滞.
     */
    public static boolean isDown(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code == Keyboard.KEY_NONE) return false;
        if (code < 0) {
            // 鼠标键:键码 = button - 100
            return Mouse.isButtonDown(code + 100);
        }
        return Keyboard.isKeyDown(code);
    }

    /**
     * 返回键位的本地化显示名(如 "LCONTROL"、"BUTTON 4"),用于 tooltip 动态展示.
     */
    public static String displayName(KeyBinding binding) {
        return GameSettings.getKeyDisplayString(binding.getKeyCode());
    }

    public static boolean isSelectDown() {
        return isDown(SELECT_MODIFIER);
    }

    public static boolean isClearDown() {
        return isDown(CLEAR_MODIFIER);
    }

    public static boolean isTooltipDetailDown() {
        return isDown(TOOLTIP_DETAIL);
    }
}
