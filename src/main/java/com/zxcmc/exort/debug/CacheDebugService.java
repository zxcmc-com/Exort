package com.zxcmc.exort.debug;

import com.zxcmc.exort.core.ExortPlugin;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CacheDebugService {
  public enum Mode {
    COMPACT,
    NORMAL,
    FULL;

    public static Mode fromString(String raw) {
      if (raw == null || raw.isBlank()) return NORMAL;
      try {
        return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
  }

  public enum EventType {
    LOAD(true),
    FLUSH(false),
    EVICT(true),
    ADD(false),
    REMOVE(false),
    REMOVE_ALL_OK(false),
    REMOVE_ALL_FAIL(true);

    private final boolean important;

    EventType(boolean important) {
      this.important = important;
    }

    public boolean isImportant() {
      return important;
    }
  }

  private static final long SUMMARY_INTERVAL_TICKS = 200L;
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private final ExortPlugin plugin;
  private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
  private volatile boolean consoleExplicit;
  private volatile Mode mode = Mode.NORMAL;
  private volatile String storageFilter;
  private volatile int summaryTaskId = -1;
  private final Summary summary = new Summary();
  private final Object summaryLock = new Object();

  public CacheDebugService(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public boolean isEnabled() {
    return consoleExplicit || !viewers.isEmpty();
  }

  public Mode getMode() {
    return mode;
  }

  public void start(CommandSender sender, Mode mode, UUID storageFilter) {
    if (mode != null) {
      this.mode = mode;
    }
    setStorageFilter(storageFilter);
    start(sender);
    updateSummaryTask();
  }

  public void start(CommandSender sender) {
    if (sender instanceof Player player) {
      viewers.add(player.getUniqueId());
    } else {
      consoleExplicit = true;
    }
    updateSummaryTask();
  }

  public void stop(CommandSender sender) {
    if (sender instanceof Player player) {
      viewers.remove(player.getUniqueId());
    } else {
      consoleExplicit = false;
    }
    if (!isEnabled()) {
      setStorageFilter(null);
    }
    updateSummaryTask();
  }

  public void record(EventType type, String storageId, String message, long amount) {
    if (!isEnabled() || type == null || message == null) return;
    if (!matchesFilter(storageId)) return;
    if (mode == Mode.COMPACT) {
      updateSummary(type, storageId, amount);
      return;
    }
    if (mode == Mode.NORMAL && !type.isImportant()) return;
    send(formatMessage(type, message));
  }

  public void record(EventType type, String storageId, String message) {
    record(type, storageId, message, 0L);
  }

  private boolean matchesFilter(String storageId) {
    String filter = storageFilter;
    if (filter == null || filter.isBlank()) return true;
    if (storageId == null) return false;
    return storageId.equalsIgnoreCase(filter);
  }

  public boolean shouldTrace(String storageId) {
    if (!isEnabled()) return false;
    if (mode != Mode.FULL) return false;
    return matchesFilter(storageId);
  }

  private void setStorageFilter(UUID storageFilter) {
    this.storageFilter = storageFilter == null ? null : storageFilter.toString();
  }

  private void updateSummaryTask() {
    boolean shouldRun = isEnabled() && mode == Mode.COMPACT;
    if (shouldRun && summaryTaskId == -1) {
      summaryTaskId =
          Bukkit.getScheduler()
              .runTaskTimer(
                  plugin, this::flushSummary, SUMMARY_INTERVAL_TICKS, SUMMARY_INTERVAL_TICKS)
              .getTaskId();
    } else if (!shouldRun && summaryTaskId != -1) {
      Bukkit.getScheduler().cancelTask(summaryTaskId);
      summaryTaskId = -1;
      resetSummary();
    }
  }

  private void updateSummary(EventType type, String storageId, long amount) {
    synchronized (summaryLock) {
      summary.record(type, storageId, amount);
    }
  }

  private void resetSummary() {
    synchronized (summaryLock) {
      summary.reset();
    }
  }

  private void flushSummary() {
    if (!isEnabled() || mode != Mode.COMPACT) return;
    Summary snapshot;
    synchronized (summaryLock) {
      if (summary.isEmpty()) return;
      snapshot = summary.copyAndReset();
    }
    Component line = formatSummary(snapshot);
    send(line);
  }

  private Component formatSummary(Summary snapshot) {
    String filter = storageFilter;
    String scope = filter == null ? "" : " storage=" + filter;
    long net = snapshot.addAmount - snapshot.removeAmount;
    NamedTextColor netColor = net >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
    Component base =
        prefix()
            .append(Component.text("cache summary (10s):", NamedTextColor.GOLD))
            .append(Component.text(" loads=" + snapshot.loads, NamedTextColor.GRAY))
            .append(Component.text(" flushes=" + snapshot.flushes, NamedTextColor.GRAY))
            .append(Component.text(" evicts=" + snapshot.evicts, NamedTextColor.GRAY))
            .append(Component.text(" addOps=" + snapshot.addOps, NamedTextColor.GRAY))
            .append(Component.text(" remOps=" + snapshot.removeOps, NamedTextColor.GRAY))
            .append(Component.text(" net=" + (net >= 0 ? "+" : "") + net, netColor))
            .append(Component.text(" storages=" + snapshot.storages.size(), NamedTextColor.GRAY))
            .append(Component.text(" fail=" + snapshot.removeAllFail, NamedTextColor.GRAY));
    if (snapshot.lastEvictStorageId != null) {
      base =
          base.append(Component.text(" lastEvict=", NamedTextColor.GRAY))
              .append(Component.text(snapshot.lastEvictStorageId, NamedTextColor.DARK_PURPLE))
              .append(Component.text(" idleMs=" + snapshot.lastEvictIdleMs, NamedTextColor.GRAY));
    }
    if (!scope.isBlank()) {
      base = base.append(Component.text(scope, NamedTextColor.GRAY));
    }
    return base;
  }

  private void send(Component line) {
    if (line == null) return;
    String legacy = LEGACY.serialize(line);
    if (consoleExplicit || !viewers.isEmpty()) {
      Bukkit.getConsoleSender().sendMessage(legacy);
    }
    for (UUID viewerId : viewers) {
      Player player = Bukkit.getPlayer(viewerId);
      if (player == null || !player.isOnline()) {
        viewers.remove(viewerId);
        continue;
      }
      player.sendMessage(legacy);
    }
  }

  private Component formatMessage(EventType type, String message) {
    return prefix().append(Component.text(message, color(type)));
  }

  private Component prefix() {
    return Component.text("[", NamedTextColor.DARK_GRAY)
        .append(Component.text("Exort", NamedTextColor.AQUA))
        .append(Component.text("] ", NamedTextColor.DARK_GRAY));
  }

  private NamedTextColor color(EventType type) {
    return switch (type) {
      case LOAD -> NamedTextColor.AQUA;
      case FLUSH -> NamedTextColor.YELLOW;
      case EVICT -> NamedTextColor.DARK_PURPLE;
      case ADD -> NamedTextColor.GREEN;
      case REMOVE -> NamedTextColor.RED;
      case REMOVE_ALL_OK -> NamedTextColor.DARK_GREEN;
      case REMOVE_ALL_FAIL -> NamedTextColor.DARK_RED;
    };
  }

  private static final class Summary {
    private long loads;
    private long flushes;
    private long evicts;
    private long addOps;
    private long removeOps;
    private long addAmount;
    private long removeAmount;
    private long removeAllFail;
    private String lastEvictStorageId;
    private long lastEvictIdleMs;
    private final Set<String> storages = new HashSet<>();

    private void record(EventType type, String storageId, long amount) {
      if (storageId != null) {
        storages.add(storageId);
      }
      switch (type) {
        case LOAD -> loads++;
        case FLUSH -> flushes++;
        case EVICT -> {
          evicts++;
          lastEvictStorageId = storageId;
          lastEvictIdleMs = Math.max(0L, amount);
        }
        case ADD -> {
          addOps++;
          addAmount += Math.max(0L, amount);
        }
        case REMOVE -> {
          removeOps++;
          removeAmount += Math.max(0L, amount);
        }
        case REMOVE_ALL_FAIL -> removeAllFail++;
        default -> {}
      }
    }

    private boolean isEmpty() {
      return loads == 0
          && flushes == 0
          && evicts == 0
          && addOps == 0
          && removeOps == 0
          && removeAllFail == 0;
    }

    private Summary copyAndReset() {
      Summary copy = new Summary();
      copy.loads = loads;
      copy.flushes = flushes;
      copy.evicts = evicts;
      copy.addOps = addOps;
      copy.removeOps = removeOps;
      copy.addAmount = addAmount;
      copy.removeAmount = removeAmount;
      copy.removeAllFail = removeAllFail;
      copy.lastEvictStorageId = lastEvictStorageId;
      copy.lastEvictIdleMs = lastEvictIdleMs;
      copy.storages.addAll(storages);
      reset();
      return copy;
    }

    private void reset() {
      loads = 0;
      flushes = 0;
      evicts = 0;
      addOps = 0;
      removeOps = 0;
      addAmount = 0;
      removeAmount = 0;
      removeAllFail = 0;
      lastEvictStorageId = null;
      lastEvictIdleMs = 0L;
      storages.clear();
    }
  }
}
