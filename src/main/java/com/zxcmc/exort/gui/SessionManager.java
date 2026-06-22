package com.zxcmc.exort.gui;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.session.GuiRenderScheduler;
import com.zxcmc.exort.gui.session.GuiSearchCoordinator;
import com.zxcmc.exort.gui.session.GuiSessionRegistry;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.text.GuiOverlayGlyphs;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SessionManager {
  private final GuiSessionRegistry registry = new GuiSessionRegistry();
  private final GuiRenderScheduler renderScheduler;
  private final GuiSearchCoordinator searchCoordinator = new GuiSearchCoordinator();
  private final Map<String, CraftingState> craftingStates = new HashMap<>();
  private final JavaPlugin plugin;
  private final Database database;
  private final StorageManager storageManager;
  private final StorageKeys keys;
  private final Lang lang;
  private final ItemNameService itemNameService;
  private final SearchDialogService searchDialogService;
  private final Supplier<BossBarManager> bossBarManager;
  private final Supplier<PlayerFeedback> playerFeedback;
  private final Supplier<WirelessTerminalService> wirelessService;
  private final Supplier<BusService> busService;
  private final Supplier<CraftingRules> craftingRules;
  private final BooleanSupplier resourceMode;
  private final BooleanSupplier dialogSupported;
  private final IntSupplier wireLimit;
  private final IntSupplier wireHardCap;
  private final IntSupplier relayRangeChunks;
  private final Supplier<Material> wireMaterial;
  private final Supplier<Material> storageCarrier;
  private final Supplier<Material> relayCarrier;
  private final Supplier<Material> terminalCarrier;
  private final GuiSessionValidator sessionValidator;
  private final Supplier<GuiRuntimeConfig> runtimeConfigSource;
  private final Supplier<GuiOverlayConfig> overlayConfigSource;
  private final Consumer<String> storageChangeListener;
  private GuiRuntimeConfig runtimeConfig;
  private GuiOverlayConfig overlayConfig;
  private int sessionTaskId = -1;

  public SessionManager(SessionManagerDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.database = dependencies.database();
    this.storageManager = dependencies.storageManager();
    this.keys = dependencies.keys();
    this.lang = dependencies.lang();
    this.itemNameService = dependencies.itemNameService();
    this.searchDialogService = dependencies.searchDialogService();
    this.bossBarManager = dependencies.bossBarManager();
    this.playerFeedback = dependencies.playerFeedback();
    this.wirelessService = dependencies.wirelessService();
    this.busService = dependencies.busService();
    this.craftingRules = dependencies.craftingRules();
    this.resourceMode = dependencies.resourceMode();
    this.dialogSupported = dependencies.dialogSupported();
    this.wireLimit = dependencies.wireLimit();
    this.wireHardCap = dependencies.wireHardCap();
    this.relayRangeChunks = dependencies.relayRangeChunks();
    this.wireMaterial = dependencies.wireMaterial();
    this.storageCarrier = dependencies.storageCarrier();
    this.relayCarrier = dependencies.relayCarrier();
    this.terminalCarrier = dependencies.terminalCarrier();
    this.sessionValidator =
        new GuiSessionValidator(
            plugin,
            keys,
            wireLimit,
            wireHardCap,
            relayRangeChunks,
            wireMaterial,
            storageCarrier,
            relayCarrier,
            terminalCarrier);
    this.runtimeConfigSource = dependencies.runtimeConfig();
    this.overlayConfigSource = dependencies.overlayConfig();
    this.storageChangeListener = dependencies.storageChangeListener();
    this.runtimeConfig = runtimeConfigSource.get();
    this.overlayConfig = overlayConfigSource.get();
    this.renderScheduler =
        new GuiRenderScheduler(
            registry, task -> Bukkit.getScheduler().runTask(plugin, task).getTaskId());
  }

  public JavaPlugin plugin() {
    return plugin;
  }

  public BossBarManager bossBarManager() {
    return bossBarManager.get();
  }

  public PlayerFeedback playerFeedback() {
    return playerFeedback.get();
  }

  public WirelessTerminalService wirelessService() {
    return wirelessService.get();
  }

  public BusService busService() {
    return busService.get();
  }

  public Lang lang() {
    return lang;
  }

  public void reconfigure() {
    runtimeConfig = runtimeConfigSource.get();
    overlayConfig = overlayConfigSource.get();
    restartSessionWatcher();
  }

  private void startSessionWatcher() {
    if (sessionTaskId != -1) return;
    long intervalTicks = runtimeConfig.sessionDeviceCheckIntervalTicks();
    sessionTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin,
                () -> {
                  WirelessTerminalService tickWireless = wirelessService.get();
                  Material tickStorageCarrier = storageCarrier.get();
                  List<GuiSession> snapshot = new ArrayList<>(registry.allSessions());
                  List<WirelessCloseRequest> toClose = new ArrayList<>();
                  List<Player> physicalToClose = new ArrayList<>();
                  for (GuiSession session : snapshot) {
                    if (!(session instanceof AbstractStorageSession storageSession)) continue;
                    if (!storageSession.isWireless()) {
                      if (sessionValidator.shouldClosePhysicalSession(storageSession)) {
                        physicalToClose.add(storageSession.getViewer());
                      }
                      continue;
                    }
                    if (tickWireless == null || tickStorageCarrier == null) continue;
                    GuiSessionValidator.WirelessValidationResult validation =
                        sessionValidator.wirelessValidation(storageSession, tickWireless);
                    if (!validation.isValid() && validation.player() != null) {
                      toClose.add(
                          new WirelessCloseRequest(validation.player(), validation.messageKey()));
                    }
                  }
                  if (!physicalToClose.isEmpty()) {
                    for (Player player : physicalToClose) {
                      forceCloseSession(player);
                    }
                  }
                  if (!toClose.isEmpty()) {
                    for (WirelessCloseRequest request : toClose) {
                      Player player = request.player();
                      if (player == null) {
                        continue;
                      }
                      if (player.isOnline()) {
                        forceCloseSession(player);
                        if (request.messageKey() != null) {
                          playerFeedback().error(player, request.messageKey());
                        }
                      } else {
                        closeSessionState(player, null);
                      }
                    }
                  }
                },
                intervalTicks,
                intervalTicks);
  }

  private void stopSessionWatcher() {
    if (sessionTaskId != -1) {
      Bukkit.getScheduler().cancelTask(sessionTaskId);
      sessionTaskId = -1;
    }
  }

  private void restartSessionWatcher() {
    stopSessionWatcher();
    startSessionWatcher();
  }

  public boolean openSession(
      Player player,
      StorageCache cache,
      StorageTier tier,
      Block terminal,
      Location storageLocation) {
    return openSessionInternal(
        player,
        cache,
        tier,
        terminal,
        storageLocation,
        false,
        false,
        false,
        SessionType.STORAGE,
        false);
  }

  public boolean openCraftingSession(
      Player player,
      StorageCache cache,
      StorageTier tier,
      Block terminal,
      Location storageLocation) {
    return openSessionInternal(
        player,
        cache,
        tier,
        terminal,
        storageLocation,
        false,
        false,
        false,
        SessionType.CRAFTING,
        false);
  }

  public boolean openDebugSession(
      Player player, StorageCache cache, StorageTier tier, boolean write) {
    return openSessionInternal(
        player, cache, tier, null, null, !write, true, write, SessionType.STORAGE, false);
  }

  public boolean openWirelessSession(
      Player player, StorageCache cache, StorageTier tier, Location storageLocation) {
    return openSessionInternal(
        player, cache, tier, null, storageLocation, false, false, false, SessionType.STORAGE, true);
  }

  private boolean openSessionInternal(
      Player player,
      StorageCache cache,
      StorageTier tier,
      Block terminal,
      Location storageLocation,
      boolean forceReadOnly,
      boolean bypassLock,
      boolean forceWrite,
      SessionType type,
      boolean wireless) {
    if (registry.containsViewer(player.getUniqueId())) {
      forceCloseSession(player);
    }
    boolean readOnly =
        forceReadOnly || (!forceWrite && registry.hasStorageSessions(cache.getStorageId()));
    if (forceWrite) {
      registry.makeForcedWriter(cache.getStorageId(), player.getUniqueId());
    }
    // Close peek bossbar if any before opening full GUI bar
    bossBarManager().remove(player);
    GuiSession session =
        createSession(type, player, cache, tier, terminal, storageLocation, readOnly, wireless);
    registry.register(session);
    cache.viewerOpened();
    player.openInventory(session.getInventory());
    session.render();
    if (storageLocation != null) {
      database.updatePlayerLastStorage(
          player.getUniqueId(),
          cache.getStorageId(),
          storageLocation.getWorld().getName(),
          storageLocation.getBlockX(),
          storageLocation.getBlockY(),
          storageLocation.getBlockZ());
    }
    return true;
  }

  public void closeSession(Player player) {
    if (closeSessionState(player, null)) {
      closeDialogQuietly(player);
    }
  }

  public void closeSession(Player player, GuiSession expectedSession) {
    if (closeSessionState(player, expectedSession)) {
      closeDialogQuietly(player);
    }
  }

  public void forceCloseSession(Player player) {
    if (player == null) return;
    closeSessionState(player, null);
    closeDialogQuietly(player);
    player.closeInventory();
  }

  private boolean closeSessionState(Player player, GuiSession expectedSession) {
    if (player == null) return false;
    if (expectedSession != null && registry.sessionFor(player.getUniqueId()) != expectedSession) {
      return false;
    }
    boolean changed = searchCoordinator.discard(player) != null;
    GuiSessionRegistry.Removal removal = registry.unregister(player, expectedSession);
    if (removal == null) {
      return changed;
    }
    GuiSession session = removal.session();
    changed = true;
    session.getCache().viewerClosed();
    session.onClose();
    if (session.getCache().isDirty() && !session.getCache().hasViewers()) {
      storageManager.flush(session.getCache());
    }
    if (session.type() == SessionType.CRAFTING) {
      if (!registry.hasCraftingSessions(session.getStorageId())) {
        CraftingState state = craftingStates.get(session.getStorageId());
        if (state != null) {
          state.clear();
        }
      }
    }
    if (removal.storageStillOpen()) {
      renderStorage(session.getStorageId(), SortEvent.DEPOSIT);
    }
    return changed;
  }

  public GuiSession sessionFor(Player player) {
    return registry.sessionFor(player.getUniqueId());
  }

  public boolean openSearch(Player player, SearchableSession parent) {
    if (parent == null) return false;
    if (!dialogSupported.getAsBoolean()) {
      playerFeedback().error(player, "error.search.dialogs_unsupported");
      return false;
    }
    SearchableSession existing = searchCoordinator.pendingFor(player);
    if (existing != null) {
      searchCoordinator.discard(player);
    }
    searchCoordinator.begin(player, parent);
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()) {
                searchCoordinator.discard(player);
                return;
              }
              if (!searchCoordinator.isPending(player, parent)) {
                return;
              }
              if (registry.sessionFor(player.getUniqueId()) != parent) {
                searchCoordinator.discard(player);
                return;
              }
              if (!sessionValidator.isSessionValid(parent)) {
                forceCloseSession(player);
                return;
              }
              try {
                player.showDialog(searchDialogService.buildDialog(player));
              } catch (RuntimeException | LinkageError e) {
                searchCoordinator.discard(player);
                throw e;
              }
            });
    return true;
  }

  public boolean isSearchCloseProtected(Player player, GuiSession parent) {
    return searchCoordinator.protectsClose(player, parent);
  }

  public void verifySearchProtectedClose(Player player, GuiSession parent) {
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (player == null) return;
              if (registry.sessionFor(player.getUniqueId()) != parent) return;
              if (!searchCoordinator.protectsClose(player, parent)) return;
              if (player.isOnline()
                  && player.getOpenInventory().getTopInventory().getHolder() == parent) {
                return;
              }
              closeSession(player, parent);
            },
            2L);
  }

  public void closeSearch(Player player, boolean reopenParent) {
    SearchableSession parent =
        reopenParent ? searchCoordinator.complete(player) : searchCoordinator.discard(player);
    if (parent == null) return;
    closeDialogQuietly(player);
    if (!reopenParent) return;
    reopenParentSearchFallback(player, parent);
  }

  public boolean hasPendingSearch(Player player) {
    return searchCoordinator.hasPending(player);
  }

  public void handleSearchInput(Player player, String query) {
    SearchableSession parent = searchCoordinator.complete(player);
    if (parent == null) return;
    if (query == null || query.isBlank()) {
      parent.clearSearch();
    } else {
      parent.setSearchQuery(query);
    }
    reopenParentSearchFallback(player, parent);
  }

  public void cancelSearch(Player player) {
    SearchableSession parent = searchCoordinator.complete(player);
    if (parent == null) return;
    reopenParentSearchFallback(player, parent);
  }

  public void clearPendingSearchIfParent(Player player, GuiSession parent) {
    searchCoordinator.discardIfParent(player, parent);
  }

  private void reopenParentSearchFallback(Player player, SearchableSession parent) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()) return;
              if (searchCoordinator.hasPending(player)) return;
              GuiSession current = registry.sessionFor(player.getUniqueId());
              if (current != parent) return;
              if (!sessionValidator.isSessionValid(parent)) {
                forceCloseSession(player);
                return;
              }
              player.openInventory(parent.getInventory());
              parent.render();
              Bukkit.getScheduler()
                  .runTaskLater(
                      plugin,
                      () -> searchCoordinator.clearCloseProtectionIfParent(player, parent),
                      2L);
            });
  }

  public Set<GuiSession> sessionsForStorage(String storageId) {
    return registry.sessionsForStorage(storageId);
  }

  public Collection<GuiSession> allSessions() {
    return registry.allSessions();
  }

  public void closeSessionsForTerminal(Block block) {
    for (GuiSession session : registry.sessionsForTerminal(block)) {
      forceCloseSession(session.getViewer());
    }
  }

  public void revalidateSessions() {
    for (GuiSession session : registry.allSessions()) {
      var termBlock = session.getTerminalBlock();
      if (termBlock == null) {
        continue;
      }
      if (termBlock.getType() != terminalCarrier.get()) {
        forceCloseSession(session.getViewer());
        continue;
      }
      if (!sessionValidator.isSessionValid(session)) {
        forceCloseSession(session.getViewer());
      }
    }
  }

  public void renderStorage(String storageId) {
    storageChangeListener.accept(storageId);
    renderScheduler.request(storageId);
  }

  public void renderStorage(String storageId, SortEvent event) {
    storageChangeListener.accept(storageId);
    renderScheduler.request(storageId, event);
  }

  public boolean isModeratorLocked(String storageId, UUID viewerId) {
    return registry.isModeratorLocked(storageId, viewerId);
  }

  public boolean forceWriterFromInfo(GuiSession session) {
    if (!registry.forceWriter(session)) {
      return false;
    }
    renderStorage(session.getStorageId(), SortEvent.NONE);
    return true;
  }

  public void updateSortMode(StorageCache cache, SortMode mode) {
    if (cache == null || mode == null) return;
    cache.setSortMode(mode);
    database.setStorageSortMode(cache.getStorageId(), mode.name());
    for (GuiSession session : registry.sessionsForStorage(cache.getStorageId())) {
      session.onSortEvent(SortEvent.DEPOSIT);
      if (session instanceof AbstractStorageSession storageSession) {
        storageSession.setSortMode(mode);
      }
    }
    renderStorage(cache.getStorageId(), SortEvent.NONE);
  }

  private GuiSession createSession(
      SessionType type,
      Player player,
      StorageCache cache,
      StorageTier tier,
      Block terminal,
      Location storageLocation,
      boolean readOnly,
      boolean wireless) {
    long timeoutMs = runtimeConfig.craftingConfirmTimeoutMs();
    if (type == SessionType.CRAFTING) {
      CraftingState state =
          craftingStates.computeIfAbsent(cache.getStorageId(), k -> new CraftingState());
      return new CraftingSession(
          player,
          cache,
          tier,
          lang,
          itemNameService,
          keys,
          terminal,
          storageLocation,
          this,
          readOnly,
          state,
          craftingRules.get(),
          titleFor(player, SessionType.CRAFTING),
          !resourceMode.getAsBoolean(),
          timeoutMs,
          cache.getSortMode(),
          wireless);
    }
    return new StorageSession(
        player,
        cache,
        tier,
        lang,
        itemNameService,
        keys,
        terminal,
        storageLocation,
        this,
        readOnly,
        titleFor(player, SessionType.STORAGE, wireless),
        !resourceMode.getAsBoolean(),
        timeoutMs,
        cache.getSortMode(),
        wireless);
  }

  private Component titleFor(Player viewer, SessionType type) {
    return titleFor(viewer, type, false);
  }

  private Component titleFor(Player viewer, SessionType type, boolean wireless) {
    boolean resourceMode = this.resourceMode.getAsBoolean();
    String overlayKey = GuiOverlayConfig.storageTerminalKey(type);
    String nameKey =
        type == SessionType.CRAFTING
            ? "item.crafting_terminal"
            : (wireless ? "item.wireless_terminal" : "item.terminal");
    Component name = ExortText.plain(lang.tr(viewer, nameKey));
    if (!resourceMode) {
      return name;
    }
    return GuiOverlayGlyphs.overlay(overlayKey, overlayConfig.storageTerminal(type), ExortLog::warn)
        .map(overlay -> ExortText.withPrefix(overlay, name))
        .orElse(name);
  }

  private void closeDialogQuietly(Player player) {
    if (player == null) return;
    try {
      player.closeDialog();
    } catch (Throwable ignored) {
      // Dialogs are available only on supported Paper versions.
    }
  }

  private record WirelessCloseRequest(Player player, String messageKey) {}
}
