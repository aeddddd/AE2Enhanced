package com.github.aeddddd.ae2enhanced.storage.mana;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IItemList;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Mana 通道的 IItemList 实现.
 * 由于 Mana 无子类型,内部仅需维护单一堆叠实例.
 */
public class ManaList implements IItemList<IAEManaStack> {

    private IAEManaStack stack = new AEManaStack();

    @Override
    public void addStorage(IAEManaStack s) {
        if (s != null && s.isMeaningful()) {
            this.stack.incStackSize(s.getStackSize());
        }
    }

    @Override
    public void addCrafting(IAEManaStack s) {
        if (s != null && s.isMeaningful()) {
            this.stack.incCountRequestable(s.getCountRequestable());
        }
    }

    @Override
    public void addRequestable(IAEManaStack s) {
        addCrafting(s);
    }

    @Override
    public IAEManaStack getFirstItem() {
        return this.stack.isMeaningful() ? this.stack.copy() : null;
    }

    @Override
    public int size() {
        return this.stack.isMeaningful() ? 1 : 0;
    }

    @Override
    public Iterator<IAEManaStack> iterator() {
        return this.stack.isMeaningful()
                ? Collections.singletonList(this.stack.copy()).iterator()
                : Collections.emptyIterator();
    }

    @Override
    public void add(IAEManaStack s) {
        addStorage(s);
    }

    @Override
    public void resetStatus() {
        this.stack.setCountRequestable(0);
        this.stack.setCraftable(false);
    }

    @Override
    public IAEManaStack findPrecise(IAEManaStack item) {
        return this.stack.isMeaningful() ? this.stack.copy() : null;
    }

    @Override
    public Collection<IAEManaStack> findFuzzy(IAEManaStack input, FuzzyMode mode) {
        IAEManaStack first = getFirstItem();
        return first != null ? Collections.singletonList(first) : Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
        return !this.stack.isMeaningful();
    }
}
