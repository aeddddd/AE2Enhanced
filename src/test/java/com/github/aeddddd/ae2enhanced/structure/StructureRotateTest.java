package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.block.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult;
import com.github.aeddddd.ae2enhanced.tile.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.tile.SupercausalStructure;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AssemblyStructure}、{@link SupercausalStructure}、{@link HyperdimensionalStructure}
 * 中 rotate 静态方法的行为一致性测试，以及 {@link AssemblyStructure#getOriginFromController}
 * 与 {@link SupercausalStructure.ValidationResult} 的契约测试。
 *
 * <p>三个类的 rotate 均为 public static，可直接调用；静态初始化块仅构造 BlockPos 集合，
 * 不依赖 Minecraft 运行环境。
 */
public class StructureRotateTest {

    /** 代表性相对坐标，x/z 非零且符号不同，y 非零用于验证 y 保持不变。 */
    private static final BlockPos REL = new BlockPos(3, 5, -7);

    /** NORTH 为恒等旋转：输出与输入相等（源码直接返回入参实例）。 */
    @Test
    public void testNorthIsIdentity() {
        assertThat(AssemblyStructure.rotate(REL, EnumFacing.NORTH)).isEqualTo(REL);
        assertThat(SupercausalStructure.rotate(REL, EnumFacing.NORTH)).isEqualTo(REL);
        assertThat(HyperdimensionalStructure.rotate(REL, EnumFacing.NORTH)).isEqualTo(REL);
        // 源码对 NORTH 直接 return rel，固化该行为
        assertThat(AssemblyStructure.rotate(REL, EnumFacing.NORTH)).isSameAs(REL);
    }

    /** SOUTH 旋转 180°：x、z 取反，y 不变。 */
    @Test
    public void testSouthNegatesXZ() {
        BlockPos expected = new BlockPos(-3, 5, 7);
        assertThat(AssemblyStructure.rotate(REL, EnumFacing.SOUTH)).isEqualTo(expected);
        assertThat(SupercausalStructure.rotate(REL, EnumFacing.SOUTH)).isEqualTo(expected);
        assertThat(HyperdimensionalStructure.rotate(REL, EnumFacing.SOUTH)).isEqualTo(expected);
    }

    /** EAST 旋转 90°：(x, y, z) → (-z, y, x)。 */
    @Test
    public void testEastRotates90Degrees() {
        BlockPos expected = new BlockPos(7, 5, 3);
        assertThat(AssemblyStructure.rotate(REL, EnumFacing.EAST)).isEqualTo(expected);
        assertThat(SupercausalStructure.rotate(REL, EnumFacing.EAST)).isEqualTo(expected);
        assertThat(HyperdimensionalStructure.rotate(REL, EnumFacing.EAST)).isEqualTo(expected);
    }

    /** WEST 旋转 -90°：(x, y, z) → (z, y, -x)。 */
    @Test
    public void testWestRotates90Degrees() {
        BlockPos expected = new BlockPos(-7, 5, -3);
        assertThat(AssemblyStructure.rotate(REL, EnumFacing.WEST)).isEqualTo(expected);
        assertThat(SupercausalStructure.rotate(REL, EnumFacing.WEST)).isEqualTo(expected);
        assertThat(HyperdimensionalStructure.rotate(REL, EnumFacing.WEST)).isEqualTo(expected);
    }

    /** 连续旋转四次（EAST）回到原点。 */
    @Test
    public void testFourRotationsReturnToOrigin() {
        for (RotateFunc rotate : allRotates()) {
            BlockPos pos = REL;
            for (int i = 0; i < 4; i++) {
                pos = rotate.apply(pos, EnumFacing.EAST);
            }
            assertThat(pos).isEqualTo(REL);
        }
    }

    /** EAST 旋转两次等价于 SOUTH 旋转一次；WEST 三次等价于 EAST 一次。 */
    @Test
    public void testRotationComposition() {
        for (RotateFunc rotate : allRotates()) {
            BlockPos eastTwice = rotate.apply(rotate.apply(REL, EnumFacing.EAST), EnumFacing.EAST);
            assertThat(eastTwice).isEqualTo(rotate.apply(REL, EnumFacing.SOUTH));

            BlockPos westThrice = REL;
            for (int i = 0; i < 3; i++) {
                westThrice = rotate.apply(westThrice, EnumFacing.WEST);
            }
            assertThat(westThrice).isEqualTo(rotate.apply(REL, EnumFacing.EAST));
        }
    }

    /** UP/DOWN 走 switch 的 default 分支：坐标不变（源码直接返回入参实例）。 */
    @Test
    public void testUpDownReturnUnchanged() {
        for (EnumFacing vertical : new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN}) {
            assertThat(AssemblyStructure.rotate(REL, vertical)).isEqualTo(REL);
            assertThat(SupercausalStructure.rotate(REL, vertical)).isEqualTo(REL);
            assertThat(HyperdimensionalStructure.rotate(REL, vertical)).isEqualTo(REL);
            assertThat(AssemblyStructure.rotate(REL, vertical)).isSameAs(REL);
        }
    }

    /** 三个结构类的 rotate 实现完全一致：同一输入在全部六个朝向下输出相同。 */
    @Test
    public void testRotateConsistentAcrossStructures() {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos assembly = AssemblyStructure.rotate(REL, facing);
            BlockPos supercausal = SupercausalStructure.rotate(REL, facing);
            BlockPos hyperdimensional = HyperdimensionalStructure.rotate(REL, facing);
            assertThat(assembly).isEqualTo(supercausal);
            assertThat(assembly).isEqualTo(hyperdimensional);
        }
    }

    /** getOriginFromController：控制器位置 + rotate((0,0,7), facing) 的固定偏移。 */
    @Test
    public void testGetOriginFromController() {
        BlockPos controller = new BlockPos(100, 64, -50);

        // NORTH: +(0,0,7)
        assertThat(AssemblyStructure.getOriginFromController(controller, EnumFacing.NORTH))
                .isEqualTo(new BlockPos(100, 64, -43));
        // SOUTH: +(0,0,-7)
        assertThat(AssemblyStructure.getOriginFromController(controller, EnumFacing.SOUTH))
                .isEqualTo(new BlockPos(100, 64, -57));
        // EAST: rotate((0,0,7)) = (-7,0,0)
        assertThat(AssemblyStructure.getOriginFromController(controller, EnumFacing.EAST))
                .isEqualTo(new BlockPos(93, 64, -50));
        // WEST: rotate((0,0,7)) = (7,0,0)
        assertThat(AssemblyStructure.getOriginFromController(controller, EnumFacing.WEST))
                .isEqualTo(new BlockPos(107, 64, -50));
    }

    /** ValidationResult 是纯数据持有者：构造参数可由 public 字段原样读回。 */
    @Test
    public void testValidationResultDataHolder() {
        Map<Block, Integer> missing = new HashMap<>();
        SupercausalStructure.ValidationResult result =
                new SupercausalStructure.ValidationResult(StructureValidationResult.VALID, missing, 4, 16);

        assertThat(result.passed).isTrue();
        assertThat(result.missing).isEmpty();
        assertThat(result.causalAnchorCount).isEqualTo(4);
        assertThat(result.parallelLimit).isEqualTo(16);
    }

    /** ValidationResult 的 missing 被包装为不可修改 Map。 */
    @Test
    public void testValidationResultMissingMapIsUnmodifiable() {
        Map<Block, Integer> missing = new HashMap<>();
        SupercausalStructure.ValidationResult result =
                new SupercausalStructure.ValidationResult(StructureValidationResult.INVALID, missing, 0, 0);

        assertThatThrownBy(() -> result.missing.put(null, 1))
                .isInstanceOf(UnsupportedOperationException.class);

        // 空构造时 missing 不为 null
        SupercausalStructure.ValidationResult empty =
                new SupercausalStructure.ValidationResult(StructureValidationResult.VALID, Collections.emptyMap(), 8, 64);
        assertThat(empty.missing).isNotNull().isEmpty();
    }

    /** 函数式接口，便于对三个类的同名静态方法做参数化遍历。 */
    private interface RotateFunc {
        BlockPos apply(BlockPos rel, EnumFacing facing);
    }

    private static RotateFunc[] allRotates() {
        return new RotateFunc[]{
                AssemblyStructure::rotate,
                SupercausalStructure::rotate,
                HyperdimensionalStructure::rotate
        };
    }
}
