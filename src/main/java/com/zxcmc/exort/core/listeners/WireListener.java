package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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
  private final ExortPlugin plugin;
  private final StorageKeys keys;
  private final long peekDurationTicks;

  private final Material wireMaterial;
  private final Material storageCarrier;

  public WireListener(
      ExortPlugin plugin,
      StorageKeys keys,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      long peekDurationTicks,
      Material storageCarrier) {
    this.plugin = plugin;
    this.keys = keys;
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.wireMaterial = wireMaterial;
    this.peekDurationTicks = peekDurationTicks;
    this.storageCarrier = storageCarrier;
  }

  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getPlayer().isSneaking()) return;
    Block block = event.getClickedBlock();
    if (block == null || !Carriers.matchesCarrier(block, wireMaterial)) return;
    if (!WireMarker.isWire(plugin, block)) return;
    if (!plugin.getRegionProtection().canInteract(event.getPlayer(), block)) {
      event.setCancelled(true);
      return;
    }

    NetworkInfo info = exploreNetwork(block);
    if (info.tooLongHardCap()) return;

    boolean tooLong = info.wires() > wireLimit;
    plugin
        .getBossBarManager()
        .showWireStatus(
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
