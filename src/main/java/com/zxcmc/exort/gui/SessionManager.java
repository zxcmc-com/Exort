package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.*;
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
  private int wirelessTaskId = -1;
  private int renderTaskId = -1;

  public SessionManager(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public ExortPlugin getPlugin() {
    return plugin;
  }

  public void reconfigure() {
    restartWirelessWatcher();
  }

  private void startWirelessWatcher() {
    WirelessTerminalService wirelessService = plugin.getWirelessService();
    Material storageCarrier = plugin.getStorageCarrier();
    if (wirelessService == null || storageCarrier == null) return;
    if (wirelessTaskId != -1) return;
    wirelessTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin,
                () -> {
                  WirelessTerminalService tickWireless = plugin.getWirelessService();
                  Material tickCarrier = plugin.getStorageCarrier();
                  if (tickWireless == null || tickCarrier == null) return;
                  List<GuiSession> snapshot = new ArrayList<>(byPlayer.values());
                  Set<UUID> toClose = new HashSet<>();
                  for (GuiSession session : snapshot) {
                    if (!(session instanceof AbstractStorageSession storageSession)) continue;
                    if (!storageSession.isWireless()) continue;
                    Player p = storageSession.getViewer();
                    if (p == null || !p.isOnline()) {
                      if (p != null) {
                        toClose.add(p.getUniqueId());
                      }
                      continue;
                    }
                    Location anchor = storageSession.getStorageLocation();
                    if (!tickWireless.isEnabled()) {
                      plugin
                          .getBossBarManager()
                          .showError(
                              p,
                              plugin.getLang().tr("message.wireless.disabled"),
                              plugin.getStoragePeekTicks());
                      toClose.add(p.getUniqueId());
                      continue;
                    }
                    if (anchor == null || !anchor.isWorldLoaded()) {
                      plugin
                          .getBossBarManager()
                          .showError(
                              p,
                              plugin.getLang().tr("message.wireless.missing_storage"),
                              plugin.getStoragePeekTicks());
                      toClose.add(p.getUniqueId());
                      continue;
                    }
                    if (!tickWireless.inRange(anchor, p.getLocation())) {
                      plugin
                          .getBossBarManager()
                          .showError(
                              p,
                              plugin.getLang().tr("message.wireless.out_of_range"),
                              plugin.getStoragePeekTicks());
                      toClose.add(p.getUniqueId());
                      continue;
                    }
                    if (!Carriers.matchesCarrier(anchor.getBlock(), tickCarrier)) {
                      plugin
                          .getBossBarManager()
                          .showError(
                              p,
                              plugin.getLang().tr("message.wireless.missing_storage"),
                              plugin.getStoragePeekTicks());
                      toClose.add(p.getUniqueId());
                    }
                  }
                  if (!toClose.isEmpty()) {
                    for (UUID playerId : toClose) {
                      Player player = Bukkit.getPlayer(playerId);
                      if (player != null) {
                        player.closeInventory();
                      }
                    }
                  }
                },
                5L,
                5L);
  }

  private void stopWirelessWatcher() {
    if (wirelessTaskId != -1) {
      Bukkit.getScheduler().cancelTask(wirelessTaskId);
      wirelessTaskId = -1;
    }
  }

  private void restartWirelessWatcher() {
    stopWirelessWatcher();
    startWirelessWatcher();
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
    closeSearch(player, false);
    GuiSession session = byPlayer.remove(player.getUniqueId());
    if (session != null) {
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
  }

  public GuiSession sessionFor(Player player) {
    return byPlayer.get(player.getUniqueId());
  }

  public boolean openSearch(Player player, SearchableSession parent) {
    if (parent == null) return false;
    if (!plugin.isDialogSupported()) {
      plugin
          .getBossBarManager()
          .showError(player, plugin.getLang().tr("error.search.dialogs_unsupported"), 100);
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
              player.closeInventory();
              player.showDialog(plugin.getSearchDialogService().buildDialog());
              switchingToSearch.remove(player.getUniqueId());
            });
    return true;
  }

  public boolean isSwitchingToSearch(Player player) {
    return switchingToSearch.contains(player.getUniqueId());
  }

  public void closeSearch(Player player, boolean reopenParent) {
    SearchableSession parent = pendingSearch.remove(player.getUniqueId());
    if (parent == null) return;
    switchingToSearch.remove(player.getUniqueId());
    try {
      player.closeDialog();
    } catch (Throwable ignored) {
      // Older servers may not support dialogs; inventory will already be closed.
    }
    if (!reopenParent) return;
    reopenParentSearchFallback(player, parent);
  }

  public boolean hasPendingSearch(Player player) {
    return pendingSearch.containsKey(player.getUniqueId());
  }

  public void handleSearchInput(Player player, String query) {
    SearchableSession parent = pendingSearch.remove(player.getUniqueId());
    switchingToSearch.remove(player.getUniqueId());
    if (parent == null) return;
    if (query == null || query.isBlank()) {
      parent.clearSearch();
    } else {
      parent.setSearchQuery(query);
    }
    reopenParentSearchFallback(player, parent);
  }

  public void cancelSearch(Player player) {
    SearchableSession parent = pendingSearch.remove(player.getUniqueId());
    switchingToSearch.remove(player.getUniqueId());
    if (parent == null) return;
    reopenParentSearchFallback(player, parent);
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
                player.closeInventory();
                return;
              }
              player.openInventory(parent.getInventory());
              parent.render();
            });
  }

  public Set<GuiSession> sessionsForStorage(String storageId) {
    return byStorage.getOrDefault(storageId, new LinkedHashSet<>());
  }

  public Collection<GuiSession> allSessions() {
    return Collections.unmodifiableCollection(byPlayer.values());
  }

  public void closeSessionsForTerminal(Block block) {
    TerminalPos pos = TerminalPos.of(block);
    Set<GuiSession> sessions = byTerminal.getOrDefault(pos, Collections.emptySet());
    for (GuiSession session : new ArrayList<>(sessions)) {
      closeSearch(session.getViewer(), false);
      session.getViewer().closeInventory();
    }
  }

  public void revalidateSessions() {
    for (GuiSession session : new ArrayList<>(byPlayer.values())) {
      var termBlock = session.getTerminalBlock();
      if (termBlock == null) {
        continue;
      }
      if (termBlock.getType() != plugin.getTerminalCarrier()) {
        closeSearch(session.getViewer(), false);
        session.getViewer().closeInventory();
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
        closeSearch(session.getViewer(), false);
        session.getViewer().closeInventory();
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

  private net.kyori.adventure.text.Component titleFor(SessionType type) {
    return titleFor(type, false);
  }

  private net.kyori.adventure.text.Component titleFor(SessionType type, boolean wireless) {
    boolean resourceMode = plugin.isResourceMode();
    String prefixKey =
        type == SessionType.CRAFTING
            ? "resourceMode.craftingTerminal.gui.prefix"
            : "resourceMode.terminal.gui.prefix";
    String fontKey =
        type == SessionType.CRAFTING
            ? "resourceMode.craftingTerminal.gui.font"
            : "resourceMode.terminal.gui.font";
    String nameKey =
        type == SessionType.CRAFTING
            ? "item.crafting_terminal"
            : (wireless ? "item.wireless_terminal" : "item.terminal");
    String prefix =
        resourceMode
            ? plugin
                .getConfig()
                .getString(prefixKey, type == SessionType.CRAFTING ? "§f૱" : "§fᾖ")
            : "";
    String font =
        resourceMode ? plugin.getConfig().getString(fontKey, "exort:default") : "minecraft:default";
    net.kyori.adventure.text.Component name =
        net.kyori.adventure.text.Component.text(plugin.getLang().tr(nameKey));
    if (prefix == null || prefix.isEmpty()) {
      return name;
    }
    net.kyori.adventure.text.Component prefixComponent =
        net.kyori.adventure.text.Component.text(prefix);
    try {
      prefixComponent = prefixComponent.font(net.kyori.adventure.key.Key.key(font));
    } catch (IllegalArgumentException ignored) {
      // Ignore invalid font id and use default.
    }
    return prefixComponent.append(name);
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

  private record TerminalPos(UUID world, int x, int y, int z) {
    static TerminalPos of(Block block) {
      return new TerminalPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
