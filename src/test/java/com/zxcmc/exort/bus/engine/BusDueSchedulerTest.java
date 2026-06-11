package com.zxcmc.exort.bus.engine;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.bus.BusType;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class BusDueSchedulerTest {
  @Test
  void pollsOnlyBusesDueAtCurrentTick() {
    BusDueScheduler scheduler = new BusDueScheduler();
    BusState state = state(1);
    state.setNextTick(10L);

    scheduler.sync(List.of(state), 1L, 0L);

    assertNull(scheduler.pollDue(9L));
    assertSame(state, scheduler.pollDue(10L));
    assertNull(scheduler.pollDue(10L));
  }

  @Test
  void skipsStaleBucketEntriesAfterReschedule() {
    BusDueScheduler scheduler = new BusDueScheduler();
    BusState state = state(1);

    scheduler.schedule(state, 5L);
    scheduler.schedule(state, 6L);

    assertNull(scheduler.pollDue(5L));
    assertSame(state, scheduler.pollDue(6L));
  }

  @Test
  void keepsListOrderForBusesDueAtSameTick() {
    BusDueScheduler scheduler = new BusDueScheduler();
    BusState first = state(1);
    BusState second = state(2);

    scheduler.sync(List.of(first, second), 1L, 0L);

    assertSame(first, scheduler.pollDue(0L));
    assertSame(second, scheduler.pollDue(0L));
    assertNull(scheduler.pollDue(0L));
  }

  @Test
  void skipsRemovedBusAfterRegistrySync() {
    BusDueScheduler scheduler = new BusDueScheduler();
    BusState removed = state(1);
    BusState live = state(2);

    scheduler.sync(List.of(removed, live), 1L, 0L);
    scheduler.sync(List.of(live), 2L, 0L);

    assertSame(live, scheduler.pollDue(0L));
    assertNull(scheduler.pollDue(0L));
  }

  @Test
  void schedulesNewBusNoEarlierThanCurrentTick() {
    BusDueScheduler scheduler = new BusDueScheduler();
    BusState state = state(1);
    state.setNextTick(5L);

    scheduler.sync(List.of(state), 1L, 10L);

    assertSame(state, scheduler.pollDue(10L));
    assertNull(scheduler.pollDue(10L));
  }

  private BusState state(int x) {
    return new BusState(
        new BusPos(UUID.fromString("00000000-0000-0000-0000-000000000001"), x, 64, 0),
        BusType.IMPORT,
        BlockFace.NORTH,
        BusMode.ALL);
  }
}
