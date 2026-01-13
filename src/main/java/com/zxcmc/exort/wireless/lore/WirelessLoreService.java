package com.zxcmc.exort.wireless.lore;

import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.wireless.WirelessMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class WirelessLoreService {
  private final Lang lang;
  private final CustomItems customItems;

  public WirelessLoreService(Lang lang, CustomItems customItems) {
    this.lang = lang;
    this.customItems = customItems;
  }

  public void apply(
      ItemMeta meta, int charge, boolean linked, WirelessMeta metaInfo, boolean enabled) {
    if (meta == null) return;
    meta.itemName(
        Component.text(lang.tr("item.wireless_terminal")).decoration(TextDecoration.ITALIC, false));
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
    meta.setUnbreakable(false);
    List<Component> lore = new ArrayList<>();
    lore.add(
        Component.text(
                lang.tr("item.wireless_terminal.battery", Math.max(0, Math.min(100, charge))))
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
    String ownerDisplay =
        ownerDisplay(
            metaInfo == null ? null : metaInfo.owner(),
            metaInfo == null ? null : metaInfo.ownerName());
    if (ownerDisplay != null && !ownerDisplay.isBlank()) {
      lore.add(
          Component.text(lang.tr("item.wireless_terminal.owner", ownerDisplay))
              .color(NamedTextColor.AQUA)
              .decoration(TextDecoration.ITALIC, false));
    }
    if (!linked) {
      lore.add(
          Component.text(lang.tr("item.wireless_terminal.not_linked"))
              .color(NamedTextColor.RED)
              .decoration(TextDecoration.ITALIC, false));
    } else if (metaInfo != null) {
      if (metaInfo.tier() != null) {
        lore.add(
            Component.text(metaInfo.tier().displayName()).decoration(TextDecoration.ITALIC, false));
      }
      if (metaInfo.storageId() != null && metaInfo.storageId().length() >= 12) {
        String tail = metaInfo.storageId().substring(metaInfo.storageId().length() - 12);
        lore.add(
            Component.text(lang.tr("item.wireless_terminal.storage_tail", tail))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
      }
    }
    meta.lore(lore);
    if (meta instanceof Damageable dmg) {
      int max = Material.SHIELD.getMaxDurability();
      int damage = (int) Math.round((100 - Math.max(0, Math.min(100, charge))) / 100.0 * max);
      dmg.setDamage(Math.max(0, Math.min(max, damage)));
    }
    customItems.applyWirelessModel(meta, charge > 0, linked, enabled);
  }

  private String ownerDisplay(String owner, String ownerName) {
    if (ownerName != null && !ownerName.isBlank()) return ownerName;
    if (owner == null || owner.isBlank()) return owner;
    try {
      UUID uuid = UUID.fromString(owner);
      OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
      String name = player.getName();
      return name != null ? name : owner;
    } catch (IllegalArgumentException ignored) {
      return owner;
    }
  }
}
