package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.ItemKeyUtil;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
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

public class MonitorListener implements Listener {
  private final ExortPlugin plugin;
  private final Material monitorCarrier;

  public MonitorListener(ExortPlugin plugin, Material monitorCarrier) {
    this.plugin = plugin;
    this.monitorCarrier = monitorCarrier;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (block == null) return;
    if (!isMonitor(block)) return;

    if (!plugin.getRegionProtection().canUse(event.getPlayer(), block)) {
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
        if (plugin.getMonitorDisplayManager() != null) {
          plugin.getMonitorDisplayManager().refresh(block);
        }
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
              plugin.getKeys(),
              plugin,
              plugin.getWireLimit(),
              plugin.getWireHardCap(),
              plugin.getWireMaterial(),
              plugin.getStorageCarrier());
      if (link.count() == 1 && link.data() != null) {
        String itemKey = MonitorMarker.itemKey(plugin, block).orElse(null);
        String itemName = itemKey;
        var blobOpt = MonitorMarker.itemBlob(plugin, block);
        if (blobOpt.isPresent()) {
          ItemStack sample = ItemKeyUtil.deserialize(blobOpt.get());
          if (sample != null && sample.getType() != Material.AIR) {
            itemName = plugin.getItemNameService().resolveDisplayName(sample);
          }
        }
        if (itemKey != null) {
          plugin
              .getBossBarManager()
              .showMonitorItem(
                  link.data().storageId(),
                  itemKey,
                  itemName == null ? itemKey : itemName,
                  event.getPlayer(),
                  plugin.getStoragePeekTicks());
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
              plugin.getKeys(),
              plugin,
              plugin.getWireLimit(),
              plugin.getWireHardCap(),
              plugin.getWireMaterial(),
              plugin.getStorageCarrier());
      if (link.count() == 1 && link.data() != null) {
        plugin
            .getBossBarManager()
            .showPeek(
                link.data().storageId(),
                link.data().tier(),
                event.getPlayer(),
                plugin.getStoragePeekTicks());
      }
      return;
    }
    if (plugin.isMonitorRecentlyPlaced(block)) {
      return;
    }

    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(inHand);
    MonitorMarker.setItem(plugin, block, data.key(), data.bytes());
    if (plugin.getMonitorDisplayManager() != null) {
      plugin.getMonitorDisplayManager().refresh(block);
    }
  }

  private boolean isMonitor(Block block) {
    return Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
  }
}
