package com.zxcmc.exort.debug;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.text.ExortText;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WorldEditDebugService {
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

  private static final long SUMMARY_INTERVAL_TICKS = 200L;
  private final ExortPlugin plugin;
  private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
  private volatile boolean consoleExplicit;
  private volatile Mode mode = Mode.NORMAL;
  private volatile int summaryTaskId = -1;
  private final Summary summary = new Summary();
  private final Object summaryLock = new Object();

  public WorldEditDebugService(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public boolean isEnabled() {
    return consoleExplicit || !viewers.isEmpty();
  }

  public Mode getMode() {
    return mode;
  }

  public void start(CommandSender sender, Mode mode) {
    if (mode != null) {
      this.mode = mode;
    }
    start(sender);
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
    updateSummaryTask();
  }

  public void recordEvent(String message, NamedTextColor color) {
    if (!isEnabled() || message == null) return;
    if (mode == Mode.COMPACT) return;
    send(formatMessage(message, color == null ? NamedTextColor.GRAY : color));
  }

  public void incSessions() {
    recordCompact(summary -> summary.sessions++);
  }

  public void incUpdatesQueued() {
    recordCompact(summary -> summary.updatesQueued++);
  }

  public void incUpdatesApplied() {
    recordCompact(summary -> summary.updatesApplied++);
  }

  public void incUpdatesRetried() {
    recordCompact(summary -> summary.updatesRetried++);
  }

  public void incUpdatesSkipped() {
    recordCompact(summary -> summary.updatesSkipped++);
  }

  public void recordSetBlocks(int blocks) {
    recordCompact(
        summary -> {
          summary.setBlocksCalls++;
          summary.blocksProcessed += blocks;
        });
  }

  public void recordSetBlock(boolean added, boolean cleared) {
    recordCompact(
        summary -> {
          summary.setBlockCalls++;
          if (added) {
            summary.markerAdds++;
          } else if (cleared) {
            summary.markerClears++;
          }
        });
  }

  private void recordCompact(java.util.function.Consumer<Summary> consumer) {
    if (!isEnabled() || mode != Mode.COMPACT || consumer == null) return;
    synchronized (summaryLock) {
      consumer.accept(summary);
    }
  }

  public boolean isFull() {
    return isEnabled() && mode == Mode.FULL;
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
    send(formatSummary(snapshot));
  }

  private Component formatSummary(Summary snapshot) {
    return prefix()
        .append(Component.text("we summary (10s):", NamedTextColor.GOLD))
        .append(Component.text(" sessions=" + snapshot.sessions, NamedTextColor.GRAY))
        .append(Component.text(" setBlocks=" + snapshot.setBlocksCalls, NamedTextColor.GRAY))
        .append(Component.text(" blocks=" + snapshot.blocksProcessed, NamedTextColor.GRAY))
        .append(Component.text(" setBlock=" + snapshot.setBlockCalls, NamedTextColor.GRAY))
        .append(Component.text(" add=" + snapshot.markerAdds, NamedTextColor.GREEN))
        .append(Component.text(" clear=" + snapshot.markerClears, NamedTextColor.RED))
        .append(Component.text(" queued=" + snapshot.updatesQueued, NamedTextColor.AQUA))
        .append(Component.text(" applied=" + snapshot.updatesApplied, NamedTextColor.GRAY))
        .append(Component.text(" retry=" + snapshot.updatesRetried, NamedTextColor.YELLOW))
        .append(Component.text(" skip=" + snapshot.updatesSkipped, NamedTextColor.DARK_RED));
  }

  private void send(Component line) {
    if (line == null) return;
    if (consoleExplicit || !viewers.isEmpty()) {
      Bukkit.getConsoleSender().sendMessage(line);
    }
    for (UUID viewerId : viewers) {
      Player player = Bukkit.getPlayer(viewerId);
      if (player == null || !player.isOnline()) {
        viewers.remove(viewerId);
        continue;
      }
      player.sendMessage(line);
    }
  }

  private Component formatMessage(String message, NamedTextColor color) {
    return prefix().append(Component.text(message, color));
  }

  private Component prefix() {
    return Component.text("[Exort] ", ExortText.PREFIX);
  }

  public static final class Summary {
    private long sessions;
    private long setBlocksCalls;
    private long blocksProcessed;
    private long setBlockCalls;
    private long markerAdds;
    private long markerClears;
    private long updatesQueued;
    private long updatesApplied;
    private long updatesRetried;
    private long updatesSkipped;

    private void reset() {
      sessions = 0;
      setBlocksCalls = 0;
      blocksProcessed = 0;
      setBlockCalls = 0;
      markerAdds = 0;
      markerClears = 0;
      updatesQueued = 0;
      updatesApplied = 0;
      updatesRetried = 0;
      updatesSkipped = 0;
    }

    private boolean isEmpty() {
      return sessions == 0
          && setBlocksCalls == 0
          && blocksProcessed == 0
          && setBlockCalls == 0
          && markerAdds == 0
          && markerClears == 0
          && updatesQueued == 0
          && updatesApplied == 0
          && updatesRetried == 0
          && updatesSkipped == 0;
    }

    private Summary copyAndReset() {
      Summary copy = new Summary();
      copy.sessions = sessions;
      copy.setBlocksCalls = setBlocksCalls;
      copy.blocksProcessed = blocksProcessed;
      copy.setBlockCalls = setBlockCalls;
      copy.markerAdds = markerAdds;
      copy.markerClears = markerClears;
      copy.updatesQueued = updatesQueued;
      copy.updatesApplied = updatesApplied;
      copy.updatesRetried = updatesRetried;
      copy.updatesSkipped = updatesSkipped;
      reset();
      return copy;
    }
  }
}
