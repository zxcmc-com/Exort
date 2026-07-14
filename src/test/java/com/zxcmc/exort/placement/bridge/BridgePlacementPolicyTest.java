package com.zxcmc.exort.placement.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.Collection;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

class BridgePlacementPolicyTest {
  @Test
  void replaceablePolicyAcceptsAirOrExplicitlyReplaceableBlocksOnly() {
    assertFalse(BridgePlacementPolicy.isReplaceable(null));
    assertTrue(BridgePlacementPolicy.isReplaceable(block(Material.AIR, false, ListEntities.NONE)));
    assertTrue(BridgePlacementPolicy.isReplaceable(block(Material.WATER, true, ListEntities.NONE)));
    assertFalse(
        BridgePlacementPolicy.isReplaceable(block(Material.STONE, false, ListEntities.NONE)));
  }

  @Test
  void livingEntityBlocksPlacementWhileNonLivingEntityDoesNot() {
    LivingEntity living = proxy(LivingEntity.class);
    Item droppedItem = proxy(Item.class);

    assertFalse(
        BridgePlacementPolicy.hasPlacementSpace(
            block(Material.AIR, true, new ListEntities(List.of(living)))));
    assertTrue(
        BridgePlacementPolicy.hasPlacementSpace(
            block(Material.AIR, true, new ListEntities(List.of(droppedItem)))));
    assertTrue(
        BridgePlacementPolicy.hasPlacementSpace(block(Material.AIR, true, ListEntities.NONE)));
  }

  @Test
  void handPolicyPreservesVanillaMainHandPrecedence() {
    assertTrue(
        BridgePlacementPolicy.shouldUseOffhand(EquipmentSlot.HAND, false, true, false, false, 0));
    assertTrue(
        BridgePlacementPolicy.shouldUseOffhand(
            EquipmentSlot.OFF_HAND, true, false, false, false, 0));
    assertFalse(
        BridgePlacementPolicy.shouldUseOffhand(
            EquipmentSlot.OFF_HAND, false, true, false, false, 20));
    assertFalse(
        BridgePlacementPolicy.shouldUseOffhand(
            EquipmentSlot.OFF_HAND, false, false, true, false, 20));
    assertFalse(
        BridgePlacementPolicy.shouldUseOffhand(
            EquipmentSlot.OFF_HAND, false, false, false, true, 10));
    assertTrue(
        BridgePlacementPolicy.shouldUseOffhand(
            EquipmentSlot.OFF_HAND, false, false, false, true, 20));
    assertTrue(
        BridgePlacementPolicy.shouldUseOffhand(
            EquipmentSlot.OFF_HAND, false, false, false, false, 10));
  }

  private static Block block(Material material, boolean replaceable, ListEntities entities) {
    World world =
        BukkitTestDoubles.proxy(
            World.class,
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getNearbyEntities" -> entities.values();
                  default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                });
    return BukkitTestDoubles.proxy(
        Block.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getType" -> material;
              case "isReplaceable" -> replaceable;
              case "getX", "getY", "getZ" -> 0;
              case "getWorld" -> world;
              default -> BukkitTestDoubles.defaultValue(method.getReturnType());
            });
  }

  private static <T> T proxy(Class<T> type) {
    return BukkitTestDoubles.proxy(
        type,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> BukkitTestDoubles.defaultValue(method.getReturnType());
            });
  }

  private record ListEntities(Collection<org.bukkit.entity.Entity> values) {
    private static final ListEntities NONE = new ListEntities(List.of());
  }
}
