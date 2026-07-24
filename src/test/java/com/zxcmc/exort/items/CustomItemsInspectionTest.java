package com.zxcmc.exort.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.api.model.ExortContentType;
import com.zxcmc.exort.api.model.ExortItemCopyPolicy;
import com.zxcmc.exort.api.model.ExortItemDescriptor;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class CustomItemsInspectionTest {
  private static final Logger LOGGER = Logger.getLogger(CustomItemsInspectionTest.class.getName());
  private static final String VALID_ID = "00000000-0000-0000-0000-000000000042";

  private final StorageKeys keys = new StorageKeys(BukkitTestDoubles.plugin());
  private final CustomItems customItems =
      new CustomItems(
          keys,
          null,
          CustomItemModelConfig.empty(),
          WirelessRuntimeConfig.defaults(),
          false,
          tierCatalog());

  @Test
  void classifiesEveryTemplateAndStatefulContentType() {
    Map<String, ExortContentType> expected = new LinkedHashMap<>();
    expected.put("storage_core", ExortContentType.STORAGE_CORE);
    expected.put("terminal", ExortContentType.TERMINAL);
    expected.put("crafting_terminal", ExortContentType.CRAFTING_TERMINAL);
    expected.put("wire", ExortContentType.WIRE);
    expected.put("monitor", ExortContentType.MONITOR);
    expected.put("import_bus", ExortContentType.IMPORT_BUS);
    expected.put("export_bus", ExortContentType.EXPORT_BUS);
    expected.put("relay", ExortContentType.RELAY);
    expected.put("transmitter", ExortContentType.TRANSMITTER);
    expected.put("chunk_loader", ExortContentType.CHUNK_LOADER);
    expected.put("personal_chunk_loader", ExortContentType.PERSONAL_CHUNK_LOADER);
    expected.put("dormant_chunk_loader", ExortContentType.DORMANT_CHUNK_LOADER);

    expected.forEach(
        (id, type) -> {
          ExortItemDescriptor descriptor =
              customItems.inspectItem(stack(Material.PAPER, Map.of(keys.type(), id))).orElseThrow();
          assertEquals(type, descriptor.type());
          assertEquals(ExortItemCopyPolicy.TEMPLATE, descriptor.copyPolicy());
          assertTrue(descriptor.variantKey().isEmpty());
        });

    ExortItemDescriptor wireless =
        customItems
            .inspectItem(
                stack(
                    Material.SHIELD, Map.of(keys.type(), FixedItemCatalog.WIRELESS_TERMINAL.id())))
            .orElseThrow();
    assertEquals(ExortContentType.WIRELESS_TERMINAL, wireless.type());
    assertEquals(ExortItemCopyPolicy.PRESERVE_STATE, wireless.copyPolicy());
  }

  @Test
  void exposesOnlyTierVariantsAndCopySafety() {
    ExortItemDescriptor blankStorage =
        customItems
            .inspectItem(
                stack(Material.PAPER, Map.of(keys.type(), "storage", keys.storageTier(), "rare")))
            .orElseThrow();
    ExortItemDescriptor identifiedStorage =
        customItems
            .inspectItem(
                stack(
                    Material.PAPER,
                    Map.of(
                        keys.type(),
                        "storage",
                        keys.storageTier(),
                        "rare",
                        keys.storageId(),
                        VALID_ID)))
            .orElseThrow();
    ExortItemDescriptor booster =
        customItems
            .inspectItem(
                stack(
                    Material.PAPER,
                    Map.of(
                        keys.type(), "wireless_booster", keys.wirelessBoosterTier(), "legendary")))
            .orElseThrow();
    ExortItemDescriptor identifiedLoader =
        customItems
            .inspectItem(
                stack(
                    Material.PAPER,
                    Map.of(keys.type(), "personal_chunk_loader", keys.chunkLoaderId(), VALID_ID)))
            .orElseThrow();

    assertEquals(Optional.of("RARE"), blankStorage.variantKey());
    assertEquals(ExortItemCopyPolicy.PRESERVE_STATE, blankStorage.copyPolicy());
    assertEquals(ExortItemCopyPolicy.PRESERVE_UNIQUE_IDENTITY, identifiedStorage.copyPolicy());
    assertTrue(identifiedStorage.hasPersistentIdentity());
    assertEquals(Optional.of("legendary"), booster.variantKey());
    assertEquals(ExortItemCopyPolicy.TEMPLATE, booster.copyPolicy());
    assertEquals(ExortItemCopyPolicy.PRESERVE_UNIQUE_IDENTITY, identifiedLoader.copyPolicy());
    assertTrue(identifiedLoader.variantKey().isEmpty());
  }

  @Test
  void rejectsMalformedForeignAndCarrierMismatchesWithoutMutation() {
    Map<NamespacedKey, Object> invalidStorageValues = new HashMap<>();
    invalidStorageValues.put(keys.type(), "storage");
    invalidStorageValues.put(keys.storageTier(), "rare");
    invalidStorageValues.put(keys.storageId(), "not-a-uuid");
    PdcStack invalidStorage = stack(Material.PAPER, invalidStorageValues);
    Map<NamespacedKey, Object> before = Map.copyOf(invalidStorageValues);

    assertTrue(customItems.inspectItem(invalidStorage).isEmpty());
    assertEquals(before, invalidStorageValues);
    assertTrue(
        customItems
            .inspectItem(
                stack(
                    Material.PAPER, Map.of(keys.type(), "storage", keys.storageTier(), "missing")))
            .isEmpty());
    assertTrue(
        customItems
            .inspectItem(
                stack(
                    Material.PAPER,
                    Map.of(keys.type(), "wireless_booster", keys.wirelessBoosterTier(), "unknown")))
            .isEmpty());
    assertTrue(
        customItems
            .inspectItem(
                stack(
                    Material.PAPER,
                    Map.of(keys.type(), "chunk_loader", keys.chunkLoaderId(), "invalid-identity")))
            .isEmpty());
    assertTrue(
        customItems.inspectItem(stack(Material.STONE, Map.of(keys.type(), "wire"))).isEmpty());
    assertTrue(
        customItems.inspectItem(stack(Material.PAPER, Map.of(keys.type(), " wire "))).isEmpty());
    assertTrue(
        customItems.inspectItem(stack(Material.PAPER, Map.of(keys.type(), "foreign"))).isEmpty());

    NamespacedKey foreignType = new NamespacedKey("foreign", "type");
    assertTrue(
        customItems.inspectItem(stack(Material.PAPER, Map.of(foreignType, "wire"))).isEmpty());
    assertTrue(customItems.inspectItem(null).isEmpty());
  }

  private static StorageTierCatalog tierCatalog() {
    YamlConfiguration tiers = new YamlConfiguration();
    tiers.set("rare.maxItems", 128L);
    tiers.set("rare.material", "CHEST");
    tiers.set("rare.name", "Rare");
    return StorageTierCatalog.parse(tiers, LOGGER);
  }

  private static PdcStack stack(Material material, Map<NamespacedKey, Object> values) {
    return new PdcStack(material, values);
  }

  private static final class PdcStack extends ItemStack {
    private final Material material;
    private final ItemMeta meta;

    private PdcStack(Material material, Map<NamespacedKey, Object> values) {
      this.material = material;
      PersistentDataContainer pdc =
          BukkitTestDoubles.proxy(
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
      this.meta =
          BukkitTestDoubles.proxy(
              ItemMeta.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getPersistentDataContainer" -> pdc;
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
}
