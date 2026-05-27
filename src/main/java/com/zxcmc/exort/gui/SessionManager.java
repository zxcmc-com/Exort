package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.core.text.ExortText;
import com.zxcmc.exort.core.text.GuiOverlayGlyphs;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SessionManager {
  private final Map<UUID, GuiSession> byPlayer = new HashMap<>();
  private final Map<String, LinkedHashSet<GuiSession>> byStorage = new HashMap<>();
  private final Map<TerminalPos, Set<GuiSession>> byTerminal = new HashMap<>();
  private final Map<UUID, SearchableSession> pendingSearch = new HashMap<>();
  private final Set<UUID> switchingToSearch = new HashSet<>();
  private final Map<String, CraftingState> craftingStates = new HashMap<>();
  private final Map<String, UUID> forcedWriters = new HashMap<>();
  private final Set<String> pendingRenders = new HashSet<>();
  private final Map<String, SortEvent> pendingSortEvents = new HashMap<>();
  private final ExortPlugin plugin;
  private int sessionTaskId = -1;
  private int renderTaskId = -1;

  public SessionManager(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public ExortPlugin getPlugin() {
    return plugin;
  }

  public void reconfigure() {
    restartSessionWatcher();
  }

  private void startSessionWatcher() {
    if (sessionTaskId != -1) return;
    long intervalTicks =
        Math.max(1L, plugin.getConfig().getLong("session.deviceCheckIntervalTicks", 5L));
    sessionTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin,
                () -> {
                  WirelessTerminalService tickWireless = plugin.getWirelessService();
                  Material tickStorageCarrier = plugin.getStorageCarrier();
                  Material tickTerminalCarrier = plugin.getTerminalCarrier();
                  double maxDeviceDistanceSquared = maxDeviceDistanceSquared();
                  List<GuiSession> snapshot = new ArrayList<>(byPlayer.values());
                  List<WirelessCloseRequest> toClose = new ArrayList<>();
                  List<Player> physicalToClose = new ArrayList<>();
                  for (GuiSession session : snapshot) {
                    if (!(session instanceof AbstractStorageSession storageSession)) continue;
                    if (!storageSession.isWireless()) {
                      if (shouldClosePhysicalSession(
                          storageSession, tickTerminalCarrier, maxDeviceDistanceSquared)) {
                        physicalToClose.add(storageSession.getViewer());
                      }
                      continue;
                    }
                    if (tickWireless == null || tickStorageCarrier == null) continue;
                    Player p = storageSession.getViewer();
                    if (p == null || !p.isOnline()) {
                      if (p != null) {
                        toClose.add(new WirelessCloseRequest(p, null));
                      }
                      continue;
                    }
                    Location anchor = storageSession.getStorageLocation();
                    if (!tickWireless.isEnabled()) {
                      toClose.add(new WirelessCloseRequest(p, "message.wireless.disabled"));
                      continue;
                    }
                    if (anchor == null || !anchor.isWorldLoaded()) {
                      toClose.add(new WirelessCloseRequest(p, "message.wireless.missing_storage"));
                      continue;
                    }
                    if (!tickWireless.inRange(anchor, p.getLocation())) {
                      toClose.add(new WirelessCloseRequest(p, "message.wireless.out_of_range"));
                      continue;
                    }
                    if (!Carriers.matchesCarrier(anchor.getBlock(), tickStorageCarrier)) {
                      toClose.add(new WirelessCloseRequest(p, "message.wireless.missing_storage"));
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
                          plugin.getPlayerFeedback().error(player, request.messageKey());
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
    if (byPlayer.containsKey(player.getUniqueId())) {
      forceCloseSession(player);
    }
    LinkedHashSet<GuiSession> sessions =
        byStorage.computeIfAbsent(cache.getStorageId(), k -> new LinkedHashSet<>());
    boolean readOnly = forceReadOnly || (!forceWrite && !sessions.isEmpty());
    if (forceWrite) {
      forcedWriters.put(cache.getStorageId(), player.getUniqueId());
      for (GuiSession session : sessions) {
        session.setReadOnly(true);
      }
    }
    // Close peek bossbar if any before opening full GUI bar
    plugin.getBossBarManager().remove(player);
    GuiSession session =
        createSession(type, player, cache, tier, terminal, storageLocation, readOnly, wireless);
    byPlayer.put(player.getUniqueId(), session);
    sessions.add(session);
    if (terminal != null) {
      TerminalPos pos = TerminalPos.of(terminal);
      byTerminal.computeIfAbsent(pos, k -> new HashSet<>()).add(session);
    }
    cache.viewerOpened();
    player.openInventory(session.getInventory());
    session.render();
    if (storageLocation != null) {
      plugin
          .getDatabase()
          .updatePlayerLastStorage(
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
    if (expectedSession != null && byPlayer.get(player.getUniqueId()) != expectedSession) {
      return false;
    }
    boolean changed = discardSearchState(player) != null;
    GuiSession session = byPlayer.remove(player.getUniqueId());
    if (session != null) {
      changed = true;
      LinkedHashSet<GuiSession> set = byStorage.get(session.getStorageId());
      boolean wasWriter = !session.isReadOnly();
      if (set != null) {
        set.remove(session);
        if (set.isEmpty()) {
          byStorage.remove(session.getStorageId());
        }
      }
      UUID forced = forcedWriters.get(session.getStorageId());
      if (forced != null && forced.equals(session.getViewer().getUniqueId())) {
        forcedWriters.remove(session.getStorageId());
        promoteWriter(session.getStorageId());
      } else if (wasWriter && (forced == null || set == null || set.isEmpty())) {
        promoteWriter(session.getStorageId());
      }
      Block term = session.getTerminalBlock();
      if (term != null) {
        TerminalPos pos = TerminalPos.of(term);
        Set<GuiSession> termSet = byTerminal.get(pos);
        if (termSet != null) {
          termSet.remove(session);
          if (termSet.isEmpty()) {
            byTerminal.remove(pos);
          }
        }
      }
      session.getCache().viewerClosed();
      session.onClose();
      if (session.getCache().isDirty() && !session.getCache().hasViewers()) {
        plugin.getStorageManager().flush(session.getCache());
      }
      if (session.type() == SessionType.CRAFTING) {
        if (!hasCraftingSessions(session.getStorageId())) {
          CraftingState state = craftingStates.get(session.getStorageId());
          if (state != null) {
            state.clear();
          }
        }
      }
      if (set != null && !set.isEmpty()) {
        renderStorage(session.getStorageId(), SortEvent.DEPOSIT);
      }
    }
    return changed;
  }

  public GuiSession sessionFor(Player player) {
    return byPlayer.get(player.getUniqueId());
  }

  public boolean openSearch(Player player, SearchableSession parent) {
    if (parent == null) return false;
    if (!plugin.isDialogSupported()) {
      plugin.getPlayerFeedback().error(player, "error.search.dialogs_unsupported");
      return false;
    }
    SearchableSession existing = pendingSearch.get(player.getUniqueId());
    if (existing != null) {
      closeSearch(player, false);
    }
    pendingSearch.put(player.getUniqueId(), parent);
    switchingToSearch.add(player.getUniqueId());
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              try {
                if (!player.isOnline()) {
                  discardSearchState(player);
                  return;
                }
                if (pendingSearch.get(player.getUniqueId()) != parent) {
                  return;
                }
                if (byPlayer.get(player.getUniqueId()) != parent) {
                  discardSearchState(player);
                  return;
                }
                if (!isSessionValid(parent)) {
                  forceCloseSession(player);
                  return;
                }
                player.showDialog(plugin.getSearchDialogService().buildDialog());
              } finally {
                Bukkit.getScheduler()
                    .runTask(plugin, () -> switchingToSearch.remove(player.getUniqueId()));
              }
            });
    return true;
  }

  public boolean isSwitchingToSearch(Player player) {
    return switchingToSearch.contains(player.getUniqueId());
  }

  public void closeSearch(Player player, boolean reopenParent) {
    SearchableSession parent = discardSearchState(player);
    if (parent == null) return;
    closeDialogQuietly(player);
    if (!reopenParent) return;
    reopenParentSearchFallback(player, parent);
  }

  public boolean hasPendingSearch(Player player) {
    return pendingSearch.containsKey(player.getUniqueId());
  }

  public void handleSearchInput(Player player, String query) {
    SearchableSession parent = discardSearchState(player);
    if (parent == null) return;
    if (query == null || query.isBlank()) {
      parent.clearSearch();
    } else {
      parent.setSearchQuery(query);
    }
    reopenParentSearchFallback(player, parent);
  }

  public void cancelSearch(Player player) {
    SearchableSession parent = discardSearchState(player);
    if (parent == null) return;
    reopenParentSearchFallback(player, parent);
  }

  public void clearPendingSearchIfParent(Player player, GuiSession parent) {
    if (player == null) return;
    if (!(parent instanceof SearchableSession searchable)) return;
    if (pendingSearch.get(player.getUniqueId()) != searchable) return;
    discardSearchState(player);
  }

  private void reopenParentSearchFallback(Player player, SearchableSession parent) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()) return;
              if (pendingSearch.containsKey(player.getUniqueId())) return;
              GuiSession current = byPlayer.get(player.getUniqueId());
              if (current != parent) return;
              if (!isSessionValid(parent)) {
                forceCloseSession(player);
                return;
              }
              player.openInventory(parent.getInventory());
              parent.render();
            });
  }

  public Set<GuiSession> sessionsForStorage(String storageId) {
    Set<GuiSession> sessions = byStorage.get(storageId);
    return sessions == null ? Set.of() : Set.copyOf(sessions);
  }

  public Collection<GuiSession> allSessions() {
    return Collections.unmodifiableCollection(byPlayer.values());
  }

  public void closeSessionsForTerminal(Block block) {
    TerminalPos pos = TerminalPos.of(block);
    Set<GuiSession> sessions = byTerminal.getOrDefault(pos, Collections.emptySet());
    for (GuiSession session : new ArrayList<>(sessions)) {
      forceCloseSession(session.getViewer());
    }
  }

  public void revalidateSessions() {
    for (GuiSession session : new ArrayList<>(byPlayer.values())) {
      var termBlock = session.getTerminalBlock();
      if (termBlock == null) {
        continue;
      }
      if (termBlock.getType() != plugin.getTerminalCarrier()) {
        forceCloseSession(session.getViewer());
        continue;
      }
      var link =
          TerminalLinkFinder.find(
              termBlock,
              plugin.getKeys(),
              plugin,
              plugin.getWireLimit(),
              plugin.getWireHardCap(),
              plugin.getWireMaterial(),
              plugin.getStorageCarrier());
      if (link.count() != 1
          || link.data() == null
          || !session.getStorageId().equals(link.data().storageId())) {
        forceCloseSession(session.getViewer());
      }
    }
  }

  public void renderStorage(String storageId) {
    renderStorage(storageId, SortEvent.NONE);
  }

  public void renderStorage(String storageId, SortEvent event) {
    if (storageId == null) return;
    if (event != SortEvent.NONE) {
      pendingSortEvents.put(storageId, event);
    }
    pendingRenders.add(storageId);
    scheduleRenderFlush();
  }

  public boolean isModeratorLocked(String storageId, UUID viewerId) {
    UUID forced = forcedWriters.get(storageId);
    return forced != null && !forced.equals(viewerId);
  }

  public boolean forceWriterFromInfo(GuiSession session) {
    if (session == null) return false;
    String storageId = session.getStorageId();
    if (isModeratorLocked(storageId, session.getViewer().getUniqueId())) {
      return false;
    }
    LinkedHashSet<GuiSession> set = byStorage.get(storageId);
    if (set == null || set.isEmpty()) return false;
    for (GuiSession s : set) {
      s.setReadOnly(s != session);
    }
    session.setReadOnly(false);
    renderStorage(storageId, SortEvent.NONE);
    return true;
  }

  public void updateSortMode(StorageCache cache, SortMode mode) {
    if (cache == null || mode == null) return;
    cache.setSortMode(mode);
    plugin.getDatabase().setStorageSortMode(cache.getStorageId(), mode.name());
    Set<GuiSession> sessions = byStorage.getOrDefault(cache.getStorageId(), new LinkedHashSet<>());
    for (GuiSession session : sessions) {
      session.onSortEvent(SortEvent.DEPOSIT);
      if (session instanceof AbstractStorageSession storageSession) {
        storageSession.setSortMode(mode);
      }
    }
    renderStorage(cache.getStorageId(), SortEvent.NONE);
  }

  private void promoteWriter(String storageId) {
    LinkedHashSet<GuiSession> set = byStorage.get(storageId);
    if (set == null || set.isEmpty()) return;
    GuiSession next = set.iterator().next();
    next.setReadOnly(false);
    next.render();
  }

  private void scheduleRenderFlush() {
    if (renderTaskId != -1) return;
    renderTaskId =
        Bukkit.getScheduler()
            .runTask(
                plugin,
                () -> {
                  renderTaskId = -1;
                  flushRenders();
                })
            .getTaskId();
  }

  private void flushRenders() {
    if (pendingRenders.isEmpty()) {
      pendingSortEvents.clear();
      return;
    }
    Set<String> storageIds = new HashSet<>(pendingRenders);
    Map<String, SortEvent> sortEvents = new HashMap<>(pendingSortEvents);
    pendingRenders.clear();
    pendingSortEvents.clear();
    for (String storageId : storageIds) {
      Set<GuiSession> sessions = byStorage.getOrDefault(storageId, new LinkedHashSet<>());
      if (sessions.isEmpty()) continue;
      SortEvent event = sortEvents.getOrDefault(storageId, SortEvent.NONE);
      for (GuiSession session : new ArrayList<>(sessions)) {
        session.onSortEvent(event);
        session.render();
      }
    }
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
    long timeoutMs =
        Math.max(0L, plugin.getConfig().getLong("crafting.confirmTimeoutSeconds", 10) * 1000L);
    if (type == SessionType.CRAFTING) {
      CraftingState state =
          craftingStates.computeIfAbsent(cache.getStorageId(), k -> new CraftingState());
      return new CraftingSession(
          player,
          cache,
          tier,
          plugin.getLang(),
          plugin.getItemNameService(),
          terminal,
          storageLocation,
          this,
          readOnly,
          state,
          plugin.getCraftingRules(),
          titleFor(SessionType.CRAFTING),
          !plugin.isResourceMode(),
          timeoutMs,
          cache.getSortMode(),
          wireless);
    }
    return new StorageSession(
        player,
        cache,
        tier,
        plugin.getLang(),
        plugin.getItemNameService(),
        terminal,
        storageLocation,
        this,
        readOnly,
        titleFor(SessionType.STORAGE, wireless),
        !plugin.isResourceMode(),
        timeoutMs,
        cache.getSortMode(),
        wireless);
  }

  private Component titleFor(SessionType type) {
    return titleFor(type, false);
  }

  private Component titleFor(SessionType type, boolean wireless) {
    boolean resourceMode = plugin.isResourceMode();
    String overlayKey =
        type == SessionType.CRAFTING
            ? "resourceMode.craftingTerminal.gui.overlayTexture"
            : "resourceMode.terminal.gui.overlayTexture";
    String nameKey =
        type == SessionType.CRAFTING
            ? "item.crafting_terminal"
            : (wireless ? "item.wireless_terminal" : "item.terminal");
    Component name = ExortText.plain(plugin.getLang().tr(nameKey));
    if (!resourceMode) {
      return name;
    }
    String defaultOverlay = type == SessionType.CRAFTING ? "gui/crafting" : "gui/inventory";
    return GuiOverlayGlyphs.overlay(
            overlayKey, plugin.getConfig().getString(overlayKey, defaultOverlay), ExortLog::warn)
        .map(overlay -> ExortText.withPrefix(overlay, name))
        .orElse(name);
  }

  private boolean hasCraftingSessions(String storageId) {
    LinkedHashSet<GuiSession> set = byStorage.get(storageId);
    if (set == null || set.isEmpty()) return false;
    for (GuiSession session : set) {
      if (session.type() == SessionType.CRAFTING) return true;
    }
    return false;
  }

  private boolean isSessionValid(GuiSession session) {
    Block termBlock = session.getTerminalBlock();
    if (termBlock == null) {
      return true;
    }
    if (termBlock.getType() != plugin.getTerminalCarrier()) {
      return false;
    }
    var link =
        TerminalLinkFinder.find(
            termBlock,
            plugin.getKeys(),
            plugin,
            plugin.getWireLimit(),
            plugin.getWireHardCap(),
            plugin.getWireMaterial(),
            plugin.getStorageCarrier());
    return link.count() == 1
        && link.data() != null
        && session.getStorageId().equals(link.data().storageId());
  }

  private boolean shouldClosePhysicalSession(
      AbstractStorageSession session, Material terminalCarrier, double maxDistanceSquared) {
    Player player = session.getViewer();
    if (player == null || !player.isOnline()) return true;
    Block terminal = session.getTerminalBlock();
    if (terminal == null) return false;
    if (terminalCarrier == null) return false;
    if (!isBlockChunkLoaded(terminal)) return true;
    if (!Carriers.matchesCarrier(terminal, terminalCarrier)) return true;
    return isOutOfDeviceRange(player, terminal, maxDistanceSquared);
  }

  private boolean isOutOfDeviceRange(Player player, Block block, double maxDistanceSquared) {
    if (player.getWorld() == null || block.getWorld() == null) return true;
    if (!player.getWorld().getUID().equals(block.getWorld().getUID())) return true;
    Location playerLocation = player.getLocation();
    double dx = playerLocation.getX() - (block.getX() + 0.5D);
    double dy = playerLocation.getY() - (block.getY() + 0.5D);
    double dz = playerLocation.getZ() - (block.getZ() + 0.5D);
    return dx * dx + dy * dy + dz * dz > maxDistanceSquared;
  }

  private boolean isBlockChunkLoaded(Block block) {
    return block.getWorld() != null
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private double maxDeviceDistanceSquared() {
    double distance =
        Math.max(1.0D, plugin.getConfig().getDouble("session.maxDeviceDistanceBlocks", 8.0D));
    return distance * distance;
  }

  private SearchableSession discardSearchState(Player player) {
    if (player == null) return null;
    switchingToSearch.remove(player.getUniqueId());
    return pendingSearch.remove(player.getUniqueId());
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

  private record TerminalPos(UUID world, int x, int y, int z) {
    static TerminalPos of(Block block) {
      return new TerminalPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
