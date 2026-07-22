package com.zxcmc.exort.gui;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.platform.PlayerInteractionRange;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class GuiSessionValidator {
  private final Plugin plugin;
  private final StorageKeys keys;
  private final IntSupplier wireLimit;
  private final IntSupplier wireHardCap;
  private final IntSupplier relayRangeChunks;
  private final Supplier<Material> wireMaterial;
  private final Supplier<Material> storageCarrier;
  private final Supplier<Material> relayCarrier;
  private final Supplier<Material> terminalCarrier;
  private final Supplier<RegionProtection> regionProtection;
  private final Supplier<NetworkGraphCache> networkGraphCache;
  private final Supplier<StorageTierCatalog> storageTierCatalog;

  GuiSessionValidator(
      Plugin plugin,
      StorageKeys keys,
      IntSupplier wireLimit,
      IntSupplier wireHardCap,
      IntSupplier relayRangeChunks,
      Supplier<Material> wireMaterial,
      Supplier<Material> storageCarrier,
      Supplier<Material> relayCarrier,
      Supplier<Material> terminalCarrier,
      Supplier<RegionProtection> regionProtection,
      Supplier<NetworkGraphCache> networkGraphCache,
      Supplier<StorageTierCatalog> storageTierCatalog) {
    this.plugin = plugin;
    this.keys = keys;
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.relayCarrier = relayCarrier;
    this.terminalCarrier = terminalCarrier;
    this.regionProtection = regionProtection;
    this.networkGraphCache = networkGraphCache;
    this.storageTierCatalog = storageTierCatalog;
  }

  boolean isSessionValid(GuiSession session) {
    Block termBlock = session.getTerminalBlock();
    if (termBlock == null) {
      return true;
    }
    if (termBlock.getType() != terminalCarrier.get()) {
      return false;
    }
    Player player = session.getViewer();
    if (player == null || !regionProtection.get().canUse(player, termBlock)) {
      return false;
    }
    var link =
        TerminalLinkFinder.find(
            termBlock,
            keys,
            plugin,
            networkGraphCache.get(),
            wireLimit.getAsInt(),
            wireHardCap.getAsInt(),
            wireMaterial.get(),
            storageCarrier.get(),
            relayCarrier.get(),
            relayRangeChunks.getAsInt());
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
    if (!regionProtection.get().canUse(player, terminal)) return true;
    return isOutOfDeviceRange(player, terminal);
  }

  WirelessValidationResult wirelessValidation(
      AbstractStorageSession session,
      WirelessTerminalService wirelessService,
      WirelessTransmitterService transmitterService) {
    Player player = session.getViewer();
    if (player == null || !player.isOnline()) {
      return new WirelessValidationResult(player, null, true);
    }
    Location anchor = session.getStorageLocation();
    if (wirelessService == null || storageCarrier.get() == null) {
      return new WirelessValidationResult(player, "message.wireless.disabled", false);
    }
    if (!wirelessService.isEnabled()) {
      return new WirelessValidationResult(player, "message.wireless.disabled", false);
    }
    if (anchor == null || !anchor.isWorldLoaded()) {
      return new WirelessValidationResult(player, "message.wireless.missing_storage", false);
    }
    if (!hasLiveStorageAnchor(session.getStorageId(), anchor)) {
      return new WirelessValidationResult(player, "message.wireless.missing_storage", false);
    }
    Block anchorBlock = anchor.getBlock();
    if (!regionProtection.get().canUse(player, anchorBlock)) {
      return new WirelessValidationResult(player, "message.no_permission", false);
    }
    if (!hasMatchingWirelessTerminal(player, session.getStorageId(), wirelessService)) {
      return new WirelessValidationResult(player, "message.wireless.not_linked", false);
    }
    if (transmitterService == null
        || !transmitterService.hasCoverage(session.getStorageId(), player.getLocation())) {
      return new WirelessValidationResult(player, "message.wireless.out_of_range", false);
    }
    return WirelessValidationResult.valid();
  }

  private boolean hasMatchingWirelessTerminal(
      Player player, String storageId, WirelessTerminalService wirelessService) {
    for (ItemStack stack : player.getInventory().getContents()) {
      if (isMatchingWirelessTerminal(player, storageId, wirelessService, stack)) {
        return true;
      }
    }
    return isMatchingWirelessTerminal(player, storageId, wirelessService, player.getItemOnCursor());
  }

  private boolean isMatchingWirelessTerminal(
      Player player, String storageId, WirelessTerminalService wirelessService, ItemStack stack) {
    return stack != null
        && wirelessService.isWireless(stack)
        && wirelessService.isOwner(player, stack)
        && wirelessService.isLinked(stack)
        && storageId.equals(wirelessService.storageId(stack))
        && wirelessService.currentCharge(stack) > 0;
  }

  boolean hasLiveStorageAnchor(String expectedStorageId, Location anchor) {
    if (expectedStorageId == null || expectedStorageId.isBlank()) return false;
    if (anchor == null) return false;
    Material carrier = storageCarrier.get();
    if (carrier == null) return false;
    World world;
    try {
      world = anchor.getWorld();
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (world == null) return false;
    int x = anchor.getBlockX();
    int z = anchor.getBlockZ();
    if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
    Block block = world.getBlockAt(x, anchor.getBlockY(), z);
    if (!Carriers.matchesCarrier(block, carrier)) return false;
    return StorageMarker.get(plugin, block, storageTierCatalog.get())
        .map(data -> expectedStorageId.equals(data.storageId()))
        .orElse(false);
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
