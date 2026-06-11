package com.zxcmc.exort.bus;

import com.zxcmc.exort.bus.resolver.BusTargetResolver;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.platform.PlayerInteractionRange;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.text.GuiOverlayGlyphs;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BusSessionManager {
  private final JavaPlugin plugin;
  private final StorageKeys keys;
  private final BossBarManager bossBarManager;
  private final BooleanSupplier resourceMode;
  private final Supplier<Material> busCarrier;
  private final int wireLimit;
  private final int wireHardCap;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Supplier<GuiRuntimeConfig> runtimeConfigSource;
  private final Supplier<GuiOverlayConfig> overlayConfigSource;
  private final BusService busService;
  private final Lang lang;
  private final ItemNameService itemNameService;
  private final Map<UUID, BusSession> byPlayer = new HashMap<>();
  private final Map<BusPos, Set<BusSession>> byBus = new HashMap<>();
  private GuiRuntimeConfig runtimeConfig;
  private GuiOverlayConfig overlayConfig;
  private int deviceTaskId = -1;

  public BusSessionManager(BusSessionDependencies dependencies, BusService busService, Lang lang) {
    this.plugin = dependencies.plugin();
    this.keys = dependencies.keys();
    this.bossBarManager = dependencies.bossBarManager();
    this.resourceMode = dependencies.resourceMode();
    this.busCarrier = dependencies.busCarrier();
    this.wireLimit = dependencies.wireLimit();
    this.wireHardCap = dependencies.wireHardCap();
    this.wireMaterial = dependencies.wireMaterial();
    this.storageCarrier = dependencies.storageCarrier();
    this.runtimeConfigSource = dependencies.runtimeConfig();
    this.overlayConfigSource = dependencies.overlayConfig();
    this.busService = busService;
    this.lang = lang;
    this.itemNameService = dependencies.itemNameService();
    this.runtimeConfig = runtimeConfigSource.get();
    this.overlayConfig = overlayConfigSource.get();
  }

  public void reconfigure() {
    runtimeConfig = runtimeConfigSource.get();
    overlayConfig = overlayConfigSource.get();
    restartDeviceWatcher();
  }

  public void shutdown() {
    stopDeviceWatcher();
    for (BusSession session : new ArrayList<>(byPlayer.values())) {
      forceCloseSession(session.getViewer());
    }
  }

  private void startDeviceWatcher() {
    if (deviceTaskId != -1) return;
    long intervalTicks = runtimeConfig.sessionDeviceCheckIntervalTicks();
    deviceTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin,
                () -> {
                  Material currentBusCarrier = busCarrier.get();
                  List<Player> toClose = new ArrayList<>();
                  for (BusSession session : new ArrayList<>(byPlayer.values())) {
                    if (shouldCloseSession(session, currentBusCarrier)) {
                      toClose.add(session.getViewer());
                    }
                  }
                  for (Player player : toClose) {
                    forceCloseSession(player);
                  }
                },
                intervalTicks,
                intervalTicks);
  }

  private void stopDeviceWatcher() {
    if (deviceTaskId != -1) {
      Bukkit.getScheduler().cancelTask(deviceTaskId);
      deviceTaskId = -1;
    }
  }

  private void restartDeviceWatcher() {
    stopDeviceWatcher();
    startDeviceWatcher();
  }

  public boolean openSession(Player player, Block busBlock) {
    if (busBlock == null) return false;
    var dataOpt = BusMarker.get(plugin, busBlock);
    if (dataOpt.isEmpty()) return false;
    BusPos pos = BusPos.of(busBlock);
    BusState state = busService.getOrCreateState(pos, dataOpt.get(), busBlock);
    if (state == null) return false;
    BusSession existing = byPlayer.get(player.getUniqueId());
    if (existing != null) {
      closeSession(player);
    }
    Component title = titleFor(player, state.type());
    BusSession session =
        new BusSession(
            player,
            this,
            state,
            lang,
            bossBarManager,
            !resourceMode.getAsBoolean(),
            title,
            busBlock);
    byPlayer.put(player.getUniqueId(), session);
    byBus.computeIfAbsent(pos, k -> new HashSet<>()).add(session);
    state.viewerOpened();
    bossBarManager.remove(player);
    player.openInventory(session.getInventory());
    session.render();
    return true;
  }

  private Component titleFor(Player viewer, BusType type) {
    boolean resourceMode = this.resourceMode.getAsBoolean();
    String nameKey = type == BusType.EXPORT ? "gui.bus.export_title" : "gui.bus.import_title";
    Component name = ExortText.plain(lang.tr(viewer, nameKey));
    if (!resourceMode) {
      return name;
    }
    return GuiOverlayGlyphs.overlay(GuiOverlayConfig.BUS_KEY, overlayConfig.bus(), ExortLog::warn)
        .map(overlay -> ExortText.withPrefix(overlay, name))
        .orElse(name);
  }

  public void closeSession(Player player) {
    closeSessionState(player, null);
  }

  public void closeSession(Player player, BusSession expectedSession) {
    closeSessionState(player, expectedSession);
  }

  public void forceCloseSession(Player player) {
    if (player == null) return;
    closeSessionState(player, null);
    player.closeInventory();
  }

  private boolean closeSessionState(Player player, BusSession expectedSession) {
    if (player == null) return false;
    if (expectedSession != null && byPlayer.get(player.getUniqueId()) != expectedSession) {
      return false;
    }
    BusSession session = byPlayer.remove(player.getUniqueId());
    if (session == null) return false;
    BusPos pos = session.getState().pos();
    Set<BusSession> set = byBus.get(pos);
    if (set != null) {
      set.remove(session);
      if (set.isEmpty()) {
        byBus.remove(pos);
      }
    }
    session.getState().viewerClosed();
    session.onClose();
    return true;
  }

  public void closeSessionsForBus(Block busBlock) {
    if (busBlock == null) return;
    BusPos pos = BusPos.of(busBlock);
    Set<BusSession> sessions = byBus.getOrDefault(pos, Collections.emptySet());
    for (BusSession session : new ArrayList<>(sessions)) {
      forceCloseSession(session.getViewer());
    }
  }

  public BusSession sessionFor(Player player) {
    return byPlayer.get(player.getUniqueId());
  }

  public void saveSettings(BusState state) {
    if (state == null) return;
    busService.saveSettings(state);
  }

  public void saveSettings(BusState state, Block busBlock) {
    if (state == null) return;
    busService.saveSettings(state, busBlock);
  }

  public BusLinkStatus resolveStatus(BusState state, Player viewer) {
    if (state == null) {
      return new BusLinkStatus(StorageState.NONE, null, null, null, false);
    }
    Block busBlock = state.pos().block();
    if (busBlock == null) {
      return new BusLinkStatus(StorageState.NONE, null, null, null, false);
    }
    var link =
        TerminalLinkFinder.find(
            busBlock, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
    boolean loop = busService.isLoopDisabled(state.pos());
    if (link.count() == 0 || link.data() == null) {
      return new BusLinkStatus(StorageState.NONE, null, null, null, loop);
    }
    if (link.count() > 1) {
      return new BusLinkStatus(StorageState.MULTIPLE, null, null, null, loop);
    }
    String storageId = link.data().storageId();
    StorageTier tier = link.data().tier();
    String storageName = tier == null ? storageId : tier.displayName();
    var targetOpt = busService.resolveTarget(busBlock, state.facing());
    String invName = null;
    if (targetOpt.isPresent()) {
      BusTargetResolver.BusTarget target = targetOpt.get();
      if (target instanceof BusTargetResolver.InventoryTarget inv) {
        invName = inventoryDisplayName(inv.block().getType(), viewer);
      } else if (target instanceof BusTargetResolver.StorageTarget storageTarget) {
        StorageTier targetTier = storageTarget.tier();
        invName =
            targetTier != null
                ? targetTier.displayName()
                : lang.tr(viewer, "gui.bus.info.exort_storage");
      }
    }
    return new BusLinkStatus(StorageState.OK, storageId, storageName, invName, loop);
  }

  String inventoryDisplayName(Material material, Player viewer) {
    if (material == null) {
      return "";
    }
    String language =
        itemNameService.dictionaryLanguage(
            viewer == null ? null : viewer.locale().toString(),
            itemNameService.getActiveLanguage());
    String resolved = itemNameService.resolveDictionaryName(material.getKey().getKey(), language);
    return resolved == null || resolved.isBlank() ? humanizeMaterial(material) : resolved;
  }

  static String humanizeMaterial(Material material) {
    if (material == null) {
      return "";
    }
    String[] parts = material.name().toLowerCase(Locale.ROOT).split("_+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        builder.append(part.substring(1));
      }
    }
    return builder.isEmpty() ? material.name() : builder.toString();
  }

  public enum StorageState {
    OK,
    NONE,
    MULTIPLE
  }

  public record BusLinkStatus(
      StorageState storageState,
      String storageId,
      String storageName,
      String inventoryName,
      boolean loopDisabled) {}

  private boolean shouldCloseSession(BusSession session, Material busCarrier) {
    Player player = session.getViewer();
    if (player == null || !player.isOnline()) return true;
    Block busBlock = session.getBusBlock();
    if (busBlock == null) return true;
    if (busCarrier == null) return false;
    if (!isBlockChunkLoaded(busBlock)) return true;
    if (!Carriers.matchesCarrier(busBlock, busCarrier)) return true;
    if (BusMarker.get(plugin, busBlock).isEmpty()) return true;
    return isOutOfDeviceRange(player, busBlock);
  }

  private boolean isOutOfDeviceRange(Player player, Block block) {
    if (player.getWorld() == null || block.getWorld() == null) return true;
    if (!player.getWorld().getUID().equals(block.getWorld().getUID())) return true;
    Location playerLocation = player.getLocation();
    double dx = playerLocation.getX() - (block.getX() + 0.5D);
    double dy = playerLocation.getY() - (block.getY() + 0.5D);
    double dz = playerLocation.getZ() - (block.getZ() + 0.5D);
    return dx * dx + dy * dy + dz * dz
        > PlayerInteractionRange.physicalDeviceCloseRangeSquared(player);
  }

  private boolean isBlockChunkLoaded(Block block) {
    return block.getWorld() != null
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }
}
