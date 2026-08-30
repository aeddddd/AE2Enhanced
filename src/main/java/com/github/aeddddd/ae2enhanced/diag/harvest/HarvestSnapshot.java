package com.github.aeddddd.ae2enhanced.diag.harvest;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * 整合包配方快照（运行时采集的终态注册表 → JSON).
 * <p>由 {@code /ae2e harvest} 在真实整合包实例内生成（CraftTweaker 执行完毕后的
 * 终态，remove 语义天然解决）;测试侧 ModpackFixture 加载后重放为 dummy Item +
 * 等价样板网络。快照存放于 research/harvest/,不入 Git.</p>
 */
public final class HarvestSnapshot {

    private static final Gson GSON = new Gson();

    public int formatVersion = 2;
    public String timestamp;
    /** modid → version. */
    public Map<String, String> mods = new LinkedHashMap<>();
    /** 矿词名 → 成员物品列表(采集期已展开). */
    public Map<String, List<StackRef>> oreDict = new LinkedHashMap<>();
    /** 合成台配方终态(shaped/shapeless/ore 变体). */
    public List<CraftEntry> crafting = new ArrayList<>();
    /** 熔炉配方终态. */
    public List<FurnaceEntry> furnace = new ArrayList<>();
    /** 机器配方(ExtendedCrafting/Thaumcraft/ModularMachinery 等,按 mod 存在性采集). */
    public List<MachineEntry> machines = new ArrayList<>();
    public Stats stats = new Stats();

    /** 物品引用(注册名 + meta + 数量;NBT 以 SNBT 摘要保留). */
    public static final class StackRef {
        public String id;
        public int meta;
        public int count = 1;
        public String nbt;
    }

    /** 合成台配方.slots 为逐槽 matchingStacks(空槽 = 空列表),row-major. */
    public static final class CraftEntry {
        public String name;
        /** shaped / shapeless / shaped_ore / shapeless_ore / dynamic:<类名> / unknown:<类名>. */
        public String type;
        public Integer width;
        public Integer height;
        public StackRef output;
        public List<List<StackRef>> slots = new ArrayList<>();
        /**
         * 配方执行后返还的槽位下标(容器物/CrT .reuse()/工具损耗返还),
         * 由采集期 getRemainingItems 实采得出;null/空 = 全部消耗(旧快照兼容).
         */
        public List<Integer> returnedSlots;
    }

    public static final class FurnaceEntry {
        public StackRef input;
        public StackRef output;
    }

    /** 机器配方.inputs 为逐成分 matchingStacks(矿词成分已展开为多候选). */
    public static final class MachineEntry {
        public String mod;
        public String machine;
        /** combination / table / ender / compressor / crucible / infusion / machine 等. */
        public String type;
        public String name;
        public List<List<StackRef>> inputs = new ArrayList<>();
        public List<StackRef> outputs = new ArrayList<>();
        /** 附带信息(耗能/tick/chance/流体量等,字符串化). */
        public Map<String, String> extras = new LinkedHashMap<>();
    }

    public static final class Stats {
        public int craftingTotal;
        /** 无法提取 ingredients/output 的配方数(自定义 IRecipe 实现). */
        public int craftingUnknown;
        public int furnaceTotal;
        public int machineTotal;
        /** 适配器执行结果:modid → ok / skipped(mod 未装) / error:<摘要>. */
        public Map<String, String> adapters = new LinkedHashMap<>();
    }

    public void save(File file) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dir);
        }
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        }
    }

    public static HarvestSnapshot load(File file) throws IOException {
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, HarvestSnapshot.class);
        }
    }
}
