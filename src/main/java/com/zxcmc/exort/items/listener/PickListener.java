package com.zxcmc.exort.items.listener;

import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.chunkloader.ChunkLoaderCreativeAudit;
import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.items.CustomItemClassifier;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.storage.StorageTier;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
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
import org.bukkit.plugin.Plugin;

public final class PickListener implements Listener {
  private final Plugin plugin;
  private final CustomItems customItems;
  private final StorageKeys keys;
  private final Consumer<String> debugSink;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final Material relayCarrier;
  private final Material transmitterCarrier;
  private final Material chunkLoaderCarrier;
  private final ChunkLoaderCreativeAudit chunkLoaderCreativeAudit;
  private final Map<UUID, RecentPick> recentPicks = new HashMap<>();

  public PickListener(
      Plugin plugin,
      CustomItems customItems,
      StorageKeys keys,
      Consumer<String> debugSink,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier,
      Material relayCarrier,
      Material transmitterCarrier,
      Material chunkLoaderCarrier,
      ChunkLoaderCreativeAudit chunkLoaderCreativeAudit) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.debugSink = debugSink == null ? message -> {} : debugSink;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.relayCarrier = relayCarrier;
    this.transmitterCarrier = transmitterCarrier;
    this.chunkLoaderCarrier = chunkLoaderCarrier;
    this.chunkLoaderCreativeAudit =
        chunkLoaderCreativeAudit == null ? ChunkLoaderCreativeAudit.NOOP : chunkLoaderCreativeAudit;
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
    } else if (isRelay(target)) {
      desired = customItems.relayItem();
      type = "relay";
    } else if (isTransmitter(target)) {
      desired = customItems.transmitterItem();
      type = "transmitter";
    } else if (isChunkLoader(target)) {
      ChunkLoaderType loaderType =
          ChunkLoaderMarker.get(plugin, target)
              .map(ChunkLoaderMarker.Data::type)
              .orElse(ChunkLoaderType.defaultType());
      desired = customItems.chunkLoaderItem(loaderType);
      type = loaderType.id();
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
    PlayerInventory inv = player.getInventory();
    int existingSlot = findExisting(inv, pick);
    if (existingSlot >= 0) {
      event.setCancelled(true);
      PickApplyResult result = pickExistingFromInventory(inv, existingSlot);
      player.updateInventory();
      debug(
          "handled event="
              + event.getClass().getSimpleName()
              + " result="
              + pick.type()
              + tierSuffix(pick.expectedTier())
              + " sourceSlot="
              + result.sourceSlot()
              + " targetSlot="
              + result.targetSlot()
              + " action="
              + result.action());
      return;
    }

    if (player.getGameMode() == GameMode.CREATIVE) {
      event.setCancelled(true);
      PickApplyResult result = applyCreativePick(player, pick, findVanillaPickTargetSlot(inv));
      debug(
          "handled creative event="
              + event.getClass().getSimpleName()
              + " result="
              + pick.type()
              + tierSuffix(pick.expectedTier())
              + " sourceSlot="
              + result.sourceSlot()
              + " targetSlot="
              + result.targetSlot()
              + " action="
              + result.action());
      return;
    }

    event.setCancelled(true);
  }

  private void applyDirectPick(Player player, PickTarget pick, String source) {
    PlayerInventory inv = player.getInventory();
    int existingSlot = findExisting(inv, pick);
    if (existingSlot >= 0) {
      PickApplyResult result = pickExistingFromInventory(inv, existingSlot);
      player.updateInventory();
      debug(
          "handled direct source="
              + source
              + " result="
              + pick.type()
              + tierSuffix(pick.expectedTier())
              + " sourceSlot="
              + result.sourceSlot()
              + " targetSlot="
              + result.targetSlot()
              + " action="
              + result.action());
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
              + existingSlot
              + " action=miss");
      return;
    }

    PickApplyResult result = applyCreativePick(player, pick, findVanillaPickTargetSlot(inv));
    debug(
        "handled direct source="
            + source
            + " result="
            + pick.type()
            + tierSuffix(pick.expectedTier())
            + " sourceSlot="
            + result.sourceSlot()
            + " targetSlot="
            + result.targetSlot()
            + " action="
            + result.action());
  }

  private PickApplyResult applyCreativePick(Player player, PickTarget pick, int targetSlot) {
    PlayerInventory inv = player.getInventory();
    PickApplyResult result = addAndPickCreative(inv, pick.item(), targetSlot);
    if (result.replacedStack() != null && isChunkLoaderStack(result.replacedStack())) {
      ItemStack replaced = result.replacedStack();
      chunkLoaderCreativeAudit.recordCreativePickReplacementDestroy(
          player,
          customItems.chunkLoaderId(replaced).orElse(null),
          customItems.chunkLoaderType(replaced),
          replaced.getAmount());
    }
    if (!"miss".equals(result.action())) {
      player.updateInventory();
      auditCreativePickIssue(player, pick);
    }
    return result;
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

  private boolean isRelay(Block block) {
    return Carriers.matchesCarrier(block, relayCarrier) && RelayMarker.isRelay(plugin, block);
  }

  private boolean isTransmitter(Block block) {
    return Carriers.matchesCarrier(block, transmitterCarrier)
        && TransmitterMarker.isTransmitter(plugin, block);
  }

  private boolean isChunkLoader(Block block) {
    return Carriers.matchesCarrier(block, chunkLoaderCarrier)
        && ChunkLoaderMarker.isChunkLoader(plugin, block);
  }

  private Optional<StorageTier> readTier(Block block) {
    return StorageMarker.get(plugin, block).map(StorageMarker.Data::tier);
  }

  private int findExisting(PlayerInventory inv, PickTarget pick) {
    ItemStack[] contents = inv.getContents();
    for (int i = 0; i < Math.min(36, contents.length); i++) {
      if (matchesPickTarget(contents[i], pick)) {
        return i;
      }
    }
    return -1;
  }

  private boolean matchesPickTarget(ItemStack stack, PickTarget pick) {
    if (pick.isChunkLoader()) {
      return isReusableChunkLoader(stack, pick.chunkLoaderType());
    }
    return matchesType(stack, pick.type(), pick.expectedTier());
  }

  private boolean isReusableChunkLoader(ItemStack stack, ChunkLoaderType type) {
    return isReusableChunkLoader(customItems, stack, type);
  }

  static boolean isReusableChunkLoader(
      CustomItems customItems, ItemStack stack, ChunkLoaderType type) {
    return stack != null
        && stack.getType() != Material.AIR
        && customItems.isChunkLoader(stack)
        && customItems.chunkLoaderType(stack) == type;
  }

  private boolean isChunkLoaderStack(ItemStack stack) {
    return stack != null && stack.getType() != Material.AIR && customItems.isChunkLoader(stack);
  }

  private boolean matchesType(ItemStack stack, String type, String expectedTier) {
    if (!CustomItemClassifier.isType(keys, stack, type)) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    if ("storage".equalsIgnoreCase(type) && expectedTier != null) {
      String tier = pdc.get(keys.storageTier(), PersistentDataType.STRING);
      return expectedTier.equalsIgnoreCase(tier);
    }
    return true;
  }

  private void auditCreativePickIssue(Player player, PickTarget pick) {
    if (pick.isChunkLoader()) {
      chunkLoaderCreativeAudit.recordCreativePickIssue(player, pick.chunkLoaderType(), 1);
    }
  }

  private static int targetSlotForExisting(PlayerInventory inv, int existingSlot) {
    return existingSlot >= 0 && existingSlot <= 8 ? existingSlot : findVanillaPickTargetSlot(inv);
  }

  static int findVanillaPickTargetSlot(PlayerInventory inv) {
    int held = inv.getHeldItemSlot();
    for (int offset = 0; offset < 9; offset++) {
      int slot = hotbarSlotAt(held, offset);
      if (isEmpty(inv.getItem(slot))) {
        return slot;
      }
    }
    for (int offset = 0; offset < 9; offset++) {
      int slot = hotbarSlotAt(held, offset);
      ItemStack stack = inv.getItem(slot);
      if (!isEmpty(stack) && !hasEnchantments(stack)) {
        return slot;
      }
    }
    return held;
  }

  static PickApplyResult pickExistingFromInventory(PlayerInventory inv, int sourceSlot) {
    return pickExistingFromInventory(inv, sourceSlot, targetSlotForExisting(inv, sourceSlot));
  }

  static PickApplyResult pickExistingFromInventory(
      PlayerInventory inv, int sourceSlot, int targetSlot) {
    if (sourceSlot >= 0 && sourceSlot <= 8) {
      inv.setHeldItemSlot(sourceSlot);
      return new PickApplyResult("select", sourceSlot, sourceSlot, -1, null);
    }
    ItemStack targetStack = inv.getItem(targetSlot);
    inv.setItem(targetSlot, inv.getItem(sourceSlot));
    inv.setItem(sourceSlot, targetStack);
    inv.setHeldItemSlot(targetSlot);
    return new PickApplyResult("swap", sourceSlot, targetSlot, -1, null);
  }

  static PickApplyResult addAndPickCreative(
      PlayerInventory inv, ItemStack pickedItem, int targetSlot) {
    if (isEmpty(pickedItem)) {
      return new PickApplyResult("miss", -1, targetSlot, -1, null);
    }
    ItemStack give = pickedItem.clone();
    give.setAmount(1);
    ItemStack displaced = inv.getItem(targetSlot);
    int displacedSlot = -1;
    ItemStack replaced = null;
    if (!isEmpty(displaced)) {
      displacedSlot = findFirstEmptySlot(inv);
      if (displacedSlot >= 0) {
        inv.setItem(displacedSlot, displaced);
      } else {
        replaced = displaced;
      }
    }
    inv.setItem(targetSlot, give);
    inv.setHeldItemSlot(targetSlot);
    return new PickApplyResult(
        displacedSlot >= 0 ? "give_move_displaced" : "give_replace",
        -1,
        targetSlot,
        displacedSlot,
        replaced);
  }

  private static int findFirstEmptySlot(PlayerInventory inv) {
    for (int i = 0; i < 36; i++) {
      if (isEmpty(inv.getItem(i))) {
        return i;
      }
    }
    return -1;
  }

  private static int hotbarSlotAt(int held, int offset) {
    return Math.floorMod(held + offset, 9);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
  }

  private static boolean hasEnchantments(ItemStack stack) {
    return stack != null && !stack.getEnchantments().isEmpty();
  }

  private void debug(String message) {
    debugSink.accept(message);
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
    if (RelayMarker.isRelay(plugin, block)) {
      if (any) builder.append(",");
      builder.append("relay");
      any = true;
    }
    if (ChunkLoaderMarker.isChunkLoader(plugin, block)) {
      if (any) builder.append(",");
      builder.append("chunk_loader");
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

  record PickApplyResult(
      String action, int sourceSlot, int targetSlot, int displacedSlot, ItemStack replacedStack) {}

  record PickTarget(ItemStack item, String type, String expectedTier) {
    boolean isChunkLoader() {
      return ChunkLoaderType.fromNullableId(type).isPresent();
    }

    ChunkLoaderType chunkLoaderType() {
      return ChunkLoaderType.fromNullableId(type).orElse(ChunkLoaderType.defaultType());
    }
  }
}
