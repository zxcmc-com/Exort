package com.zxcmc.exort.monitor.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class MonitorListener implements Listener {
  private final JavaPlugin plugin;
  private final RegionProtection regionProtection;
  private final AuthenticationGate authenticationGate;
  private final WorldEditWandGuard worldEditWandGuard;
  private final StorageKeys keys;
  private final BossBarManager bossBarManager;
  private final ItemNameService itemNameService;
  private final Material monitorCarrier;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material relayCarrier;
  private final IntSupplier wireLimit;
  private final IntSupplier wireHardCap;
  private final IntSupplier relayRangeChunks;
  private final LongSupplier storagePeekTicks;
  private final Predicate<Block> monitorRecentlyPlaced;
  private final Consumer<Block> monitorDisplayRefresh;

  public MonitorListener(MonitorListenerDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.regionProtection = dependencies.regionProtection();
    this.authenticationGate = dependencies.authenticationGate();
    this.worldEditWandGuard = dependencies.worldEditWandGuard();
    this.keys = dependencies.keys();
    this.bossBarManager = dependencies.bossBarManager();
    this.itemNameService = dependencies.itemNameService();
    this.monitorCarrier = dependencies.monitorCarrier();
    this.wireMaterial = dependencies.wireMaterial();
    this.storageCarrier = dependencies.storageCarrier();
    this.relayCarrier = dependencies.relayCarrier();
    this.wireLimit = dependencies.wireLimit();
    this.wireHardCap = dependencies.wireHardCap();
    this.relayRangeChunks = dependencies.relayRangeChunks();
    this.storagePeekTicks = dependencies.storagePeekTicks();
    this.monitorRecentlyPlaced = dependencies.monitorRecentlyPlaced();
    this.monitorDisplayRefresh = dependencies.monitorDisplayRefresh();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (block == null) return;
    if (!isMonitor(block)) return;
    if (hasDeniedUse(event) || authenticationGate.blocks(event.getPlayer())) return;
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;

    if (!regionProtection.canUse(event.getPlayer(), block)) {
      event.setCancelled(true);
      return;
    }

    if (event.getPlayer().isSneaking()) {
      ItemStack inHand = event.getItem();
      if (inHand == null || inHand.getType() == Material.AIR) {
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        MonitorMarker.clearItem(plugin, block);
        monitorDisplayRefresh.accept(block);
      }
      return;
    }

    if (MonitorMarker.itemKey(plugin, block).isPresent()) {
      event.setUseInteractedBlock(Result.DENY);
      event.setUseItemInHand(Result.DENY);
      event.setCancelled(true);
      var link =
          TerminalLinkFinder.find(
              block,
              keys,
              plugin,
              wireLimit.getAsInt(),
              wireHardCap.getAsInt(),
              wireMaterial,
              storageCarrier,
              relayCarrier,
              relayRangeChunks.getAsInt());
      if (link.count() == 1 && link.data() != null) {
        String itemKey = MonitorMarker.itemKey(plugin, block).orElse(null);
        String itemName = itemKey;
        var blobOpt = MonitorMarker.itemBlob(plugin, block);
        if (blobOpt.isPresent()) {
          ItemStack sample = ItemKeyUtil.deserialize(blobOpt.get());
          if (sample != null && sample.getType() != Material.AIR) {
            String language =
                itemNameService.dictionaryLanguage(
                    event.getPlayer().locale().toString(), itemNameService.getActiveLanguage());
            itemName = itemNameService.resolveDisplayName(sample, language);
          }
        }
        if (itemKey != null) {
          bossBarManager.showMonitorItem(
              link.data().storageId(),
              itemKey,
              itemName == null ? itemKey : itemName,
              event.getPlayer(),
              storagePeekTicks.getAsLong());
        }
      }
      return;
    }

    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    event.setCancelled(true);

    ItemStack inHand = event.getItem();
    if (inHand == null || inHand.getType() == Material.AIR) {
      var link =
          TerminalLinkFinder.find(
              block,
              keys,
              plugin,
              wireLimit.getAsInt(),
              wireHardCap.getAsInt(),
              wireMaterial,
              storageCarrier,
              relayCarrier,
              relayRangeChunks.getAsInt());
      if (link.count() == 1 && link.data() != null) {
        bossBarManager.showPeek(
            link.data().storageId(),
            link.data().tier(),
            link.data().displayName(),
            event.getPlayer(),
            storagePeekTicks.getAsLong());
      }
      return;
    }
    if (monitorRecentlyPlaced.test(block)) {
      return;
    }

    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(inHand);
    MonitorMarker.setItem(plugin, block, data.key(), data.bytes());
    monitorDisplayRefresh.accept(block);
  }

  private static boolean hasDeniedUse(PlayerInteractEvent event) {
    return event.useInteractedBlock() == Result.DENY || event.useItemInHand() == Result.DENY;
  }

  private boolean isMonitor(Block block) {
    return Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
  }
}
