package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 服务端奇点处理仓某一时刻的只读状态。 */
public final class ChamberSnapshot {
    private final int dimension;
    private final BlockPos pos;
    private final long energy;
    private final long maxEnergy;
    private final long parallelChannels;
    private final long usedChannels;
    private final boolean paused;
    private final List<CountedStackView> inputs;
    private final List<CountedStackView> bufferedOutputs;
    private final List<ChamberJobView> jobs;

    public ChamberSnapshot(int dimension, BlockPos pos, long energy, long maxEnergy,
            long parallelChannels, long usedChannels, boolean paused,
            List<CountedStackView> inputs, List<CountedStackView> bufferedOutputs, List<ChamberJobView> jobs) {
        this.dimension = dimension;
        this.pos = pos;
        this.energy = Math.max(0L, energy);
        this.maxEnergy = Math.max(0L, maxEnergy);
        this.parallelChannels = Math.max(0L, parallelChannels);
        this.usedChannels = Math.max(0L, usedChannels);
        this.paused = paused;
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.bufferedOutputs = Collections.unmodifiableList(new ArrayList<>(bufferedOutputs));
        this.jobs = Collections.unmodifiableList(new ArrayList<>(jobs));
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    public long getEnergy() {
        return energy;
    }

    public long getMaxEnergy() {
        return maxEnergy;
    }

    public long getParallelChannels() {
        return parallelChannels;
    }

    public long getUsedChannels() {
        return usedChannels;
    }

    public int getActiveJobCount() {
        return jobs.size();
    }

    public boolean isPaused() {
        return paused;
    }

    public List<CountedStackView> getInputs() {
        return inputs;
    }

    public List<CountedStackView> getBufferedOutputs() {
        return bufferedOutputs;
    }

    public List<ChamberJobView> getJobs() {
        return jobs;
    }
}
