package com.github.aeddddd.ae2enhanced.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 计划诊断快照的传输模型，不暴露规划器或网络实现。 */
public class PlanSnapshotObservation {

    public int formatVersion = 1;
    public String timestamp;
    public StackRef root;
    public List<StackRef> stock = new ArrayList<>();
    public List<PatternEntry> patterns = new ArrayList<>();

    public static class StackRef {
        public String id;
        public int meta;
        public long count;
        public String nbt;
    }

    public static class PatternEntry {
        public List<StackRef> inputs = new ArrayList<>();
        public List<StackRef> outputs = new ArrayList<>();
        public boolean craftable;
        public boolean canSubstitute;
        public int priority;
        public List<StackRef> rawSlots;
        public Map<Integer, List<StackRef>> substitutes;
        public List<StackRef> returns;
    }
}
