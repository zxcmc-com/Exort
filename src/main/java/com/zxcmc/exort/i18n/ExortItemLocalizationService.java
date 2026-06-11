package com.zxcmc.exort.i18n;

import com.zxcmc.exort.items.CustomItemRegistry;
import com.zxcmc.exort.keys.PdcValueSanitizer;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessMeta;
import com.zxcmc.exort.wireless.charge.WirelessChargeService;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ExortItemLocalizationService {
  private static final int STORAGE_ID_TAIL_LENGTH = 12;
  private static final ThreadLocal<DecimalFormat> FORMAT_NUMBER =
      ThreadLocal.withInitial(() -> new DecimalFormat("#,###"));
  private static final ThreadLocal<DecimalFormat> FORMAT_PERCENT =
      ThreadLocal.withInitial(() -> new DecimalFormat("0.0"));

  private final StorageKeys keys;
  private final Lang lang;

  public ExortItemLocalizationService(StorageKeys keys, Lang lang) {
    this.keys = keys;
    this.lang = lang;
  }

  public ItemStack localize(Player player, ItemStack source) {
    return localize(source, lang.pluginTextLanguage(player));
  }

  ItemStack localize(ItemStack source, String language) {
    if (source == null || !source.hasItemMeta()) {
      return source;
    }
    ItemMeta originalMeta = source.getItemMeta();
    if (originalMeta == null) {
      return source;
    }
    PersistentDataContainer pdc = originalMeta.getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    if (type == null || type.isBlank()) {
      return source;
    }

    ItemStack localized = source.clone();
    ItemMeta meta = localized.getItemMeta();
    if (meta == null) {
      return source;
    }

    boolean changed =
        switch (type) {
          case "storage" -> localizeStorage(meta, pdc, language);
          case "wireless_terminal" -> localizeWireless(meta, pdc, language);
          default ->
              CustomItemRegistry.fixedItem(type)
                  .map(identity -> localizeName(meta, language, identity.translationKey()))
                  .orElse(false);
        };
    if (!changed) {
      return source;
    }
    localized.setItemMeta(meta);
    return localized;
  }

  private boolean localizeName(ItemMeta meta, String language, String key) {
    meta.itemName(text(language, key));
    return true;
  }

  private boolean localizeStorage(ItemMeta meta, PersistentDataContainer pdc, String language) {
    String tierRaw = pdc.get(keys.storageTier(), PersistentDataType.STRING);
    StorageTier tier = StorageTier.fromString(tierRaw).orElse(null);
    if (tier == null) {
      return false;
    }
    long nested = pdc.getOrDefault(keys.nestedCount(), PersistentDataType.LONG, 0L);
    String storageId =
        PdcValueSanitizer.uuidString(pdc.get(keys.storageId(), PersistentDataType.STRING));
    meta.lore(storageLore(tier, storageId, nested, language));
    return true;
  }

  private List<Component> storageLore(
      StorageTier tier, String storageId, long currentAmount, String language) {
    long max = tier.maxItems();
    double percent =
        Math.min(100.0, Math.max(0.0, (double) currentAmount / Math.max(1, max) * 100.0));
    List<Component> lore = new ArrayList<>();
    lore.add(
        text(
            language,
            "lore.storage.capacity",
            formatNumber(currentAmount),
            formatNumber(max),
            formatPercent(percent) + "%"));
    if (storageId != null && !storageId.isBlank() && storageId.length() >= STORAGE_ID_TAIL_LENGTH) {
      String tail = storageId.substring(storageId.length() - STORAGE_ID_TAIL_LENGTH);
      lore.add(text(language, "lore.storage.id_tail", tail).color(NamedTextColor.GRAY));
    }
    return lore;
  }

  private boolean localizeWireless(ItemMeta meta, PersistentDataContainer pdc, String language) {
    meta.itemName(text(language, "item.wireless_terminal"));
    int charge =
        WirelessChargeService.computeChargeValue(
            System.currentTimeMillis(),
            pdc.getOrDefault(keys.wirelessCharge(), PersistentDataType.INTEGER, 100),
            pdc.getOrDefault(keys.wirelessStoredAt(), PersistentDataType.LONG, 0L));
    WirelessMeta metaInfo = wirelessMeta(pdc);
    boolean linked = pdc.has(keys.wirelessStorageId(), PersistentDataType.STRING);
    List<Component> lore = new ArrayList<>();
    lore.add(
        text(language, "lore.wireless_terminal.battery", Math.max(0, Math.min(100, charge)))
            .color(NamedTextColor.GREEN));
    String ownerDisplay = ownerDisplay(metaInfo.owner(), metaInfo.ownerName());
    if (ownerDisplay != null && !ownerDisplay.isBlank()) {
      lore.add(
          text(language, "lore.wireless_terminal.owner", ownerDisplay).color(NamedTextColor.AQUA));
    }
    if (!linked) {
      lore.add(text(language, "lore.wireless_terminal.not_linked").color(NamedTextColor.RED));
    } else {
      if (metaInfo.tier() != null) {
        lore.add(
            Component.text(metaInfo.tier().displayName()).decoration(TextDecoration.ITALIC, false));
      }
      String storageId = metaInfo.storageId();
      if (storageId != null && storageId.length() >= STORAGE_ID_TAIL_LENGTH) {
        String tail = storageId.substring(storageId.length() - STORAGE_ID_TAIL_LENGTH);
        lore.add(
            text(language, "lore.wireless_terminal.storage_tail", tail).color(NamedTextColor.GRAY));
      }
    }
    meta.lore(lore);
    return true;
  }

  private WirelessMeta wirelessMeta(PersistentDataContainer pdc) {
    StorageTier tier = null;
    String tierRaw = pdc.get(keys.wirelessTier(), PersistentDataType.STRING);
    if (tierRaw != null) {
      tier = StorageTier.fromString(tierRaw).orElse(null);
    }
    String storageId =
        PdcValueSanitizer.uuidString(pdc.get(keys.wirelessStorageId(), PersistentDataType.STRING));
    String owner =
        PdcValueSanitizer.uuidString(pdc.get(keys.wirelessOwner(), PersistentDataType.STRING));
    String ownerName = pdc.get(keys.wirelessOwnerName(), PersistentDataType.STRING);
    return new WirelessMeta(tier, storageId, owner, ownerName);
  }

  private String ownerDisplay(String owner, String ownerName) {
    if (ownerName != null && !ownerName.isBlank()) return ownerName;
    if (owner == null || owner.isBlank()) return owner;
    try {
      OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(owner));
      String name = player.getName();
      return name != null ? name : owner;
    } catch (IllegalArgumentException ignored) {
      return owner;
    }
  }

  private Component text(String language, String key, Object... params) {
    return Component.text(lang.trLanguage(language, key, params))
        .decoration(TextDecoration.ITALIC, false);
  }

  private static String formatNumber(long value) {
    return FORMAT_NUMBER.get().format(value);
  }

  private static String formatPercent(double value) {
    return FORMAT_PERCENT.get().format(value);
  }
}
