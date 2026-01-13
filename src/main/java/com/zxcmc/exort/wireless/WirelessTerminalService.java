package com.zxcmc.exort.wireless;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.access.WirelessAccessService;
import com.zxcmc.exort.wireless.bind.WirelessBindService;
import com.zxcmc.exort.wireless.charge.WirelessChargeService;
import com.zxcmc.exort.wireless.lore.WirelessLoreService;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class WirelessTerminalService {
  private static final int MAX_CHARGE = WirelessChargeService.MAX_CHARGE;

  private final StorageKeys keys;
  private final CustomItems customItems;
  private final boolean enabled;
  private final WirelessChargeService chargeService;
  private final WirelessBindService bindService;
  private final WirelessAccessService accessService;
  private final WirelessLoreService loreService;

  public WirelessTerminalService(
      ExortPlugin plugin,
      StorageKeys keys,
      CustomItems customItems,
      boolean enabled,
      int rangeChunks) {
    this.keys = keys;
    this.customItems = customItems;
    this.enabled = enabled;
    this.chargeService = new WirelessChargeService(keys);
    this.bindService = new WirelessBindService(keys);
    this.accessService = new WirelessAccessService(bindService, rangeChunks);
    this.loreService = new WirelessLoreService(plugin.getLang(), customItems);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isWireless(ItemStack stack) {
    return customItems.isWirelessTerminal(stack);
  }

  public ItemStack create() {
    ItemStack base = customItems.wirelessTerminalItem(null, MAX_CHARGE);
    return displaySample(base);
  }

  public void prepareForStorage(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    chargeService.markStoredNow(pdc);
    stack.setItemMeta(meta);
  }

  public ItemStack displaySample(ItemStack storedSample) {
    if (!isWireless(storedSample) || storedSample.getItemMeta() == null) return storedSample;
    ItemStack out = storedSample.clone();
    ItemMeta meta = out.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    int charge = chargeService.computeCharge(pdc);
    WirelessMeta metaInfo = bindService.readMeta(pdc);
    loreService.apply(meta, charge, bindService.isLinked(pdc), metaInfo, enabled);
    out.setItemMeta(meta);
    return out;
  }

  public ItemStack outputSample(ItemStack storedSample) {
    if (!isWireless(storedSample) || storedSample.getItemMeta() == null)
      return storedSample.clone();
    return extractFromStorage(storedSample.clone());
  }

  public boolean bind(
      Player player, ItemStack stack, String storageId, StorageTier tier, Location storageLoc) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return false;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    bindService.bind(pdc, player, storageId, tier, storageLoc);
    WirelessMeta metaInfo = bindService.readMeta(pdc);
    int charge = pdc.getOrDefault(keys.wirelessCharge(), PersistentDataType.INTEGER, MAX_CHARGE);
    loreService.apply(meta, charge, true, metaInfo, enabled);
    stack.setItemMeta(meta);
    return true;
  }

  public boolean isOwner(Player player, ItemStack stack) {
    if (!isWireless(stack)) return false;
    return accessService.isOwner(player, stack);
  }

  public boolean isLinked(ItemStack stack) {
    if (!isWireless(stack)) return false;
    return accessService.isLinked(stack);
  }

  public boolean inRange(Location storage, Location playerLoc) {
    return accessService.inRange(storage, playerLoc);
  }

  public Location storageLocation(ItemStack stack) {
    if (!isWireless(stack)) return null;
    return accessService.storageLocation(stack);
  }

  public void unbind(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    bindService.unbind(pdc);
    WirelessMeta metaInfo = bindService.readMeta(pdc);
    int charge = pdc.getOrDefault(keys.wirelessCharge(), PersistentDataType.INTEGER, MAX_CHARGE);
    loreService.apply(meta, charge, false, metaInfo, enabled);
    stack.setItemMeta(meta);
  }

  public ItemStack resetLinkViaCraft(ItemStack original) {
    ItemStack out = original.clone();
    unbind(out);
    return out;
  }

  public boolean consumeCharge(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return false;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    int charge = chargeService.computeCharge(pdc);
    if (charge <= 0) return false;
    charge = Math.max(0, charge - 1);
    pdc.set(keys.wirelessCharge(), PersistentDataType.INTEGER, charge);
    chargeService.clearStoredAt(pdc);
    WirelessMeta metaInfo = bindService.readMeta(pdc);
    loreService.apply(meta, charge, bindService.isLinked(pdc), metaInfo, enabled);
    stack.setItemMeta(meta);
    return charge >= 0;
  }

  /** Clears stored-at timestamp and updates lore/model when item leaves storage. */
  public ItemStack extractFromStorage(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return stack;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    int charge = chargeService.computeCharge(pdc);
    pdc.set(keys.wirelessCharge(), PersistentDataType.INTEGER, charge);
    chargeService.clearStoredAt(pdc);
    WirelessMeta metaInfo = bindService.readMeta(pdc);
    loreService.apply(meta, charge, bindService.isLinked(pdc), metaInfo, enabled);
    stack.setItemMeta(meta);
    return stack;
  }

  public boolean refreshAppearance(ItemStack stack, boolean inStorage) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return false;
    if (!inStorage) {
      extractFromStorage(stack);
      return true;
    }
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    int charge = chargeService.computeCharge(pdc);
    WirelessMeta metaInfo = bindService.readMeta(pdc);
    loreService.apply(meta, charge, bindService.isLinked(pdc), metaInfo, enabled);
    stack.setItemMeta(meta);
    return true;
  }

  public String storageId(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return null;
    return bindService.storageId(stack.getItemMeta().getPersistentDataContainer());
  }

  public String owner(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return null;
    return bindService.owner(stack.getItemMeta().getPersistentDataContainer());
  }

  public Optional<StorageTier> storageTier(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return Optional.empty();
    return bindService.tier(stack.getItemMeta().getPersistentDataContainer());
  }

  public int currentCharge(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return 0;
    return chargeService.computeCharge(stack.getItemMeta().getPersistentDataContainer());
  }

  public boolean isCharging(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return false;
    return chargeService.isCharging(stack.getItemMeta().getPersistentDataContainer());
  }

  public long chargingEndsAtMillis(ItemStack stack) {
    if (!isWireless(stack) || stack.getItemMeta() == null) return -1L;
    return chargeService.chargingEndsAtMillis(stack.getItemMeta().getPersistentDataContainer());
  }
}
