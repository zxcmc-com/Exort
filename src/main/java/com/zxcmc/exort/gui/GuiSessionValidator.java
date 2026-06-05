package com.zxcmc.exort.gui;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.platform.PlayerInteractionRange;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class GuiSessionValidator {
  private final Plugin plugin;
  private final StorageKeys keys;
  private final IntSupplier wireLimit;
  private final IntSupplier wireHardCap;
  private final Supplier<Material> wireMaterial;
  private final Supplier<Material> storageCarrier;
  private final Supplier<Material> terminalCarrier;

  GuiSessionValidator(
      Plugin plugin,
      StorageKeys keys,
      IntSupplier wireLimit,
      IntSupplier wireHardCap,
      Supplier<Material> wireMaterial,
      Supplier<Material> storageCarrier,
      Supplier<Material> terminalCarrier) {
    this.plugin = plugin;
    this.keys = keys;
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
  }

  boolean isSessionValid(GuiSession session) {
    Block termBlock = session.getTerminalBlock();
    if (termBlock == null) {
      return true;
    }
    if (termBlock.getType() != terminalCarrier.get()) {
      return false;
    }
    var link =
        TerminalLinkFinder.find(
            termBlock,
            keys,
            plugin,
            wireLimit.getAsInt(),
            wireHardCap.getAsInt(),
            wireMaterial.get(),
            storageCarrier.get());
    return link.count() == 1
        && link.data() != null
        && session.getStorageId().equals(link.data().storageId());
  }

  boolean shouldClosePhysicalSession(AbstractStorageSession session) {
    Player player = session.getViewer();
    if (player == null || !player.isOnline()) return true;
    Block terminal = session.getTerminalBlock();
    if (terminal == null) return false;
    Material carrier = terminalCarrier.get();
    if (carrier == null) return false;
    if (!isBlockChunkLoaded(terminal)) return true;
    if (!Carriers.matchesCarrier(terminal, carrier)) return true;
    return isOutOfDeviceRange(player, terminal);
  }

  WirelessValidationResult wirelessValidation(
      AbstractStorageSession session, WirelessTerminalService wirelessService) {
    Player player = session.getViewer();
    if (player == null || !player.isOnline()) {
      return new WirelessValidationResult(player, null, true);
    }
    Location anchor = session.getStorageLocation();
    if (wirelessService == null || storageCarrier.get() == null) {
      return WirelessValidationResult.valid();
    }
    if (!wirelessService.isEnabled()) {
      return new WirelessValidationResult(player, "message.wireless.disabled", false);
    }
    if (anchor == null || !anchor.isWorldLoaded()) {
      return new WirelessValidationResult(player, "message.wireless.missing_storage", false);
    }
    if (!wirelessService.inRange(anchor, player.getLocation())) {
      return new WirelessValidationResult(player, "message.wireless.out_of_range", false);
    }
    if (!Carriers.matchesCarrier(anchor.getBlock(), storageCarrier.get())) {
      return new WirelessValidationResult(player, "message.wireless.missing_storage", false);
    }
    return WirelessValidationResult.valid();
  }

  private boolean isOutOfDeviceRange(Player player, Block block) {
    if (player.getWorld() == null || block.getWorld() == null) return true;
    if (!player.getWorld().getUID().equals(block.getWorld().getUID())) return true;
    Location playerLocation = player.getLocation();
    double dx = playerLocation.getX() - (block.getX() + 0.5D);
    double dy = playerLocation.getY() - (block.getY() + 0.5D);
    double dz = playerLocation.getZ() - (block.getZ() + 0.5D);
    return dx * dx + dy * dy + dz * dz
        > PlayerInteractionRange.physicalDeviceCloseRangeSquared(player);
  }

  private boolean isBlockChunkLoaded(Block block) {
    return block.getWorld() != null
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  record WirelessValidationResult(Player player, String messageKey, boolean offline) {
    static WirelessValidationResult valid() {
      return new WirelessValidationResult(null, null, false);
    }

    boolean isValid() {
      return player == null && messageKey == null && !offline;
    }
  }
}
