package com.zxcmc.exort.feedback;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BossBarManager {
  private final JavaPlugin plugin;
  private final StorageManager storageManager;
  private final Lang lang;
  private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> removalTasks = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> generations = new ConcurrentHashMap<>();

  public BossBarManager(JavaPlugin plugin, StorageManager storageManager, Lang lang) {
    this.plugin = plugin;
    this.storageManager = storageManager;
    this.lang = lang;
  }

  public void showPeek(String storageId, StorageTier tier, Player player, long durationTicks) {
    showPeek(storageId, tier, null, player, durationTicks);
  }

  public void showPeek(
      String storageId, StorageTier tier, String displayName, Player player, long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    storageManager
        .getOrLoad(storageId)
        .whenComplete(
            (cache, err) -> {
              if (err != null) {
                handleLoadFailure(player, storageId, durationTicks, gen, err);
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin,
                  () -> {
                    if (!player.isOnline()) return;
                    if (!generations.getOrDefault(player.getUniqueId(), 0).equals(gen)) return;
                    long current = cache.effectiveTotal();
                    long max = Math.max(1, tier.maxItems());
                    double progress = Math.min(1.0, Math.max(0.0, (double) current / (double) max));
                    String percent = FORMAT_PERCENT.format(progress * 100.0) + "%";
                    String labelName = displayName == null ? cache.getDisplayName() : displayName;
                    String title =
                        lang.tr(
                            player,
                            "gui.bossbar",
                            StorageDisplayName.label(lang, player, tier, labelName),
                            formatNumber(current),
                            formatNumber(max),
                            percent);
                    double free = 1.0 - progress;
                    showCustom(player, title, progress, freeColor(free), durationTicks, gen);
                  });
            });
  }

  public void showWireStatus(
      int used,
      int max,
      boolean tooLong,
      boolean storageConnected,
      Player player,
      long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    double progress = Math.min(1.0, Math.max(0.0, (double) used / Math.max(1, max)));
    String title =
        tooLong
            ? lang.tr(player, "wire.too_long", used, max)
            : lang.tr(player, "wire.status", used, max);
    if (storageConnected) {
      title = title + " • " + lang.tr(player, "wire.storage_connected");
    }
    BarColor color = tooLong ? BarColor.RED : BarColor.BLUE;
    showCustom(player, title, progress, color, durationTicks, gen);
  }

  public void showRelayStatus(
      String peerCoords, String storageStatus, Player player, long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    String title = lang.tr(player, "relay.status", peerCoords, storageStatus);
    showCustom(player, title, 1.0, BarColor.BLUE, durationTicks, gen);
  }

  public void showChunkLoaderStatus(UUID loaderId, Player player, long durationTicks) {
    showChunkLoaderStatus(ChunkLoaderType.defaultType(), loaderId, player, durationTicks);
  }

  public void showChunkLoaderStatus(
      ChunkLoaderType type, UUID loaderId, Player player, long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    String title =
        lang.tr(
            player,
            "chunk_loader.status",
            lang.tr(player, safeType.translationKey()),
            loaderId == null ? "unknown" : loaderId.toString());
    showCustom(player, title, 1.0, BarColor.PURPLE, durationTicks, gen);
  }

  public void showMonitorItem(
      String storageId, String itemKey, String itemName, Player player, long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    storageManager
        .getOrLoad(storageId)
        .whenComplete(
            (cache, err) -> {
              if (err != null) {
                handleLoadFailure(player, storageId, durationTicks, gen, err);
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin,
                  () -> {
                    if (!player.isOnline()) return;
                    if (!generations.getOrDefault(player.getUniqueId(), 0).equals(gen)) return;
                    long amount = cache.getAmount(itemKey);
                    String title =
                        lang.tr(player, "gui.monitor.item", itemName, formatNumber(amount));
                    showCustom(player, title, 1.0, BarColor.BLUE, durationTicks, gen);
                  });
            });
  }

  public void showError(Player player, String message, long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    showCustom(player, message, 1.0, BarColor.RED, durationTicks, gen);
  }

  public void showProgress(Player player, String title, double progress, BarColor color) {
    cancelRemoval(player.getUniqueId());
    BossBar bar = createOrGetBar(player);
    bar.setColor(color);
    bar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
    bar.setTitle(title);
    bar.setVisible(true);
  }

  private void showCustom(
      Player player, String title, double progress, BarColor color, long durationTicks, int gen) {
    BossBar bar = createOrGetBar(player);
    bar.setColor(color);
    bar.setProgress(progress);
    bar.setTitle(title);
    bar.setVisible(true);
    int taskId =
        Bukkit.getScheduler()
            .runTaskLater(
                plugin,
                () -> {
                  Integer currentGen = generations.getOrDefault(player.getUniqueId(), 0);
                  if (!currentGen.equals(gen)) return;
                  remove(player);
                },
                durationTicks)
            .getTaskId();
    removalTasks.put(player.getUniqueId(), taskId);
  }

  private void handleLoadFailure(
      Player player, String storageId, long durationTicks, int gen, Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to load storage " + storageId, err);
    PluginTasks.runSyncIfEnabled(
        plugin,
        () -> {
          if (player == null || !player.isOnline()) return;
          if (!generations.getOrDefault(player.getUniqueId(), 0).equals(gen)) return;
          showCustom(
              player,
              lang.tr(player, "message.storage_load_failed"),
              1.0,
              BarColor.RED,
              durationTicks,
              gen);
        });
  }

  private BossBar createOrGetBar(Player player) {
    return bars.compute(
        player.getUniqueId(),
        (id, existing) -> {
          if (existing != null) {
            if (!existing.getPlayers().contains(player)) {
              existing.addPlayer(player);
            }
            existing.setVisible(true);
            return existing;
          }
          BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
          bar.addPlayer(player);
          bar.setVisible(true);
          return bar;
        });
  }

  public void remove(Player player) {
    remove(player.getUniqueId());
  }

  public void remove(UUID playerId) {
    cancelRemoval(playerId);
    generations.remove(playerId);
    BossBar bar = bars.remove(playerId);
    if (bar != null) {
      bar.removeAll();
    }
  }

  private void cancelRemoval(UUID playerId) {
    Integer taskId = removalTasks.remove(playerId);
    if (taskId != null) {
      Bukkit.getScheduler().cancelTask(taskId);
    }
  }

  public void clearAll() {
    for (Integer id : removalTasks.values()) {
      if (id != null) {
        Bukkit.getScheduler().cancelTask(id);
      }
    }
    removalTasks.clear();
    bars.values().forEach(BossBar::removeAll);
    bars.clear();
  }

  private String formatNumber(long value) {
    return FORMAT_NUMBER.format(value);
  }

  private BarColor freeColor(double freeRatio) {
    if (freeRatio <= 0.05) {
      return BarColor.RED;
    }
    if (freeRatio <= 0.30) {
      return BarColor.YELLOW;
    }
    return BarColor.GREEN;
  }

  private static final java.text.DecimalFormat FORMAT_NUMBER = new java.text.DecimalFormat("#,###");
  private static final java.text.DecimalFormat FORMAT_PERCENT = new java.text.DecimalFormat("0.0");
}
