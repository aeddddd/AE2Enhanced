package com.github.aeddddd.ae2enhanced.storage.starlight;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IItemList;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Starlight 通道的 IItemList 实现.
 * 由于 Starlight 无子类型,内部仅需维护单一堆叠实例.
 */
public class StarlightList implements IItemList<IAEStarlightStack> {

    private IAEStarlightStack stack = new AEStarlightStack();

    @Override
    public void addStorage(IAEStarlightStack s) {
        if (s != null && s.isMeaningful()) {
            this.stack.incStackSize(s.getStackSize());
        }
    }

    @Override
    public void addCrafting(IAEStarlightStack s) {
        if (s != null && s.isMeaningful()) {
            this.stack.incCountRequestable(s.getCountRequestable());
        }
    }

    @Override
    public void addRequestable(IAEStarlightStack s) {
        addCrafting(s);
    }

    @Override
    public IAEStarlightStack getFirstItem() {
        return this.stack.isMeaningful() ? this.stack.copy() : null;
    }

    @Override
    public int size() {
        return this.stack.isMeaningful() ? 1 : 0;
    }

    @Override
    public Iterator<IAEStarlightStack> iterator() {
        return this.stack.isMeaningful()
                ? Collections.singletonList(this.stack.copy()).iterator()
                : Collections.emptyIterator();
    }

    @Override
    public void add(IAEStarlightStack s) {
        addStorage(s);
    }

    @Override
    public void resetStatus() {
        this.stack.setCountRequestable(0);
        this.stack.setCraftable(false);
    }

    @Override
    public IAEStarlightStack findPrecise(IAEStarlightStack item) {
        return this.stack.isMeaningful() ? this.stack.copy() : null;
    }

    @Override
    public Collection<IAEStarlightStack> findFuzzy(IAEStarlightStack input, FuzzyMode mode) {
        IAEStarlightStack first = getFirstItem();
        return first != null ? Collections.singletonList(first) : Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
        return !this.stack.isMeaningful();
    }
}
