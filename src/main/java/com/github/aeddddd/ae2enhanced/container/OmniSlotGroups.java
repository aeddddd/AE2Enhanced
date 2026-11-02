package com.github.aeddddd.ae2enhanced.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.github.aeddddd.ae2enhanced.container.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.container.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.container.slot.SlotHighCapacity;
import com.github.aeddddd.ae2enhanced.container.slot.SlotOmniUpgrade;

import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotCraftingTerm;
import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import net.minecraft.inventory.Slot;

final class OmniSlotGroups {
    final List<Slot> player;
    final List<Slot> viewCells;
    final List<Slot> craftingMatrix;
    final List<Slot> craftingOutput;
    final List<Slot> storage;
    final List<Slot> upgrades;
    final List<Slot> pattern;
    final List<Slot> patternFeed;

    private OmniSlotGroups(List<Slot> player, List<Slot> viewCells, List<Slot> craftingMatrix,
            List<Slot> craftingOutput, List<Slot> storage, List<Slot> upgrades,
            List<Slot> pattern, List<Slot> patternFeed) {
        this.player = immutable(player);
        this.viewCells = immutable(viewCells);
        this.craftingMatrix = immutable(craftingMatrix);
        this.craftingOutput = immutable(craftingOutput);
        this.storage = immutable(storage);
        this.upgrades = immutable(upgrades);
        this.pattern = immutable(pattern);
        this.patternFeed = immutable(patternFeed);
    }

    static OmniSlotGroups classify(List<Slot> slots, RCSlotFakeCraftingMatrix[][] patternInputs,
            RCSlotPatternOutputs[][] patternOutputs, Slot patternCraft, Slot patternIn,
            Slot patternOut, Slot[] viewCellSlots) {
        List<Slot> player = new ArrayList<Slot>();
        List<Slot> viewCells = new ArrayList<Slot>();
        List<Slot> craftingMatrix = new ArrayList<Slot>();
        List<Slot> craftingOutput = new ArrayList<Slot>();
        List<Slot> storage = new ArrayList<Slot>();
        List<Slot> upgrades = new ArrayList<Slot>();
        List<Slot> pattern = new ArrayList<Slot>();
        List<Slot> patternFeed = new ArrayList<Slot>();
        Set<Slot> patternArea = Collections.newSetFromMap(new IdentityHashMap<Slot, Boolean>());
        for (int group = 0; group < patternInputs.length; group++) {
            for (RCSlotFakeCraftingMatrix slot : patternInputs[group]) {
                pattern.add(slot);
                patternArea.add(slot);
            }
            for (RCSlotPatternOutputs slot : patternOutputs[group]) {
                pattern.add(slot);
                patternArea.add(slot);
            }
        }
        if (patternCraft != null) {
            pattern.add(patternCraft);
            patternArea.add(patternCraft);
        }
        if (patternIn != null) {
            patternFeed.add(patternIn);
            patternArea.add(patternIn);
        }
        if (patternOut != null) {
            patternFeed.add(patternOut);
            patternArea.add(patternOut);
        }
        for (Slot slot : slots) {
            if (patternArea.contains(slot)) {
                continue;
            }
            if (slot instanceof SlotPlayerInv || slot instanceof SlotPlayerHotBar || slot instanceof SlotDisabled) {
                player.add(slot);
            } else if (slot instanceof SlotCraftingMatrix) {
                craftingMatrix.add(slot);
            } else if (slot instanceof SlotCraftingTerm) {
                craftingOutput.add(slot);
            } else if (slot instanceof SlotHighCapacity) {
                storage.add(slot);
            } else if (slot instanceof SlotOmniUpgrade) {
                upgrades.add(slot);
            }
        }
        for (Slot slot : viewCellSlots) {
            if (slot != null) {
                viewCells.add(slot);
            }
        }
        return new OmniSlotGroups(player, viewCells, craftingMatrix, craftingOutput,
                storage, upgrades, pattern, patternFeed);
    }

    private static List<Slot> immutable(List<Slot> slots) {
        return Collections.unmodifiableList(new ArrayList<Slot>(slots));
    }
}
