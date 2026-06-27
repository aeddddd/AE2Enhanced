package com.github.aeddddd.ae2enhanced.storage.energy;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * 能量通道的 IItemList 实现.
 * 由于 RF 能量无子类型,内部仅需维护单一堆叠实例.
 */
public class EnergyList implements IItemList<IAEEnergyStack> {

    private IAEEnergyStack stack = new AEEnergyStack();

    @Override
    public void addStorage(IAEEnergyStack s) {
        if (s != null && s.isMeaningful()) {
            this.stack.incStackSize(s.getStackSize());
        }
    }

    @Override
    public void addCrafting(IAEEnergyStack s) {
        if (s != null && s.isMeaningful()) {
            this.stack.incCountRequestable(s.getCountRequestable());
        }
    }

    @Override
    public void addRequestable(IAEEnergyStack s) {
        addCrafting(s);
    }

    @Override
    public IAEEnergyStack getFirstItem() {
        return this.stack.isMeaningful() ? this.stack.copy() : null;
    }

    @Override
    public int size() {
        return this.stack.isMeaningful() ? 1 : 0;
    }

    @Override
    public Iterator<IAEEnergyStack> iterator() {
        return this.stack.isMeaningful()
                ? Collections.singletonList(this.stack.copy()).iterator()
                : Collections.emptyIterator();
    }

    @Override
    public void add(IAEEnergyStack s) {
        addStorage(s);
    }

    @Override
    public void resetStatus() {
        this.stack.setCountRequestable(0);
        this.stack.setCraftable(false);
    }

    @Override
    public IAEEnergyStack findPrecise(IAEEnergyStack item) {
        return this.stack.isMeaningful() ? this.stack.copy() : null;
    }

    @Override
    public Collection<IAEEnergyStack> findFuzzy(IAEEnergyStack input, FuzzyMode mode) {
        IAEEnergyStack first = getFirstItem();
        return first != null ? Collections.singletonList(first) : Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
        return !this.stack.isMeaningful();
    }
}
