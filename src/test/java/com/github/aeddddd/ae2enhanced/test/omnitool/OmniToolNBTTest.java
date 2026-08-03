package com.github.aeddddd.ae2enhanced.test.omnitool;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OmniToolNBT} NBT 键常量测试.
 */
class OmniToolNBTTest {

    @Test
    void testConstantValues() {
        assertThat(OmniToolNBT.MODE).isEqualTo("Mode");
        assertThat(OmniToolNBT.SILK_TOUCH).isEqualTo("SilkTouch");
        assertThat(OmniToolNBT.CHAOS_CORE).isEqualTo("ChaosCore");
        assertThat(OmniToolNBT.FORTUNE).isEqualTo("Fortune");
        assertThat(OmniToolNBT.BLINK_DIST).isEqualTo("BlinkDist");
        assertThat(OmniToolNBT.LAST_BLINK).isEqualTo("LastBlink");
        assertThat(OmniToolNBT.BREAK_COOLDOWN).isEqualTo("BreakCooldown");
        assertThat(OmniToolNBT.LAST_BREAK).isEqualTo("LastBreak");
        assertThat(OmniToolNBT.DROP_MODE).isEqualTo("DropMode");
        assertThat(OmniToolNBT.ANTI_HEAL).isEqualTo("AE2E_AntiHeal");
        assertThat(OmniToolNBT.CONFORMAL_CHARGE).isEqualTo("ConformalCharge");
        assertThat(OmniToolNBT.PARAM_ENABLED).isEqualTo("ParamEnabled");
        assertThat(OmniToolNBT.CHAOS_FORCE_KILL).isEqualTo("ChaosForceKill");
        assertThat(OmniToolNBT.ADVANCED_SILK_TOUCH).isEqualTo("AdvancedSilkTouch");
        assertThat(OmniToolNBT.BEDROCK_BREAKER).isEqualTo("BedrockBreaker");
        assertThat(OmniToolNBT.WALL_PHASE).isEqualTo("WallPhase");
        assertThat(OmniToolNBT.ENCHANTMENTS).isEqualTo("AE2E_Enchantments");
        assertThat(OmniToolNBT.CONFORMAL_INIT).isEqualTo("AE2E_ConformalInit");
        assertThat(OmniToolNBT.CONFORMAL_PRESERVED).isEqualTo("AE2E_ConformalPreserved");
    }

    @Test
    void testConstantsAreUnique() throws Exception {
        // 任意两个 NBT 键不允许重名,否则会发生状态互相覆盖
        Set<String> seen = new HashSet<>();
        for (Field field : OmniToolNBT.class.getDeclaredFields()) {
            if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String value = (String) field.get(null);
            assertThat(seen.add(value)).as("NBT 键重复: %s", value).isTrue();
        }
        assertThat(seen).hasSize(19);
    }
}
