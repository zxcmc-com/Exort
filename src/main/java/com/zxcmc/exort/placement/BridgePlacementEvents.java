package com.zxcmc.exort.placement;

import com.zxcmc.exort.carrier.Carriers;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Dispatches the standard Paper placement contract for manual carrier placement. */
public final class BridgePlacementEvents {
  private static final ThreadLocal<Integer> DISPATCH_DEPTH = ThreadLocal.withInitial(() -> 0);

  private BridgePlacementEvents() {}

  public static boolean isDispatching() {
    return DISPATCH_DEPTH.get() > 0;
  }

  public static Approval authorize(
      Player player,
      EquipmentSlot hand,
      ItemStack item,
      Block target,
      Block placedAgainst,
      Material carrier) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(hand, "hand");
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(placedAgainst, "placedAgainst");
    Objects.requireNonNull(carrier, "carrier");
    var intendedData = carrier.createBlockData();
    if (target.getY() < target.getWorld().getMinHeight()
        || target.getY() >= target.getWorld().getMaxHeight()
        || !target
            .getWorld()
            .getWorldBorder()
            .isInside(target.getLocation().add(0.5D, 0.5D, 0.5D))) {
      return null;
    }
    BlockCanBuildEvent canBuild =
        new BlockCanBuildEvent(target, player, intendedData, target.canPlace(intendedData), hand);
    Bukkit.getPluginManager().callEvent(canBuild);
    if (!canBuild.isBuildable()) return null;

    BlockState replaced = target.getState();
    Carriers.applyCarrier(target, carrier);
    BlockPlaceEvent place =
        new BlockPlaceEvent(target, replaced, placedAgainst, item, player, true, hand);
    enterDispatch();
    try {
      Bukkit.getPluginManager().callEvent(place);
    } finally {
      exitDispatch();
    }
    if (place.isCancelled() || !place.canBuild()) {
      replaced.update(true, false);
      return null;
    }
    return new Approval(replaced);
  }

  private static void enterDispatch() {
    DISPATCH_DEPTH.set(DISPATCH_DEPTH.get() + 1);
  }

  private static void exitDispatch() {
    int next = DISPATCH_DEPTH.get() - 1;
    if (next <= 0) {
      DISPATCH_DEPTH.remove();
    } else {
      DISPATCH_DEPTH.set(next);
    }
  }

  public record Approval(BlockState replacedState) {
    public Approval {
      Objects.requireNonNull(replacedState, "replacedState");
    }

    public void rollback() {
      replacedState.update(true, false);
    }
  }
}
