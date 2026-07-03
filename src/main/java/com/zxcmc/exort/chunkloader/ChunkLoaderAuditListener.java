package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.items.CustomItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkLoaderAuditListener implements Listener {
  private final JavaPlugin plugin;
  private final CustomItems customItems;
  private final Database database;
  private final ChunkLoaderAuditLogger auditLogger;
  private final Map<UUID, EntityDamageEvent.DamageCause> itemDamageCauses = new HashMap<>();
  private final ChunkLoaderItemSnapshot.Resolver snapshotResolver;

  public ChunkLoaderAuditListener(
      JavaPlugin plugin,
      CustomItems customItems,
      Database database,
      ChunkLoaderAuditLogger auditLogger) {
    this.plugin = plugin;
    this.customItems = customItems;
    this.database = database;
    this.auditLogger = auditLogger;
    this.snapshotResolver =
        new ChunkLoaderItemSnapshot.Resolver() {
          @Override
          public boolean isChunkLoader(ItemStack stack) {
            return ChunkLoaderAuditListener.this.customItems.isChunkLoader(stack);
          }

          @Override
          public Optional<UUID> chunkLoaderId(ItemStack stack) {
            return ChunkLoaderAuditListener.this.chunkLoaderId(stack);
          }

          @Override
          public ChunkLoaderType chunkLoaderType(ItemStack stack) {
            return ChunkLoaderAuditListener.this.customItems.chunkLoaderType(stack);
          }
        };
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onDrop(PlayerDropItemEvent event) {
    ItemStack stack = event.getItemDrop().getItemStack();
    if (customItems.isChunkLoader(stack)) {
      auditLogger.log(
          ChunkLoaderAuditEvent.DROP,
          event.getPlayer(),
          chunkLoaderId(stack).orElse(null),
          customItems.chunkLoaderType(stack),
          null,
          "amount=" + stack.getAmount());
      observeItem(
          stack,
          event.getPlayer(),
          ChunkLoaderAuditEvent.DROP,
          "drop",
          event.getItemDrop().getLocation());
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onPickup(EntityPickupItemEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    ItemStack stack = event.getItem().getItemStack();
    if (customItems.isChunkLoader(stack)) {
      auditLogger.log(
          ChunkLoaderAuditEvent.PICKUP,
          player,
          chunkLoaderId(stack).orElse(null),
          customItems.chunkLoaderType(stack),
          null,
          "amount=" + stack.getAmount());
      observeItem(stack, player, ChunkLoaderAuditEvent.PICKUP, "pickup", player.getLocation());
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    Inventory top = event.getView().getTopInventory();
    if (isExternalInventory(top)) {
      scheduleInventoryBoundaryAudit(
          player, top, snapshotInventory(top), snapshotPlayerState(player));
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onCreativeInventory(InventoryCreativeEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    ChunkLoaderItemSnapshot before = snapshotPlayerState(player);
    if (!before.isEmpty()) {
      scheduleInventoryLossAudit(player, player, before, "creative_inventory");
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    Inventory top = event.getView().getTopInventory();
    if (!isExternalInventory(top)) {
      return;
    }
    scheduleInventoryBoundaryAudit(
        player, top, snapshotInventory(top), snapshotPlayerState(player));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onInventoryMoveItem(InventoryMoveItemEvent event) {
    ChunkLoaderItemSnapshot moving = snapshotStack(event.getItem());
    if (moving.isEmpty()) {
      return;
    }
    Inventory source = event.getSource();
    Inventory destination = event.getDestination();
    ChunkLoaderItemSnapshot beforeSource = snapshotInventory(source);
    ChunkLoaderItemSnapshot beforeDestination = snapshotInventory(destination);
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () ->
                logAutomationTransfer(
                    moving, source, destination, beforeSource, beforeDestination));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onItemDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Item item && customItems.isChunkLoader(item.getItemStack())) {
      itemDamageCauses.put(item.getUniqueId(), event.getCause());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onEntityRemove(EntityRemoveEvent event) {
    if (!(event.getEntity() instanceof Item item)) {
      return;
    }
    EntityDamageEvent.DamageCause damageCause = itemDamageCauses.remove(item.getUniqueId());
    if (!customItems.isChunkLoader(item.getItemStack()) || !isDestroyCause(event.getCause())) {
      return;
    }
    ItemStack stack = item.getItemStack();
    auditLogger.logItemDestroy(
        null,
        null,
        chunkLoaderId(stack).orElse(null),
        customItems.chunkLoaderType(stack),
        stack.getAmount(),
        destroyReason(event.getCause(), damageCause),
        item.getLocation());
    recordItemLoss(
        chunkLoaderId(stack).orElse(null),
        registryStatusForEntityRemove(damageCause),
        null,
        destroyReason(event.getCause(), damageCause),
        item.getLocation());
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
    if (isClearCommand(event.getMessage())) {
      scheduleClearAudit(event.getPlayer(), "/clear");
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onServerCommand(ServerCommandEvent event) {
    if (isClearCommand(event.getCommand())) {
      scheduleClearAudit(event.getSender() instanceof Player player ? player : null, "/clear");
    }
  }

  private Optional<UUID> chunkLoaderId(ItemStack item) {
    return customItems.chunkLoaderId(item);
  }

  private boolean isExternalInventory(Inventory inventory) {
    if (inventory == null) {
      return false;
    }
    InventoryType type = inventory.getType();
    return type != InventoryType.PLAYER
        && type != InventoryType.CRAFTING
        && type != InventoryType.CREATIVE;
  }

  private String inventoryName(Inventory inventory) {
    return inventory == null ? "external_inventory" : inventory.getType().name();
  }

  private boolean isDestroyCause(EntityRemoveEvent.Cause cause) {
    return switch (cause) {
      case PICKUP, UNLOAD, MERGE, PLAYER_QUIT, DROP, TRANSFORMATION -> false;
      default -> true;
    };
  }

  private String destroyReason(
      EntityRemoveEvent.Cause cause, EntityDamageEvent.DamageCause damageCause) {
    if (damageCause != null) {
      return damageCause.name().toLowerCase(java.util.Locale.ROOT);
    }
    return cause.name().toLowerCase(java.util.Locale.ROOT);
  }

  private boolean isClearCommand(String command) {
    if (command == null) {
      return false;
    }
    String trimmed = command.trim();
    if (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    if (trimmed.isBlank()) {
      return false;
    }
    String literal = trimmed.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
    return "clear".equals(literal) || literal.endsWith(":clear");
  }

  private void scheduleClearAudit(Player actor, String reason) {
    Map<UUID, InventorySnapshot> before = snapshotOnlinePlayers();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              for (Map.Entry<UUID, InventorySnapshot> entry : before.entrySet()) {
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target == null) {
                  continue;
                }
                logInventoryLoss(
                    actor,
                    target,
                    entry.getValue().items(),
                    snapshotPlayerState(target),
                    reason,
                    ChunkLoaderRegistryStatus.REMOVED);
              }
            });
  }

  private Map<UUID, InventorySnapshot> snapshotOnlinePlayers() {
    Map<UUID, InventorySnapshot> result = new HashMap<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      ChunkLoaderItemSnapshot items = snapshotPlayerState(player);
      if (!items.isEmpty()) {
        result.put(player.getUniqueId(), new InventorySnapshot(items));
      }
    }
    return result;
  }

  private void scheduleInventoryLossAudit(
      Player actor, Player target, ChunkLoaderItemSnapshot before, String reason) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!target.isOnline()) {
                return;
              }
              logInventoryLoss(
                  actor,
                  target,
                  before,
                  snapshotPlayerState(target),
                  reason,
                  ChunkLoaderRegistryStatus.LOST);
            });
  }

  private void scheduleInventoryBoundaryAudit(
      Player actor,
      Inventory inventory,
      ChunkLoaderItemSnapshot beforeExternal,
      ChunkLoaderItemSnapshot beforePlayer) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!actor.isOnline()) {
                return;
              }
              ChunkLoaderItemSnapshot afterExternal = snapshotInventory(inventory);
              ChunkLoaderItemSnapshot afterPlayer = snapshotPlayerState(actor);
              for (ChunkLoaderInventoryDiff.Transfer transfer :
                  ChunkLoaderInventoryDiff.diff(
                      beforeExternal, beforePlayer, afterExternal, afterPlayer)) {
                logInventoryBoundaryTransfer(actor, inventory, transfer);
              }
            });
  }

  private void logInventoryBoundaryTransfer(
      Player actor, Inventory inventory, ChunkLoaderInventoryDiff.Transfer transfer) {
    Location inventoryLocation = observedInventoryLocation(actor, inventory);
    String inventoryName = inventoryName(inventory);
    switch (transfer.direction()) {
      case INTO_EXTERNAL -> {
        auditLogger.logInventoryTransfer(
            actor,
            transfer.id(),
            transfer.type(),
            transfer.amount(),
            inventoryName,
            inventoryLocation);
        observeId(
            transfer.id(),
            transfer.type(),
            actor,
            ChunkLoaderAuditEvent.INVENTORY_MOVE,
            "inventory_into",
            inventoryLocation);
      }
      case INTO_PLAYER -> {
        auditLogger.logInventoryTake(
            actor,
            transfer.id(),
            transfer.type(),
            transfer.amount(),
            inventoryName,
            inventoryLocation);
        observeId(
            transfer.id(),
            transfer.type(),
            actor,
            ChunkLoaderAuditEvent.INVENTORY_MOVE,
            "inventory_take",
            actor.getLocation());
      }
      case LOST_FROM_EXTERNAL -> {
        auditLogger.logInventoryDisappearance(
            actor,
            transfer.id(),
            transfer.type(),
            transfer.amount(),
            inventoryName,
            inventoryLocation);
        recordItemLoss(
            transfer.id(),
            ChunkLoaderRegistryStatus.LOST,
            actor,
            "inventory_interaction",
            inventoryLocation);
      }
    }
  }

  private void logAutomationTransfer(
      ChunkLoaderItemSnapshot moving,
      Inventory source,
      Inventory destination,
      ChunkLoaderItemSnapshot beforeSource,
      ChunkLoaderItemSnapshot beforeDestination) {
    ChunkLoaderItemSnapshot afterSource = snapshotInventory(source);
    ChunkLoaderItemSnapshot afterDestination = snapshotInventory(destination);
    Location sourceLocation = inventoryLocation(source);
    Location destinationLocation = inventoryLocation(destination);
    String sourceName = inventoryName(source);
    String destinationName = inventoryName(destination);
    for (ChunkLoaderAutomationDiff.Result result :
        ChunkLoaderAutomationDiff.diff(
            moving, beforeSource, beforeDestination, afterSource, afterDestination)) {
      switch (result.action()) {
        case MOVED -> {
          auditLogger.logAutomationTransfer(
              result.id(),
              result.type(),
              result.amount(),
              sourceName,
              destinationName,
              sourceLocation,
              destinationLocation);
          observeId(
              result.id(),
              result.type(),
              null,
              ChunkLoaderAuditEvent.INVENTORY_MOVE,
              "automation_move",
              destinationLocation == null ? sourceLocation : destinationLocation);
        }
        case LOST -> {
          auditLogger.logAutomationLoss(
              result.id(),
              result.type(),
              result.amount(),
              sourceName,
              destinationName,
              sourceLocation,
              destinationLocation);
          recordItemLoss(
              result.id(),
              ChunkLoaderRegistryStatus.LOST,
              null,
              "automation_transfer",
              sourceLocation == null ? destinationLocation : sourceLocation);
        }
      }
    }
  }

  private void logInventoryLoss(
      Player actor,
      Player target,
      ChunkLoaderItemSnapshot before,
      ChunkLoaderItemSnapshot after,
      String reason,
      ChunkLoaderRegistryStatus status) {
    for (ChunkLoaderItemSnapshot.Key key : before.keys()) {
      int lost = before.count(key) - after.count(key);
      if (lost > 0) {
        auditLogger.logItemDestroy(
            actor, target, key.id(), key.type(), lost, reason, target.getLocation());
        recordItemLoss(key.id(), status, target, reason, target.getLocation());
      }
    }
  }

  private void observeItem(
      ItemStack stack,
      Player actor,
      ChunkLoaderAuditEvent event,
      String source,
      Location location) {
    observeId(
        chunkLoaderId(stack).orElse(null),
        customItems.chunkLoaderType(stack),
        actor,
        event,
        source,
        location);
  }

  private void observeId(
      UUID id,
      ChunkLoaderType type,
      Player actor,
      ChunkLoaderAuditEvent event,
      String source,
      Location location) {
    if (id == null || database == null) {
      return;
    }
    ChunkLoaderObservation observation =
        ChunkLoaderObservation.atLocation(
            id, ChunkLoaderRegistryStatus.ITEM, location, actor, source, null);
    if (observation == null) {
      return;
    }
    database
        .recordChunkLoaderObservation(observation)
        .whenComplete(
            (found, err) -> {
              if (err != null || !Boolean.TRUE.equals(found)) {
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin, () -> auditLogger.logFound(event, actor, id, type, source, location));
            });
  }

  private void recordItemLoss(
      UUID id, ChunkLoaderRegistryStatus status, Player actor, String reason, Location location) {
    if (id == null || database == null) {
      return;
    }
    ChunkLoaderObservation observation =
        ChunkLoaderObservation.atLocation(id, status, location, actor, reason, reason);
    if (observation != null) {
      database.recordChunkLoaderObservation(observation);
    }
  }

  private ChunkLoaderRegistryStatus registryStatusForEntityRemove(
      EntityDamageEvent.DamageCause damageCause) {
    if (damageCause == null) {
      return ChunkLoaderRegistryStatus.LOST;
    }
    return switch (damageCause) {
      case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, VOID -> ChunkLoaderRegistryStatus.REMOVED;
      default -> ChunkLoaderRegistryStatus.LOST;
    };
  }

  private Location observedInventoryLocation(Player actor, Inventory inventory) {
    Location location = inventory == null ? null : inventory.getLocation();
    return location == null && actor != null ? actor.getLocation() : location;
  }

  private ChunkLoaderItemSnapshot snapshotPlayerState(Player player) {
    List<ItemStack> stacks = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
    stacks.add(player.getOpenInventory().getCursor());
    return ChunkLoaderItemSnapshot.of(stacks, snapshotResolver);
  }

  private ChunkLoaderItemSnapshot snapshotInventory(Inventory inventory) {
    if (inventory == null) {
      return ChunkLoaderItemSnapshot.empty();
    }
    return ChunkLoaderItemSnapshot.of(Arrays.asList(inventory.getContents()), snapshotResolver);
  }

  private ChunkLoaderItemSnapshot snapshotStack(ItemStack stack) {
    return ChunkLoaderItemSnapshot.of(stack, snapshotResolver);
  }

  private Location inventoryLocation(Inventory inventory) {
    return inventory == null ? null : inventory.getLocation();
  }

  private record InventorySnapshot(ChunkLoaderItemSnapshot items) {}
}
