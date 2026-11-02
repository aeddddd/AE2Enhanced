package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CentralRecoveryPersistenceTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @org.junit.jupiter.api.BeforeEach
    void startScope() {
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.startServer();
    }

    @org.junit.jupiter.api.AfterEach
    void stopScope() {
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.stopServer();
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.closeServer();
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.releaseServer();
    }

    @Test
    void committedRecordRoundTripPreservesIdentityAndLargeCounts() {
        TargetBinding binding = new TargetBinding(BlockPos.ORIGIN, 0, "minecraft:furnace");
        TargetSession session = new TargetSession(binding, mock(DualityCentralInterface.class));
        session.beginPush(null);
        session.preCommitExpectedOutputs(new appeng.api.storage.data.IAEItemStack[]{
                AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND, 2))});
        session.commitPush(session.getExpectedOutputs(),
                Collections.singletonList(new ItemStack(Items.STICK, 1000)),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 250)), 10L);
        PhysicalSessionRecord original = PhysicalSessionRecord.of(session);
        NBTTagCompound persisted = original.writeToNBT();
        persisted.setString("FutureRecoveryMetadata", "preserve");

        PhysicalSessionRecord restored = PhysicalSessionRecord.readFromNBT(persisted);

        assertThat(restored.id).isEqualTo(original.id);
        assertThat(restored.hasPreservedContent()).isTrue();
        assertThat(restored.writeToNBT().getString("FutureRecoveryMetadata")).isEqualTo("preserve");
        assertThat(restored.binding).isEqualTo(binding);
        assertThat(restored.inputs.get(0).getCount()).isEqualTo(1000);
        assertThat(restored.inputFluids.get(0).amount).isEqualTo(250);
        assertThat(restored.expectedOutputs).hasSize(1);
    }

    @Test
    void committedSessionIsPersistedAndDeletedOnlyAfterCompletion() {
        DualityCentralInterface owner = mock(DualityCentralInterface.class);
        TargetSession session = new TargetSession(new TargetBinding(BlockPos.ORIGIN, 0, "minecraft:furnace"), owner);
        UUID id;
        session.beginPush(null);
        session.commitPush(null, null, null, 10L);
        id = session.getSessionId();
        PhysicalSessionRecord record = PhysicalSessionRecord.of(session);
        NBTTagCompound persisted = record.writeToNBT();

        assertThat(id).isNotNull();
        assertThat(persisted.getUniqueId("id")).isEqualTo(id);
        session.reset();

        assertThat(PhysicalSessionRecord.readFromNBT(persisted).id).isEqualTo(id);
    }

    @Test
    void outboxKeepsOnlyActualRemainderAndMarksEveryChange() {
        UUID id = UUID.randomUUID();
        AtomicInteger dirty = new AtomicInteger();
        CentralRecoveryOutbox outbox = new CentralRecoveryOutbox(dirty::incrementAndGet);
        outbox.put(id, Collections.singletonList(new ItemStack(Items.DIAMOND, 10)),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000)));
        outbox.put(id, Collections.singletonList(new ItemStack(Items.DIAMOND, 3)),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 400)));
        outbox.get(id).countAttempt(dirty::incrementAndGet);

        NBTTagCompound nbt = new NBTTagCompound();
        outbox.writeToNBT(nbt);
        CentralRecoveryOutbox restored = new CentralRecoveryOutbox(dirty::incrementAndGet);
        restored.readFromNBT(nbt);
        CentralRecoveryOutbox.Entry entry = restored.get(id);
        assertThat(entry.items.get(0).getCount()).isEqualTo(3);
        assertThat(entry.fluids.get(0).amount).isEqualTo(400);
        assertThat(entry.attempts).isEqualTo(1);
        assertThat(restored.remove(id)).isTrue();
        assertThat(restored.remove(id)).isFalse();
        assertThat(dirty.get()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void unknownOutboxEntriesRemainVerbatimAndInvisible() {
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setUniqueId("id", UUID.randomUUID());
        unknown.setInteger("attempts", 2);
        NBTTagList items = new NBTTagList();
        NBTTagCompound item = new NBTTagCompound();
        item.setString("id", "missing_mod:item");
        item.setInteger("Count", 7);
        items.appendTag(item);
        unknown.setTag("items", items);
        unknown.setTag("fluids", new NBTTagList());
        NBTTagList list = new NBTTagList();
        list.appendTag(unknown);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("centralRecoveryOutbox", list);

        CentralRecoveryOutbox restored = new CentralRecoveryOutbox(() -> { });
        restored.readFromNBT(nbt);
        NBTTagCompound rewritten = new NBTTagCompound();
        restored.writeToNBT(rewritten);

        assertThat(restored.entries()).isEmpty();
        assertThat(rewritten.getTagList("centralRecoveryOutbox", 10).getCompoundTagAt(0))
                .isEqualTo(unknown);
    }
}
