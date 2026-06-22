package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SortSearchHelperTest {
  @TempDir Path tempDir;

  @Test
  void fixedExortItemMatchesLocalizedPlayerLanguageName() {
    Harness harness = harness();
    TestItemStack terminal = exortItem(harness.keys(), "terminal");
    terminal.meta.itemName = Component.text("Storage Terminal");

    assertTrue(matches(terminal, "терминал хранилища", harness));
    assertEquals(
        "Терминал хранилища",
        SortSearchHelper.sortNameKey(terminal, null, harness.lang(), harness.keys(), "ru_ru"));
  }

  @Test
  void fixedExortItemStillMatchesStableIds() {
    Harness harness = harness();
    TestItemStack terminal = exortItem(harness.keys(), "terminal");

    assertTrue(matches(terminal, "terminal", harness));
    assertTrue(matches(terminal, "exort:terminal", harness));
  }

  @Test
  void storageItemMatchesLocalizedStorageTierAndCustomName() {
    loadRareTier();
    Harness harness = harness();
    TestItemStack storage = exortItem(harness.keys(), "storage");
    storage.meta.itemName = Component.text("Storage");
    storage.meta.pdc.set(harness.keys().storageTier(), "rare");
    storage.meta.pdc.set(harness.keys().storageTierMaxItems(), 45L * 64L * 5L);
    storage.meta.pdc.set(harness.keys().storageName(), "Main Vault");

    assertTrue(matches(storage, "хранилище", harness));
    assertTrue(matches(storage, "редкий", harness));
    assertTrue(matches(storage, "main vault", harness));
    assertTrue(matches(storage, "storage", harness));
    assertTrue(matches(storage, "exort:storage", harness));
    assertEquals(
        "Хранилище",
        SortSearchHelper.sortNameKey(storage, null, harness.lang(), harness.keys(), "ru_ru"));
  }

  @Test
  void vanillaItemsStillUseItemNameServiceDictionary() throws Exception {
    Harness harness = harness();
    ItemNameService itemNames = itemNameService("de_de", Map.of("diamond", "Diamant"));
    TestItemStack diamond = item(Material.DIAMOND);

    assertTrue(
        SortSearchHelper.matchesQuery(
            diamond, List.of("diamant"), itemNames, harness.lang(), harness.keys(), "de_de"));
  }

  private static boolean matches(ItemStack stack, String query, Harness harness) {
    return SortSearchHelper.matchesQuery(
        stack, List.of(query), null, harness.lang(), harness.keys(), "ru_ru");
  }

  private static Harness harness() {
    try {
      StorageKeys keys = new StorageKeys(plugin());
      Constructor<Lang> constructor =
          Lang.class.getDeclaredConstructor(JavaPlugin.class, File.class, Path.class);
      constructor.setAccessible(true);
      Lang lang = constructor.newInstance(null, null, Path.of("src/main/resources"));
      lang.load("en_us");
      return new Harness(keys, lang);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static TestItemStack exortItem(StorageKeys keys, String type) {
    TestItemStack item = item(Material.PAPER);
    item.meta.pdc.set(keys.type(), type);
    return item;
  }

  private static TestItemStack item(Material material) {
    return new TestItemStack(material, new MetaState());
  }

  private static void loadRareTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("rare.maxItems", "5p");
    config.set("rare.material", "CHEST");
    config.set("rare.name", "{tier.rare}");
    StorageTier.loadFromConfig(config, Logger.getLogger("test"));
  }

  private ItemNameService itemNameService(String language, Map<String, String> entries)
      throws Exception {
    Constructor<ItemNameService> constructor =
        ItemNameService.class.getDeclaredConstructor(JavaPlugin.class, File.class);
    constructor.setAccessible(true);
    ItemNameService service = constructor.newInstance(null, tempDir.toFile());
    setField(service, "dictionaries", Map.of(language, entries));
    setField(service, "activeLanguage", "en_us");
    return service;
  }

  private static void setField(ItemNameService service, String fieldName, Object value)
      throws Exception {
    Field field = ItemNameService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(service, value);
  }

  private static Plugin plugin() {
    InvocationHandler handler =
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "getName" -> "Exort";
            case "namespace" -> "exort";
            case "getLogger" -> Logger.getLogger("test");
            case "isEnabled" -> true;
            default -> defaultValue(method.getReturnType());
          };
        };
    return (Plugin)
        Proxy.newProxyInstance(
            SortSearchHelperTest.class.getClassLoader(), new Class<?>[] {Plugin.class}, handler);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == double.class) return 0.0d;
    if (type == float.class) return 0.0f;
    if (type == short.class) return (short) 0;
    if (type == byte.class) return (byte) 0;
    if (type == char.class) return (char) 0;
    return null;
  }

  private record Harness(StorageKeys keys, Lang lang) {}

  private static final class TestItemStack extends ItemStack {
    private final Material material;
    private final MetaState meta;

    private TestItemStack(Material material, MetaState meta) {
      this.material = material;
      this.meta = meta;
    }

    @Override
    public Material getType() {
      return material;
    }

    @Override
    public boolean hasItemMeta() {
      return meta != null;
    }

    @Override
    public ItemMeta getItemMeta() {
      return meta == null ? null : meta.proxy();
    }
  }

  private static final class MetaState implements InvocationHandler {
    private Component itemName;
    private Component displayName;
    private final PdcState pdc = new PdcState();

    private ItemMeta proxy() {
      return (ItemMeta)
          Proxy.newProxyInstance(
              SortSearchHelperTest.class.getClassLoader(), new Class<?>[] {ItemMeta.class}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "itemName" -> {
          if (args == null || args.length == 0) {
            yield itemName;
          }
          itemName = (Component) args[0];
          yield null;
        }
        case "hasItemName" -> itemName != null;
        case "displayName" -> {
          if (args == null || args.length == 0) {
            yield displayName;
          }
          displayName = (Component) args[0];
          yield null;
        }
        case "hasDisplayName" -> displayName != null;
        case "getPersistentDataContainer" -> pdc.proxy();
        case "clone" -> proxy();
        case "serialize" -> Map.of();
        case "addItemFlags", "removeItemFlags" -> null;
        case "getItemFlags" -> Set.of();
        default -> defaultValue(method.getReturnType());
      };
    }
  }

  private static final class PdcState implements InvocationHandler {
    private final Map<NamespacedKey, Object> values = new LinkedHashMap<>();

    private PersistentDataContainer proxy() {
      return (PersistentDataContainer)
          Proxy.newProxyInstance(
              SortSearchHelperTest.class.getClassLoader(),
              new Class<?>[] {PersistentDataContainer.class},
              this);
    }

    private void set(NamespacedKey key, Object value) {
      values.put(key, value);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "set" -> {
          values.put((NamespacedKey) args[0], args[2]);
          yield null;
        }
        case "get" -> values.get((NamespacedKey) args[0]);
        case "getOrDefault" -> values.getOrDefault((NamespacedKey) args[0], args[2]);
        case "has" -> values.containsKey((NamespacedKey) args[0]);
        case "remove" -> {
          values.remove((NamespacedKey) args[0]);
          yield null;
        }
        case "getKeys" -> values.keySet();
        case "isEmpty" -> values.isEmpty();
        case "getSize" -> values.size();
        case "copyTo" -> null;
        case "getAdapterContext" -> (PersistentDataAdapterContext) null;
        case "serializeToBytes" -> new byte[0];
        default -> defaultValue(method.getReturnType());
      };
    }
  }
}
