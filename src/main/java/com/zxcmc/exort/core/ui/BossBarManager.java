package com.zxcmc.exort.core.ui;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public class BossBarManager {
  private final ExortPlugin plugin;
  private final StorageManager storageManager;
  private final Lang lang;
  private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> removalTasks = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> generations = new ConcurrentHashMap<>();

  public BossBarManager(ExortPlugin plugin, StorageManager storageManager, Lang lang) {
    this.plugin = plugin;
    this.storageManager = storageManager;
    this.lang = lang;
  }

  public void showPeek(String storageId, StorageTier tier, Player player, long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    storageManager
        .getOrLoad(storageId)
        .thenAccept(
            cache ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!player.isOnline()) return;
                          if (!generations.getOrDefault(player.getUniqueId(), 0).equals(gen))
                            return;
                          long current = cache.effectiveTotal();
                          long max = Math.max(1, tier.maxItems());
                          double progress =
                              Math.min(1.0, Math.max(0.0, (double) current / (double) max));
                          String percent = FORMAT_PERCENT.format(progress * 100.0) + "%";
                          String title =
                              lang.tr(
                                  "gui.bossbar",
                                  tier.displayName(),
                                  formatNumber(current),
                                  formatNumber(max),
                                  percent);
                          double free = 1.0 - progress;
                          showCustom(player, title, progress, freeColor(free), durationTicks, gen);
                        }));
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
        tooLong ? lang.tr("wire.too_long", used, max) : lang.tr("wire.status", used, max);
    if (storageConnected) {
      title = title + " • " + lang.tr("wire.storage_connected");
    }
    BarColor color = tooLong ? BarColor.RED : BarColor.BLUE;
    showCustom(player, title, progress, color, durationTicks, gen);
  }

  public void showMonitorItem(
      String storageId, String itemKey, String itemName, Player player, long durationTicks) {
    int gen = generations.compute(player.getUniqueId(), (id, val) -> val == null ? 1 : val + 1);
    cancelRemoval(player.getUniqueId());
    storageManager
        .getOrLoad(storageId)
        .thenAccept(
            cache ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!player.isOnline()) return;
                          if (!generations.getOrDefault(player.getUniqueId(), 0).equals(gen))
                            return;
                          long amount = cache.getAmount(itemKey);
                          String title =
                              lang.tr("gui.monitor.item", itemName, formatNumber(amount));
                          showCustom(player, title, 1.0, BarColor.BLUE, durationTicks, gen);
                        }));
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
