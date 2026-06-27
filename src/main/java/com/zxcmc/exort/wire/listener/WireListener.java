package com.zxcmc.exort.wire.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.WireMarker;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class WireListener implements Listener {
  private static final BlockFace[] FACES =
      new BlockFace[] {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
      };
  private final int wireLimit;
  private final int wireHardCap;
  private final JavaPlugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final BossBarManager bossBarManager;
  private final StorageKeys keys;
  private final long peekDurationTicks;

  private final Material wireMaterial;
  private final Material storageCarrier;

  public WireListener(WireListenerDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.regionProtection = dependencies.regionProtection();
    this.worldEditWandGuard = dependencies.worldEditWandGuard();
    this.bossBarManager = dependencies.bossBarManager();
    this.keys = dependencies.keys();
    this.wireLimit = dependencies.wireLimit();
    this.wireHardCap = dependencies.wireHardCap();
    this.wireMaterial = dependencies.wireMaterial();
    this.peekDurationTicks = dependencies.peekDurationTicks();
    this.storageCarrier = dependencies.storageCarrier();
  }

  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getPlayer().isSneaking()) return;
    Block block = event.getClickedBlock();
    if (block == null || !Carriers.matchesCarrier(block, wireMaterial)) return;
    if (!WireMarker.isWire(plugin, block)) return;
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;
    if (!regionProtection.canInteract(event.getPlayer(), block)) {
      event.setCancelled(true);
      return;
    }

    NetworkInfo info = exploreNetwork(block);
    if (info.tooLongHardCap()) return;

    boolean tooLong = info.wires() > wireLimit;
    bossBarManager.showWireStatus(
        info.wires(),
        wireLimit,
        tooLong,
        info.storageConnected(),
        event.getPlayer(),
        peekDurationTicks);
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    if (!allowPlacement(event.getItem())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onWaterFlow(BlockFromToEvent event) {
    if (wireMaterial != Material.CHORUS_PLANT) return;
    Block target = event.getToBlock();
    if (target.getType() != wireMaterial) return;
    if (WireMarker.isWire(plugin, target)) {
      event.setCancelled(true);
    }
  }

  private NetworkInfo exploreNetwork(Block start) {
    Queue<Block> queue = new ArrayDeque<>();
    Set<Block> visited = new HashSet<>();
    queue.add(start);
    visited.add(start);
    boolean storageConnected = false;

    while (!queue.isEmpty()) {
      Block current = queue.poll();
      for (var face : FACES) {
        Block next = current.getRelative(face);
        if (visited.contains(next)) continue;
        if (!isChunkLoaded(next)) continue;
        if (isWire(next)) {
          visited.add(next);
          queue.add(next);
          if (visited.size() > wireHardCap) {
            return new NetworkInfo(visited.size(), storageConnected, true);
          }
        } else if (isStorage(next)) {
          storageConnected = true;
        }
      }
    }
    return new NetworkInfo(visited.size(), storageConnected, false);
  }

  private boolean isWire(Block block) {
    return Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
  }

  private boolean isStorage(Block block) {
    return Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent();
  }

  private boolean isChunkLoaded(Block block) {
    return block != null
        && block.getWorld() != null
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private boolean allowPlacement(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return false;
    PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    if (type == null) return false;
    return "wire".equalsIgnoreCase(type)
        || "terminal".equalsIgnoreCase(type)
        || "storage".equalsIgnoreCase(type)
        || "monitor".equalsIgnoreCase(type)
        || "import_bus".equalsIgnoreCase(type)
        || "export_bus".equalsIgnoreCase(type);
  }

  private record NetworkInfo(int wires, boolean storageConnected, boolean tooLongHardCap) {}
}
