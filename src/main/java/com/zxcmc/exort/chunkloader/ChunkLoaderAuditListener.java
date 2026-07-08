package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.items.CustomItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkLoaderAuditListener implements Listener, ChunkLoaderCreativeAudit {
  private final JavaPlugin plugin;
  private final CustomItems customItems;
  private final Database database;
  private final ChunkLoaderAuditLogger auditLogger;
  private final Map<UUID, EntityDamageEvent.DamageCause> itemDamageCauses = new HashMap<>();
  private final Map<UUID, CreativeInventorySession> creativeSessions = new HashMap<>();
  private final Predicate<UUID> activeLoaderIds;
  private final ChunkLoaderItemSnapshot.Resolver snapshotResolver;

  public ChunkLoaderAuditListener(
      JavaPlugin plugin,
      CustomItems customItems,
      Database database,
      ChunkLoaderAuditLogger auditLogger) {
    this(plugin, customItems, database, auditLogger, id -> false);
  }

  public ChunkLoaderAuditListener(
      JavaPlugin plugin,
      CustomItems customItems,
      Database database,
      ChunkLoaderAuditLogger auditLogger,
      Predicate<UUID> activeLoaderIds) {
    this.plugin = plugin;
    this.customItems = customItems;
    this.database = database;
    this.auditLogger = auditLogger;
    this.activeLoaderIds = activeLoaderIds == null ? id -> false : activeLoaderIds;
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
      recordCreativeSessionDelta(event.getPlayer(), snapshotStack(stack), -1);
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
      recordCreativeSessionDelta(player, snapshotStack(stack), 1);
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
    creativeSessions.computeIfAbsent(
        player.getUniqueId(), ignored -> new CreativeInventorySession(before));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onInventoryClose(InventoryCloseEvent event) {
    if (event.getPlayer() instanceof Player player) {
      flushCreativeSession(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    flushCreativeSession(event.getPlayer());
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onGameModeChange(PlayerGameModeChangeEvent event) {
    flushCreativeSession(event.getPlayer());
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
    if (!customItems.isChunkLoader(item.getItemStack())
        || !shouldAuditEntityRemove(event.getCause())) {
      return;
    }
    ItemStack stack = item.getItemStack();
    String reason = destroyReason(event.getCause(), damageCause);
    ChunkLoaderRegistryStatus status = registryStatusForEntityRemove(event.getCause(), damageCause);
    auditLogger.logItemDestroy(
        null,
        null,
        chunkLoaderId(stack).orElse(null),
        customItems.chunkLoaderType(stack),
        stack.getAmount(),
        reason,
        item.getLocation());
    recordItemLoss(chunkLoaderId(stack).orElse(null), status, null, reason, item.getLocation());
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onPlayerDeath(PlayerDeathEvent event) {
    if (event.getKeepInventory()) {
      return;
    }
    Player player = event.getPlayer();
    flushCreativeSession(player);
    ChunkLoaderItemSnapshot before = snapshotPlayerState(player);
    if (before.isEmpty()) {
      return;
    }
    ChunkLoaderItemSnapshot dropped =
        ChunkLoaderItemSnapshot.of(event.getDrops(), snapshotResolver);
    ChunkLoaderItemSnapshot kept =
        ChunkLoaderItemSnapshot.of(event.getItemsToKeep(), snapshotResolver);
    ChunkLoaderItemSnapshot vanishing = snapshotVanishingPlayerState(player);
    for (DeathLoss loss : deathLosses(before, dropped, kept, vanishing)) {
      auditLogger.logItemDestroy(
          player,
          player,
          loss.key().id(),
          loss.key().type(),
          loss.amount(),
          loss.reason(),
          player.getLocation());
      recordItemLoss(loss.key().id(), loss.status(), player, loss.reason(), player.getLocation());
    }
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

  static boolean shouldAuditEntityRemove(EntityRemoveEvent.Cause cause) {
    return switch (cause) {
      case PICKUP, UNLOAD, MERGE, PLAYER_QUIT, DROP, TRANSFORMATION -> false;
      default -> true;
    };
  }

  static String destroyReason(
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

  private void flushCreativeSession(Player player) {
    CreativeInventorySession session = creativeSessions.remove(player.getUniqueId());
    if (session == null) {
      return;
    }
    for (CreativeInventoryVerdict verdict :
        creativeInventoryVerdicts(
            session.baseline(),
            session.ledgerDeltas(),
            snapshotPlayerState(player),
            activeLoaderIds)) {
      switch (verdict.action()) {
        case DESTROY -> {
          auditLogger.logItemDestroy(
              player,
              player,
              verdict.key().id(),
              verdict.key().type(),
              verdict.amount(),
              "creative_inventory",
              player.getLocation());
          recordItemLoss(
              verdict.key().id(),
              ChunkLoaderRegistryStatus.REMOVED,
              player,
              "creative_inventory",
              player.getLocation());
        }
        case ISSUE ->
            auditLogger.logIssue(
                player,
                player,
                verdict.amount(),
                verdict.key().type(),
                "creative_inventory",
                player.getLocation());
        case DUPLICATE ->
            auditLogger.logDuplicate(
                player,
                verdict.key().id(),
                verdict.key().type(),
                verdict.amount(),
                "creative_inventory",
                player.getLocation());
      }
    }
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
        recordCreativeSessionDelta(
            actor,
            new ChunkLoaderItemSnapshot.Key(transfer.id(), transfer.type()),
            -transfer.amount());
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
        recordCreativeSessionDelta(
            actor,
            new ChunkLoaderItemSnapshot.Key(transfer.id(), transfer.type()),
            transfer.amount());
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
        recordCreativeSessionDelta(target, key, -lost);
      }
    }
  }

  static List<CreativeInventoryVerdict> creativeInventoryVerdicts(
      ChunkLoaderItemSnapshot baseline,
      Map<ChunkLoaderItemSnapshot.Key, Integer> ledgerDeltas,
      ChunkLoaderItemSnapshot current) {
    return creativeInventoryVerdicts(baseline, ledgerDeltas, current, id -> false);
  }

  static List<CreativeInventoryVerdict> creativeInventoryVerdicts(
      ChunkLoaderItemSnapshot baseline,
      Map<ChunkLoaderItemSnapshot.Key, Integer> ledgerDeltas,
      ChunkLoaderItemSnapshot current,
      Predicate<UUID> activeLoaderIds) {
    ChunkLoaderItemSnapshot safeBaseline =
        baseline == null ? ChunkLoaderItemSnapshot.empty() : baseline;
    ChunkLoaderItemSnapshot safeCurrent =
        current == null ? ChunkLoaderItemSnapshot.empty() : current;
    Map<ChunkLoaderItemSnapshot.Key, Integer> safeLedger =
        ledgerDeltas == null ? Map.of() : ledgerDeltas;
    Predicate<UUID> safeActiveLoaderIds = activeLoaderIds == null ? id -> false : activeLoaderIds;
    Set<ChunkLoaderItemSnapshot.Key> keys = new HashSet<>(safeBaseline.keys());
    keys.addAll(safeCurrent.keys());
    keys.addAll(safeLedger.keySet());
    List<CreativeInventoryVerdict> result = new ArrayList<>();
    for (ChunkLoaderItemSnapshot.Key key : keys.stream().sorted(keyOrder()).toList()) {
      int expected = Math.max(0, safeBaseline.count(key) + safeLedger.getOrDefault(key, 0));
      int actual = safeCurrent.count(key);
      if (actual < expected) {
        result.add(
            new CreativeInventoryVerdict(
                CreativeInventoryVerdictAction.DESTROY, key, expected - actual));
      } else if (actual > expected) {
        CreativeInventoryVerdictAction action =
            key.id() != null && (expected > 0 || safeActiveLoaderIds.test(key.id()))
                ? CreativeInventoryVerdictAction.DUPLICATE
                : CreativeInventoryVerdictAction.ISSUE;
        result.add(new CreativeInventoryVerdict(action, key, actual - expected));
      }
    }
    return List.copyOf(result);
  }

  @Override
  public void recordCreativePickIssue(Player player, ChunkLoaderType type, int amount) {
    if (player == null) {
      return;
    }
    int safeAmount = Math.max(1, amount);
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    auditLogger.logIssue(
        player, player, safeAmount, safeType, "creative_pick", player.getLocation());
    recordCreativeSessionDelta(player, new ChunkLoaderItemSnapshot.Key(null, safeType), safeAmount);
  }

  @Override
  public void recordCreativePickReplacementDestroy(
      Player player, UUID loaderId, ChunkLoaderType type, int amount) {
    if (player == null) {
      return;
    }
    int safeAmount = Math.max(1, amount);
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    auditLogger.logItemDestroy(
        player,
        player,
        loaderId,
        safeType,
        safeAmount,
        "creative_pick_replace",
        player.getLocation());
    recordItemLoss(
        loaderId,
        ChunkLoaderRegistryStatus.REMOVED,
        player,
        "creative_pick_replace",
        player.getLocation());
    recordCreativeSessionDelta(
        player, new ChunkLoaderItemSnapshot.Key(loaderId, safeType), -safeAmount);
  }

  private void recordCreativeSessionDelta(
      Player player, ChunkLoaderItemSnapshot snapshot, int multiplier) {
    if (player == null || snapshot == null || snapshot.isEmpty() || multiplier == 0) {
      return;
    }
    CreativeInventorySession session = creativeSessions.get(player.getUniqueId());
    if (session == null) {
      return;
    }
    for (Map.Entry<ChunkLoaderItemSnapshot.Key, Integer> entry : snapshot.counts().entrySet()) {
      session.addDelta(entry.getKey(), entry.getValue() * multiplier);
    }
  }

  private void recordCreativeSessionDelta(
      Player player, ChunkLoaderItemSnapshot.Key key, int amount) {
    if (player == null || key == null || amount == 0) {
      return;
    }
    CreativeInventorySession session = creativeSessions.get(player.getUniqueId());
    if (session != null) {
      session.addDelta(key, amount);
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

  static ChunkLoaderRegistryStatus registryStatusForEntityRemove(
      EntityRemoveEvent.Cause cause, EntityDamageEvent.DamageCause damageCause) {
    if (isVanillaItemDestruction(damageCause)) {
      return ChunkLoaderRegistryStatus.REMOVED;
    }
    if (damageCause != null) {
      return ChunkLoaderRegistryStatus.LOST;
    }
    return switch (cause) {
      case DEATH, DESPAWN, EXPLODE, OUT_OF_WORLD -> ChunkLoaderRegistryStatus.REMOVED;
      default -> ChunkLoaderRegistryStatus.LOST;
    };
  }

  private static boolean isVanillaItemDestruction(EntityDamageEvent.DamageCause damageCause) {
    if (damageCause == null) {
      return false;
    }
    return switch (damageCause) {
      case BLOCK_EXPLOSION,
          CAMPFIRE,
          CONTACT,
          ENTITY_EXPLOSION,
          FIRE,
          FIRE_TICK,
          HOT_FLOOR,
          KILL,
          LAVA,
          LIGHTNING,
          VOID,
          WORLD_BORDER ->
          true;
      default -> false;
    };
  }

  static List<DeathLoss> deathLosses(
      ChunkLoaderItemSnapshot before,
      ChunkLoaderItemSnapshot dropped,
      ChunkLoaderItemSnapshot kept,
      ChunkLoaderItemSnapshot vanishingBefore) {
    ChunkLoaderItemSnapshot safeBefore = before == null ? ChunkLoaderItemSnapshot.empty() : before;
    ChunkLoaderItemSnapshot safeDropped =
        dropped == null ? ChunkLoaderItemSnapshot.empty() : dropped;
    ChunkLoaderItemSnapshot safeKept = kept == null ? ChunkLoaderItemSnapshot.empty() : kept;
    ChunkLoaderItemSnapshot safeVanishing =
        vanishingBefore == null ? ChunkLoaderItemSnapshot.empty() : vanishingBefore;
    List<DeathLoss> result = new ArrayList<>();
    for (ChunkLoaderItemSnapshot.Key key : safeBefore.keys().stream().sorted(keyOrder()).toList()) {
      int retained = safeDropped.count(key) + safeKept.count(key);
      int lost = Math.max(0, safeBefore.count(key) - retained);
      if (lost <= 0) {
        continue;
      }
      int vanishingLost = Math.min(lost, safeVanishing.count(key));
      if (vanishingLost > 0) {
        result.add(
            new DeathLoss(
                key, vanishingLost, ChunkLoaderRegistryStatus.REMOVED, "curse_of_vanishing"));
        lost -= vanishingLost;
      }
      if (lost > 0) {
        result.add(new DeathLoss(key, lost, ChunkLoaderRegistryStatus.LOST, "death_no_drop"));
      }
    }
    return List.copyOf(result);
  }

  private static Comparator<ChunkLoaderItemSnapshot.Key> keyOrder() {
    return Comparator.comparing(
            ChunkLoaderItemSnapshot.Key::id, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.type().id());
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

  private ChunkLoaderItemSnapshot snapshotVanishingPlayerState(Player player) {
    List<ItemStack> stacks = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
    stacks.add(player.getOpenInventory().getCursor());
    return ChunkLoaderItemSnapshot.ofMatching(stacks, snapshotResolver, this::hasVanishingCurse);
  }

  private boolean hasVanishingCurse(ItemStack stack) {
    return stack != null && stack.containsEnchantment(Enchantment.VANISHING_CURSE);
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

  record DeathLoss(
      ChunkLoaderItemSnapshot.Key key,
      int amount,
      ChunkLoaderRegistryStatus status,
      String reason) {}

  enum CreativeInventoryVerdictAction {
    DESTROY,
    ISSUE,
    DUPLICATE
  }

  record CreativeInventoryVerdict(
      CreativeInventoryVerdictAction action, ChunkLoaderItemSnapshot.Key key, int amount) {}

  private static final class CreativeInventorySession {
    private final ChunkLoaderItemSnapshot baseline;
    private final Map<ChunkLoaderItemSnapshot.Key, Integer> ledgerDeltas = new HashMap<>();

    private CreativeInventorySession(ChunkLoaderItemSnapshot baseline) {
      this.baseline = baseline == null ? ChunkLoaderItemSnapshot.empty() : baseline;
    }

    private ChunkLoaderItemSnapshot baseline() {
      return baseline;
    }

    private Map<ChunkLoaderItemSnapshot.Key, Integer> ledgerDeltas() {
      return Map.copyOf(ledgerDeltas);
    }

    private void addDelta(ChunkLoaderItemSnapshot.Key key, int amount) {
      if (key == null || amount == 0) {
        return;
      }
      ledgerDeltas.merge(key, amount, Integer::sum);
      if (ledgerDeltas.getOrDefault(key, 0) == 0) {
        ledgerDeltas.remove(key);
      }
    }
  }
}
