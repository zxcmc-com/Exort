package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.storage.StorageTier;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PickListener implements Listener {
  private final ExortPlugin plugin;
  private final CustomItems customItems;
  private final StorageKeys keys;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final Map<UUID, RecentPick> recentPicks = new HashMap<>();

  public PickListener(
      ExortPlugin plugin,
      CustomItems customItems,
      StorageKeys keys,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier) {
    this.plugin = plugin;
    this.customItems = customItems;
    this.keys = keys;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onPickDebug(PlayerPickItemEvent event) {
    debug("event " + describePickEvent(event));
  }

  @EventHandler(ignoreCancelled = true)
  public void onPick(PlayerPickItemEvent event) {
    Block target = pickEventBlock(event);
    if (target == null) {
      debug("miss event=" + event.getClass().getSimpleName() + " target=null");
      return;
    }

    PickTarget pick = resolveTarget(target);
    if (pick == null) {
      debug("miss event=" + event.getClass().getSimpleName() + " target=" + describeBlock(target));
      return;
    }
    if (isDuplicatePick(event.getPlayer(), target, "event " + event.getClass().getSimpleName())) {
      return;
    }
    applyEventPick(event, pick);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    recentPicks.remove(event.getPlayer().getUniqueId());
  }

  private Block pickEventBlock(PlayerPickItemEvent event) {
    if (event instanceof PlayerPickBlockEvent blockEvent) {
      return blockEvent.getBlock();
    }
    return event.getPlayer().getTargetBlockExact(8, FluidCollisionMode.NEVER);
  }

  public boolean handleDirectPick(Player player, Block target, String source) {
    if (player == null || target == null) {
      return false;
    }
    PickTarget pick = resolveTarget(target);
    if (pick == null) {
      debug("direct miss source=" + source + " target=" + describeBlock(target));
      return false;
    }
    if (isDuplicatePick(player, target, "direct " + source)) {
      return true;
    }
    applyDirectPick(player, pick, source);
    return true;
  }

  public boolean isPickTargetBlock(Block block) {
    return resolveTarget(block) != null;
  }

  public String describeDebugBlock(Block block) {
    return describeBlock(block);
  }

  private boolean isDuplicatePick(Player player, Block target, String source) {
    int tick = Bukkit.getCurrentTick();
    RecentPick current = new RecentPick(BlockKey.of(target), tick);
    RecentPick previous = recentPicks.put(player.getUniqueId(), current);
    if (previous != null
        && previous.block().equals(current.block())
        && tick - previous.tick() <= 1) {
      debug("duplicate pick skipped source=" + source + " target=" + describeBlock(target));
      return true;
    }
    return false;
  }

  private PickTarget resolveTarget(Block target) {
    ItemStack desired = null;
    String type = null;
    String expectedTier = null;
    if (isTerminal(target)) {
      TerminalKind kind = TerminalMarker.kind(plugin, target);
      if (kind == TerminalKind.CRAFTING) {
        desired = customItems.craftingTerminalItem();
        type = "crafting_terminal";
      } else {
        desired = customItems.terminalItem();
        type = "terminal";
      }
    } else if (isWire(target)) {
      desired = customItems.wireItem();
      type = "wire";
    } else if (isMonitor(target)) {
      desired = customItems.monitorItem();
      type = "monitor";
    } else if (isBus(target)) {
      var data = BusMarker.get(plugin, target).orElse(null);
      if (data == null) return null;
      if (data.type() == BusType.EXPORT) {
        desired = customItems.exportBusItem();
        type = "export_bus";
      } else {
        desired = customItems.importBusItem();
        type = "import_bus";
      }
    } else if (isStorage(target)) {
      var tierOpt = readTier(target);
      if (tierOpt.isEmpty()) return null;
      StorageTier tier = tierOpt.get();
      desired = customItems.storageItem(tier, null);
      type = "storage";
      expectedTier = tier.key();
    } else if (isStorageCore(target)) {
      desired = customItems.storageCoreItem();
      type = "storage_core";
    } else {
      return null;
    }
    return new PickTarget(desired, type, expectedTier);
  }

  private void applyEventPick(PlayerPickItemEvent event, PickTarget pick) {
    Player player = event.getPlayer();
    int held = player.getInventory().getHeldItemSlot();
    int existingSlot = findExisting(player.getInventory(), pick.type(), pick.expectedTier());
    if (existingSlot >= 0) {
      if (existingSlot <= 8) {
        event.setSourceSlot(existingSlot);
        event.setTargetSlot(existingSlot);
      } else {
        int empty = findEmptyHotbar(player.getInventory(), held);
        if (empty >= 0) {
          event.setSourceSlot(existingSlot);
          event.setTargetSlot(empty);
        } else {
          event.setSourceSlot(existingSlot);
          event.setTargetSlot(held);
        }
      }
      debug(
          "handled event="
              + event.getClass().getSimpleName()
              + " result="
              + pick.type()
              + tierSuffix(pick.expectedTier())
              + " sourceSlot="
              + event.getSourceSlot()
              + " targetSlot="
              + event.getTargetSlot());
      return;
    }

    event.setCancelled(true);
    if (player.getGameMode() == GameMode.CREATIVE && pick.item() != null) {
      ItemStack give = pick.item().clone();
      give.setAmount(1);
      int empty = findEmptyHotbar(player.getInventory(), held);
      int targetSlot = empty >= 0 ? empty : held;
      player.getInventory().setItem(targetSlot, give);
      player.getInventory().setHeldItemSlot(targetSlot);
      player.updateInventory();
      debug(
          "handled creative event="
              + event.getClass().getSimpleName()
              + " result="
              + pick.type()
              + tierSuffix(pick.expectedTier())
              + " targetSlot="
              + targetSlot);
    }
  }

  private void applyDirectPick(Player player, PickTarget pick, String source) {
    int held = player.getInventory().getHeldItemSlot();
    int existingSlot = findExisting(player.getInventory(), pick.type(), pick.expectedTier());
    if (existingSlot >= 0 && existingSlot <= 8) {
      player.getInventory().setHeldItemSlot(existingSlot);
      debug(
          "handled direct source="
              + source
              + " result="
              + pick.type()
              + tierSuffix(pick.expectedTier())
              + " sourceSlot="
              + existingSlot
              + " targetSlot="
              + existingSlot);
      return;
    }

    if (player.getGameMode() != GameMode.CREATIVE || pick.item() == null) {
      debug(
          "handled direct source="
              + source
              + " result="
              + pick.type()
              + tierSuffix(pick.expectedTier())
              + " creative=false existingSlot="
              + existingSlot);
      return;
    }

    int targetSlot;
    ItemStack give;
    if (existingSlot >= 9) {
      targetSlot = chooseDirectTargetSlot(player.getInventory(), held);
      ItemStack existing = player.getInventory().getItem(existingSlot);
      give = existing != null ? existing.clone() : pick.item().clone();
    } else {
      targetSlot = chooseDirectTargetSlot(player.getInventory(), held);
      give = pick.item().clone();
    }
    give.setAmount(1);
    player.getInventory().setItem(targetSlot, give);
    player.getInventory().setHeldItemSlot(targetSlot);
    player.updateInventory();
    debug(
        "handled direct source="
            + source
            + " result="
            + pick.type()
            + tierSuffix(pick.expectedTier())
            + " sourceSlot="
            + existingSlot
            + " targetSlot="
            + targetSlot);
  }

  private int chooseDirectTargetSlot(PlayerInventory inv, int held) {
    int empty = findEmptyHotbar(inv, held);
    return empty >= 0 ? empty : held;
  }

  private boolean isTerminal(Block block) {
    return Carriers.matchesCarrier(block, terminalCarrier)
        && TerminalMarker.isTerminal(plugin, block);
  }

  private boolean isWire(Block block) {
    return Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
  }

  private boolean isStorage(Block block) {
    return Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent();
  }

  private boolean isStorageCore(Block block) {
    return Carriers.matchesCarrier(block, storageCarrier)
        && StorageCoreMarker.isCore(plugin, block);
  }

  private boolean isMonitor(Block block) {
    return Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
  }

  private boolean isBus(Block block) {
    return Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
  }

  private Optional<StorageTier> readTier(Block block) {
    return StorageMarker.get(plugin, block).map(StorageMarker.Data::tier);
  }

  private int findExisting(PlayerInventory inv, String type, String expectedTier) {
    ItemStack[] contents = inv.getContents();
    for (int i = 0; i < contents.length; i++) {
      if (matchesType(contents[i], type, expectedTier)) {
        return i;
      }
    }
    return -1;
  }

  private boolean matchesType(ItemStack stack, String type, String expectedTier) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String t = pdc.get(keys.type(), PersistentDataType.STRING);
    if (!type.equalsIgnoreCase(t)) return false;
    if ("storage".equalsIgnoreCase(type) && expectedTier != null) {
      String tier = pdc.get(keys.storageTier(), PersistentDataType.STRING);
      return expectedTier.equalsIgnoreCase(tier);
    }
    return true;
  }

  private int findEmptyHotbar(PlayerInventory inv, int startSlot) {
    for (int i = startSlot; i <= 8; i++) {
      ItemStack stack = inv.getItem(i);
      if (stack == null || stack.getType() == Material.AIR) {
        return i;
      }
    }
    for (int i = 0; i < startSlot; i++) {
      ItemStack stack = inv.getItem(i);
      if (stack == null || stack.getType() == Material.AIR) {
        return i;
      }
    }
    return -1;
  }

  private void debug(String message) {
    var service = plugin.getPickDebugService();
    if (service != null) {
      service.record(message);
    }
  }

  private String describePickEvent(PlayerPickItemEvent event) {
    StringBuilder builder =
        new StringBuilder(event.getClass().getSimpleName())
            .append(" player=")
            .append(event.getPlayer().getName())
            .append(" cancelled=")
            .append(event.isCancelled())
            .append(" sourceSlot=")
            .append(event.getSourceSlot())
            .append(" targetSlot=")
            .append(event.getTargetSlot())
            .append(" includeData=")
            .append(event.isIncludeData());
    if (event instanceof PlayerPickBlockEvent blockEvent) {
      builder.append(" block=").append(describeBlock(blockEvent.getBlock()));
    } else if (event instanceof PlayerPickEntityEvent entityEvent) {
      builder.append(" entity=").append(describeEntity(entityEvent.getEntity()));
    }
    builder
        .append(" playerTarget=")
        .append(describeBlock(event.getPlayer().getTargetBlockExact(8, FluidCollisionMode.NEVER)));
    return builder.toString();
  }

  private String describeBlock(Block block) {
    if (block == null) {
      return "null";
    }
    return block.getWorld().getName()
        + "@"
        + block.getX()
        + ","
        + block.getY()
        + ","
        + block.getZ()
        + ":"
        + block.getType()
        + markers(block);
  }

  private String describeEntity(Entity entity) {
    if (entity == null) {
      return "null";
    }
    Location loc = entity.getLocation();
    return entity.getType()
        + "@"
        + loc.getWorld().getName()
        + ","
        + loc.getBlockX()
        + ","
        + loc.getBlockY()
        + ","
        + loc.getBlockZ()
        + " tags="
        + entity.getScoreboardTags();
  }

  private String markers(Block block) {
    StringBuilder builder = new StringBuilder("[");
    boolean any = false;
    var storage = StorageMarker.get(plugin, block).orElse(null);
    if (storage != null) {
      builder.append("storage:").append(storage.tier().key());
      any = true;
    }
    if (StorageCoreMarker.isCore(plugin, block)) {
      if (any) builder.append(",");
      builder.append("core");
      any = true;
    }
    if (TerminalMarker.isTerminal(plugin, block)) {
      if (any) builder.append(",");
      builder.append("terminal:").append(TerminalMarker.kind(plugin, block).name());
      any = true;
    }
    if (MonitorMarker.isMonitor(plugin, block)) {
      if (any) builder.append(",");
      builder.append("monitor");
      any = true;
    }
    var bus = BusMarker.get(plugin, block).orElse(null);
    if (bus != null) {
      if (any) builder.append(",");
      builder.append("bus:").append(bus.type().name());
      any = true;
    }
    if (WireMarker.isWire(plugin, block)) {
      if (any) builder.append(",");
      builder.append("wire");
      any = true;
    }
    if (!any) {
      builder.append("none");
    }
    return builder.append("]").toString();
  }

  private String tierSuffix(String expectedTier) {
    return expectedTier == null ? "" : ":" + expectedTier;
  }

  private record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  private record RecentPick(BlockKey block, int tick) {}

  private record PickTarget(ItemStack item, String type, String expectedTier) {}
}
