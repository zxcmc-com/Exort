package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.junit.jupiter.api.Test;

class ChunkLoaderAuditListenerTest {
  @Test
  void entityRemoveIgnoresNonDestructiveLifecycleCauses() {
    for (EntityRemoveEvent.Cause cause :
        List.of(
            EntityRemoveEvent.Cause.PICKUP,
            EntityRemoveEvent.Cause.UNLOAD,
            EntityRemoveEvent.Cause.MERGE,
            EntityRemoveEvent.Cause.PLAYER_QUIT,
            EntityRemoveEvent.Cause.DROP,
            EntityRemoveEvent.Cause.TRANSFORMATION)) {
      assertFalse(ChunkLoaderAuditListener.shouldAuditEntityRemove(cause), cause.name());
    }
  }

  @Test
  void entityRemoveKeepsPluginAndDiscardAsLost() {
    assertTrue(ChunkLoaderAuditListener.shouldAuditEntityRemove(EntityRemoveEvent.Cause.PLUGIN));
    assertTrue(ChunkLoaderAuditListener.shouldAuditEntityRemove(EntityRemoveEvent.Cause.DISCARD));
    assertEquals(
        ChunkLoaderRegistryStatus.LOST,
        ChunkLoaderAuditListener.registryStatusForEntityRemove(
            EntityRemoveEvent.Cause.PLUGIN, null));
    assertEquals(
        ChunkLoaderRegistryStatus.LOST,
        ChunkLoaderAuditListener.registryStatusForEntityRemove(
            EntityRemoveEvent.Cause.DISCARD, null));
  }

  @Test
  void entityRemovePhysicalDamageCausesAreRemoved() {
    for (String cause :
        List.of(
            "minecraft:cactus",
            "minecraft:explosion",
            "minecraft:player_explosion",
            "minecraft:generic_kill",
            "minecraft:in_fire",
            "minecraft:lava",
            "minecraft:hot_floor",
            "minecraft:campfire",
            "minecraft:out_of_world",
            "minecraft:lightning_bolt")) {
      assertEquals(
          ChunkLoaderRegistryStatus.REMOVED,
          ChunkLoaderAuditListener.registryStatusForEntityRemove(
              EntityRemoveEvent.Cause.DEATH, cause),
          cause);
    }
  }

  @Test
  void entityRemoveVanillaRemoveCausesAreRemovedWithoutDamageCause() {
    for (EntityRemoveEvent.Cause cause :
        List.of(
            EntityRemoveEvent.Cause.DEATH,
            EntityRemoveEvent.Cause.DESPAWN,
            EntityRemoveEvent.Cause.EXPLODE,
            EntityRemoveEvent.Cause.OUT_OF_WORLD)) {
      assertEquals(
          ChunkLoaderRegistryStatus.REMOVED,
          ChunkLoaderAuditListener.registryStatusForEntityRemove(cause, null),
          cause.name());
    }
  }

  @Test
  void deathLossesSeparateVanishingFromUnknownNoDropLoss() {
    UUID assigned = UUID.fromString("00000000-0000-0000-0000-000000000011");
    ChunkLoaderItemSnapshot.Key assignedKey =
        new ChunkLoaderItemSnapshot.Key(assigned, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot.Key unassignedKey =
        new ChunkLoaderItemSnapshot.Key(null, ChunkLoaderType.PERSONAL_CHUNK_LOADER);
    ChunkLoaderItemSnapshot before =
        ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(assignedKey, 2, unassignedKey, 1));
    ChunkLoaderItemSnapshot dropped = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(assignedKey, 1));
    ChunkLoaderItemSnapshot kept = ChunkLoaderItemSnapshot.empty();
    ChunkLoaderItemSnapshot vanishing =
        ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(assignedKey, 1));

    List<ChunkLoaderAuditListener.DeathLoss> losses =
        ChunkLoaderAuditListener.deathLosses(before, dropped, kept, vanishing);
    ChunkLoaderAuditListener.DeathLoss vanishingLoss =
        losses.stream()
            .filter(loss -> "curse_of_vanishing".equals(loss.reason()))
            .findFirst()
            .orElseThrow();
    ChunkLoaderAuditListener.DeathLoss unknownLoss =
        losses.stream()
            .filter(loss -> "death_no_drop".equals(loss.reason()))
            .findFirst()
            .orElseThrow();

    assertEquals(2, losses.size());
    assertEquals(assignedKey, vanishingLoss.key());
    assertEquals(1, vanishingLoss.amount());
    assertEquals(ChunkLoaderRegistryStatus.REMOVED, vanishingLoss.status());
    assertEquals(unassignedKey, unknownLoss.key());
    assertEquals(1, unknownLoss.amount());
    assertEquals(ChunkLoaderRegistryStatus.LOST, unknownLoss.status());
  }

  @Test
  void creativeInventoryVerdictIgnoresCursorMoveAndReturn() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000021");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot baseline = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 60));
    ChunkLoaderItemSnapshot current = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 60));

    List<ChunkLoaderAuditListener.CreativeInventoryVerdict> verdicts =
        ChunkLoaderAuditListener.creativeInventoryVerdicts(baseline, Map.of(), current);

    assertTrue(verdicts.isEmpty());
  }

  @Test
  void creativeInventoryVerdictAccountsForDropLedger() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000022");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot baseline = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 60));

    List<ChunkLoaderAuditListener.CreativeInventoryVerdict> verdicts =
        ChunkLoaderAuditListener.creativeInventoryVerdicts(
            baseline, Map.of(key, -60), ChunkLoaderItemSnapshot.empty());

    assertTrue(verdicts.isEmpty());
  }

  @Test
  void creativeInventoryVerdictAccountsForDropAndPickupLedger() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000023");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot baseline = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 60));
    ChunkLoaderItemSnapshot current = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 60));

    List<ChunkLoaderAuditListener.CreativeInventoryVerdict> verdicts =
        ChunkLoaderAuditListener.creativeInventoryVerdicts(baseline, Map.of(key, 0), current);

    assertTrue(verdicts.isEmpty());
  }

  @Test
  void creativeInventoryVerdictDestroysOnlyUnaccountedLoss() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000024");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot baseline = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 60));

    ChunkLoaderAuditListener.CreativeInventoryVerdict verdict =
        singleVerdict(
            ChunkLoaderAuditListener.creativeInventoryVerdicts(
                baseline, Map.of(), ChunkLoaderItemSnapshot.empty()));

    assertEquals(ChunkLoaderAuditListener.CreativeInventoryVerdictAction.DESTROY, verdict.action());
    assertEquals(key, verdict.key());
    assertEquals(60, verdict.amount());
  }

  @Test
  void creativeInventoryVerdictIssuesUnassignedIncrease() {
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(null, ChunkLoaderType.PERSONAL_CHUNK_LOADER);
    ChunkLoaderItemSnapshot current = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 1));

    ChunkLoaderAuditListener.CreativeInventoryVerdict verdict =
        singleVerdict(
            ChunkLoaderAuditListener.creativeInventoryVerdicts(
                ChunkLoaderItemSnapshot.empty(), Map.of(), current));

    assertEquals(ChunkLoaderAuditListener.CreativeInventoryVerdictAction.ISSUE, verdict.action());
    assertEquals(key, verdict.key());
    assertEquals(1, verdict.amount());
  }

  @Test
  void creativeInventoryVerdictDuplicatesExistingUuidIncrease() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000025");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.DORMANT_CHUNK_LOADER);
    ChunkLoaderItemSnapshot baseline = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 1));
    ChunkLoaderItemSnapshot current = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 2));

    ChunkLoaderAuditListener.CreativeInventoryVerdict verdict =
        singleVerdict(
            ChunkLoaderAuditListener.creativeInventoryVerdicts(baseline, Map.of(), current));

    assertEquals(
        ChunkLoaderAuditListener.CreativeInventoryVerdictAction.DUPLICATE, verdict.action());
    assertEquals(key, verdict.key());
    assertEquals(1, verdict.amount());
  }

  @Test
  void creativeInventoryVerdictDoesNotCallFreshUuidADuplicate() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000026");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot current = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 1));

    ChunkLoaderAuditListener.CreativeInventoryVerdict verdict =
        singleVerdict(
            ChunkLoaderAuditListener.creativeInventoryVerdicts(
                ChunkLoaderItemSnapshot.empty(), Map.of(), current));

    assertEquals(ChunkLoaderAuditListener.CreativeInventoryVerdictAction.ISSUE, verdict.action());
    assertEquals(key, verdict.key());
    assertEquals(1, verdict.amount());
  }

  @Test
  void creativeInventoryVerdictDuplicatesActiveRegistryUuidIncrease() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000028");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot current = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 1));

    ChunkLoaderAuditListener.CreativeInventoryVerdict verdict =
        singleVerdict(
            ChunkLoaderAuditListener.creativeInventoryVerdicts(
                ChunkLoaderItemSnapshot.empty(), Map.of(), current, id::equals));

    assertEquals(
        ChunkLoaderAuditListener.CreativeInventoryVerdictAction.DUPLICATE, verdict.action());
    assertEquals(key, verdict.key());
    assertEquals(1, verdict.amount());
  }

  @Test
  void creativeInventoryVerdictAccountsForCreativePickIssueLedger() {
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(null, ChunkLoaderType.CHUNK_LOADER);
    ChunkLoaderItemSnapshot current = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 1));

    List<ChunkLoaderAuditListener.CreativeInventoryVerdict> verdicts =
        ChunkLoaderAuditListener.creativeInventoryVerdicts(
            ChunkLoaderItemSnapshot.empty(), Map.of(key, 1), current);

    assertTrue(verdicts.isEmpty());
  }

  @Test
  void creativeInventoryVerdictAccountsForCreativePickReplacementLedger() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000027");
    ChunkLoaderItemSnapshot.Key key =
        new ChunkLoaderItemSnapshot.Key(id, ChunkLoaderType.PERSONAL_CHUNK_LOADER);
    ChunkLoaderItemSnapshot baseline = ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, 1));

    List<ChunkLoaderAuditListener.CreativeInventoryVerdict> verdicts =
        ChunkLoaderAuditListener.creativeInventoryVerdicts(
            baseline, Map.of(key, -1), ChunkLoaderItemSnapshot.empty());

    assertTrue(verdicts.isEmpty());
  }

  private static ChunkLoaderAuditListener.CreativeInventoryVerdict singleVerdict(
      List<ChunkLoaderAuditListener.CreativeInventoryVerdict> verdicts) {
    assertEquals(1, verdicts.size());
    return verdicts.getFirst();
  }
}
