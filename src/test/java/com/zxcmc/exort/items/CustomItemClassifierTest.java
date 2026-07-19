package com.zxcmc.exort.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class CustomItemClassifierTest {
  private final StorageKeys keys = new StorageKeys(BukkitTestDoubles.plugin());
  private final CustomItems customItems = customItems(keys);

  @Test
  void acceptsOnlyKnownExortTypes() {
    ItemStack terminal = stack(Material.PAPER, "terminal");
    ItemStack storageWithoutTier = stack(Material.PAPER, "storage");
    ItemStack unknown = stack(Material.PAPER, "foreign_widget");

    assertTrue(customItems.isCustomItem(terminal));
    assertTrue(customItems.isTerminal(terminal));
    assertTrue(customItems.isCustomItem(storageWithoutTier));
    assertFalse(customItems.isCustomItem(unknown));
    assertFalse(customItems.isTerminal(unknown));
    assertFalse(customItems.refreshItem(unknown, null, false));
  }

  @Test
  void rejectsKnownMarkerOnForeignCarrierMaterial() {
    ItemStack foreignTerminal = stack(Material.DIAMOND, "terminal");
    ItemStack foreignWireless = stack(Material.PAPER, "wireless_terminal");

    assertFalse(customItems.isCustomItem(foreignTerminal));
    assertFalse(customItems.isTerminal(foreignTerminal));
    assertFalse(customItems.refreshItem(foreignTerminal, null, false));
    assertFalse(customItems.isWirelessTerminal(foreignWireless));
    assertTrue(customItems.isWirelessTerminal(stack(Material.SHIELD, "wireless_terminal")));
  }

  @Test
  void craftingProtectionUsesSameStrictClassification() {
    CraftingRules rules = new CraftingRules(keys, true, false);
    String id = "00000000-0000-0000-0000-000000000042";

    assertTrue(rules.isCustomItem(stack(Material.PAPER, "wire")));
    assertFalse(rules.isCustomItem(stack(Material.STONE, "wire")));
    assertFalse(rules.isCustomItem(stack(Material.PAPER, "external_type")));
    assertTrue(
        rules.isCustomItem(stack(Material.PAPER, "external_type", keys.storageId(), id)),
        "durable storage identities stay protected from destructive recipes even if type is"
            + " corrupt");
  }

  @Test
  void wirelessBoosterRequiresKnownTierAndPaperCarrier() {
    SimplePdc validPdc = new SimplePdc();
    validPdc.values.put(keys.type(), "wireless_booster");
    validPdc.values.put(keys.wirelessBoosterTier(), "legendary");
    ItemStack valid = new PdcStack(Material.PAPER, validPdc);

    SimplePdc invalidPdc = new SimplePdc();
    invalidPdc.values.put(keys.type(), "wireless_booster");
    invalidPdc.values.put(keys.wirelessBoosterTier(), "unknown");

    assertTrue(customItems.isWirelessBooster(valid));
    assertTrue(
        customItems.wirelessBoosterTier(valid).orElseThrow() == WirelessBoosterTier.LEGENDARY);
    assertFalse(customItems.isWirelessBooster(new PdcStack(Material.PAPER, invalidPdc)));
    assertFalse(customItems.isWirelessBooster(new PdcStack(Material.DIAMOND, validPdc)));
  }

  private ItemStack stack(Material material, String type) {
    return stack(material, type, null, null);
  }

  private ItemStack stack(
      Material material, String type, NamespacedKey extraKey, String extraValue) {
    SimplePdc pdc = new SimplePdc();
    pdc.values.put(keys.type(), type);
    if (extraKey != null) {
      pdc.values.put(extraKey, extraValue);
    }
    return new PdcStack(material, pdc);
  }

  private static CustomItems customItems(StorageKeys keys) {
    return new CustomItems(
        keys,
        null,
        CustomItemModelConfig.empty(),
        com.zxcmc.exort.wireless.WirelessRuntimeConfig.defaults(),
        false);
  }

  private static final class PdcStack extends ItemStack {
    private final Material material;
    private final ItemMeta meta;

    private PdcStack(Material material, SimplePdc pdc) {
      this.material = material;
      this.meta =
          BukkitTestDoubles.proxy(
              ItemMeta.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getPersistentDataContainer" -> pdc.proxy();
                    case "clone" -> proxy;
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
    }

    @Override
    public Material getType() {
      return material;
    }

    @Override
    public boolean hasItemMeta() {
      return true;
    }

    @Override
    public ItemMeta getItemMeta() {
      return meta;
    }
  }

  private static final class SimplePdc {
    private final Map<NamespacedKey, Object> values = new HashMap<>();

    private PersistentDataContainer proxy() {
      return BukkitTestDoubles.proxy(
          PersistentDataContainer.class,
          (proxy, method, args) ->
              switch (method.getName()) {
                case "get" -> values.get((NamespacedKey) args[0]);
                case "has" -> values.containsKey((NamespacedKey) args[0]);
                case "getOrDefault" -> values.getOrDefault((NamespacedKey) args[0], args[2]);
                case "getKeys" -> Set.copyOf(values.keySet());
                case "isEmpty" -> values.isEmpty();
                default -> BukkitTestDoubles.defaultValue(method.getReturnType());
              });
    }
  }
}
