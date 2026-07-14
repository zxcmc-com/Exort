package com.zxcmc.exort.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BridgePlacementEventsTest {
  @Test
  void heightAndWorldBorderRejectBeforeEventsOrMutation() {
    PlacementContext belowWorld = new PlacementContext(-65, -64, 320, true, true);
    AtomicInteger belowEvents = new AtomicInteger();

    assertNull(belowWorld.authorize(EquipmentSlot.HAND, event -> belowEvents.incrementAndGet()));
    assertEquals(0, belowEvents.get());
    assertEquals(Material.WATER, belowWorld.currentType());

    PlacementContext outsideBorder = new PlacementContext(64, -64, 320, false, true);
    AtomicInteger borderEvents = new AtomicInteger();

    assertNull(
        outsideBorder.authorize(EquipmentSlot.HAND, event -> borderEvents.incrementAndGet()));
    assertEquals(0, borderEvents.get());
    assertEquals(Material.WATER, outsideBorder.currentType());
  }

  @Test
  void blockCanBuildDenialPreventsCarrierMutationAndPlaceEvent() {
    PlacementContext context = new PlacementContext(64, -64, 320, true, true);
    List<Class<?>> events = new ArrayList<>();

    BridgePlacementEvents.Approval approval =
        context.authorize(
            EquipmentSlot.HAND,
            event -> {
              events.add(event.getClass());
              if (event instanceof BlockCanBuildEvent canBuild) {
                canBuild.setBuildable(false);
              }
            });

    assertNull(approval);
    assertEquals(List.of(BlockCanBuildEvent.class), events);
    assertEquals(Material.WATER, context.currentType());
    assertEquals(0, context.restoreCalls());
  }

  @Test
  void listenerCanExplicitlyAllowInitiallyUnplaceableCarrier() {
    PlacementContext context = new PlacementContext(64, -64, 320, true, false);
    List<Boolean> initialBuildable = new ArrayList<>();

    BridgePlacementEvents.Approval approval =
        context.authorize(
            EquipmentSlot.HAND,
            event -> {
              if (event instanceof BlockCanBuildEvent canBuild) {
                initialBuildable.add(canBuild.isBuildable());
                canBuild.setBuildable(true);
              }
            });

    assertEquals(List.of(false), initialBuildable);
    assertNotNull(approval);
    assertEquals(Material.BARRIER, context.currentType());
  }

  @Test
  void placeEventReceivesExactMainAndOffHandContext() {
    for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
      PlacementContext context = new PlacementContext(64, -64, 320, true, true);
      List<Event> events = new ArrayList<>();

      BridgePlacementEvents.Approval approval = context.authorize(hand, events::add);

      assertNotNull(approval);
      assertEquals(2, events.size());
      BlockPlaceEvent place = (BlockPlaceEvent) events.get(1);
      assertEquals(hand, place.getHand());
      assertSame(context.item(), place.getItemInHand());
      assertSame(context.target(), place.getBlockPlaced());
      assertSame(context.placedAgainst(), place.getBlockAgainst());
      assertSame(context.replacedState(), place.getBlockReplacedState());
      assertSame(context.player(), place.getPlayer());
    }
  }

  @Test
  void cancellationAndBuildDenialRestoreOriginalBlock() {
    assertPlaceDenialRestores(event -> event.setCancelled(true));
    assertPlaceDenialRestores(event -> event.setBuild(false));
  }

  @Test
  void approvalRollbackRestoresOriginalBlockAfterLaterFailure() {
    PlacementContext context = new PlacementContext(64, -64, 320, true, true);
    BridgePlacementEvents.Approval approval = context.authorize(EquipmentSlot.HAND, event -> {});

    assertNotNull(approval);
    assertEquals(Material.BARRIER, context.currentType());
    approval.rollback();

    assertEquals(Material.WATER, context.currentType());
    assertEquals(1, context.restoreCalls());
  }

  @Test
  void exceptionalPlaceListenerRestoresBlockAndClearsDispatchGuard() {
    PlacementContext context = new PlacementContext(64, -64, 320, true, true);
    IllegalStateException failure =
        new IllegalStateException("injected placement listener failure");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                context.authorize(
                    EquipmentSlot.HAND,
                    event -> {
                      if (event instanceof BlockPlaceEvent) throw failure;
                    }));

    assertSame(failure, thrown);
    assertEquals(Material.WATER, context.currentType());
    assertEquals(1, context.restoreCalls());
    assertFalse(BridgePlacementEvents.isDispatching());
  }

  private static void assertPlaceDenialRestores(
      java.util.function.Consumer<BlockPlaceEvent> denial) {
    PlacementContext context = new PlacementContext(64, -64, 320, true, true);

    BridgePlacementEvents.Approval approval =
        context.authorize(
            EquipmentSlot.HAND,
            event -> {
              if (event instanceof BlockPlaceEvent place) denial.accept(place);
            });

    assertNull(approval);
    assertEquals(Material.WATER, context.currentType());
    assertEquals(1, context.restoreCalls());
    assertFalse(BridgePlacementEvents.isDispatching());
  }

  private static final class PlacementContext {
    private final AtomicReference<Material> currentType = new AtomicReference<>(Material.WATER);
    private final AtomicInteger restoreCalls = new AtomicInteger();
    private final ItemStack item = new PlacementItemStack();
    private final Player player = proxy(Player.class);
    private final BlockData intendedData = blockData(Material.BARRIER);
    private final BlockState replacedState;
    private final Block target;
    private final Block placedAgainst;

    private PlacementContext(
        int y, int minHeight, int maxHeight, boolean insideBorder, boolean canPlace) {
      WorldBorder border =
          BukkitTestDoubles.proxy(
              WorldBorder.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "isInside" -> insideBorder;
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
      AtomicReference<World> worldRef = new AtomicReference<>();
      World world =
          BukkitTestDoubles.proxy(
              World.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getMinHeight" -> minHeight;
                    case "getMaxHeight" -> maxHeight;
                    case "getWorldBorder" -> border;
                    case "toString" -> "placement-world";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
      worldRef.set(world);
      AtomicReference<Block> targetRef = new AtomicReference<>();
      replacedState =
          BukkitTestDoubles.proxy(
              BlockState.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getBlock" -> targetRef.get();
                    case "getType" -> Material.WATER;
                    case "update" -> {
                      restoreCalls.incrementAndGet();
                      currentType.set(Material.WATER);
                      yield true;
                    }
                    case "toString" -> "replaced-water-state";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
      target =
          BukkitTestDoubles.proxy(
              Block.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getWorld" -> worldRef.get();
                    case "getX", "getZ" -> 0;
                    case "getY" -> y;
                    case "getLocation" -> new Location(worldRef.get(), 0, y, 0);
                    case "canPlace" -> canPlace;
                    case "getState" -> replacedState;
                    case "getType" -> currentType.get();
                    case "setType" -> {
                      currentType.set((Material) args[0]);
                      yield null;
                    }
                    case "toString" -> "placement-target";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
      targetRef.set(target);
      placedAgainst = proxy(Block.class);
    }

    private BridgePlacementEvents.Approval authorize(
        EquipmentSlot hand, BridgePlacementEvents.EventDispatcher dispatcher) {
      return BridgePlacementEvents.authorize(
          player, hand, item, target, placedAgainst, Material.BARRIER, intendedData, dispatcher);
    }

    private Material currentType() {
      return currentType.get();
    }

    private int restoreCalls() {
      return restoreCalls.get();
    }

    private ItemStack item() {
      return item;
    }

    private Player player() {
      return player;
    }

    private Block target() {
      return target;
    }

    private Block placedAgainst() {
      return placedAgainst;
    }

    private BlockState replacedState() {
      return replacedState;
    }
  }

  private static BlockData blockData(Material material) {
    return BukkitTestDoubles.proxy(
        BlockData.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getMaterial" -> material;
              case "clone" -> proxy;
              case "toString", "getAsString" -> material.name().toLowerCase();
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals", "matches" -> proxy == args[0];
              default -> BukkitTestDoubles.defaultValue(method.getReturnType());
            });
  }

  private static <T> T proxy(Class<T> type) {
    return BukkitTestDoubles.proxy(
        type,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "toString" -> type.getSimpleName() + "-test-double";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> BukkitTestDoubles.defaultValue(method.getReturnType());
            });
  }

  private static final class PlacementItemStack extends ItemStack {
    @Override
    public Material getType() {
      return Material.PAPER;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  }
}
