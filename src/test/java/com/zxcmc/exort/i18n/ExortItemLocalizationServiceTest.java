package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ExortItemLocalizationServiceTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void localizesExortItemNameForRequestedLanguageWithoutMutatingOriginal() {
    Harness harness = harness("en_us");
    TestItemStack item = item(Material.PAPER, 7);
    item.meta.pdc.set(harness.keys.type(), "storage_core");
    item.meta.itemName = Component.text("Storage Core");

    ItemStack localized = harness.service.localize(item, "de_de");

    assertNotSame(item, localized);
    assertEquals(7, localized.getAmount());
    assertEquals("Storage Core", plain(item.getItemMeta().itemName()));
    assertNotEquals(
        plain(item.getItemMeta().itemName()), plain(localized.getItemMeta().itemName()));
    assertEquals(
        "storage_core",
        localized
            .getItemMeta()
            .getPersistentDataContainer()
            .get(harness.keys.type(), PersistentDataType.STRING));
  }

  @Test
  void fallsBackToConfiguredLanguageForUnknownLocale() {
    Harness harness = harness("ru_ru");
    TestItemStack item = item(Material.PAPER, 1);
    item.meta.pdc.set(harness.keys.type(), "terminal");
    item.meta.itemName = Component.text("Терминал хранилища");

    ItemStack localized = harness.service.localize(item, "zz_zz");

    assertEquals("Терминал хранилища", plain(localized.getItemMeta().itemName()));
  }

  @Test
  void localizesWirelessLoreAndKeepsOriginalStackUntouched() {
    Harness harness = harness("en_us");
    TestItemStack item = item(Material.SHIELD, 1);
    item.meta.pdc.set(harness.keys.type(), "wireless_terminal");
    item.meta.pdc.set(harness.keys.wirelessCharge(), 42);
    item.meta.itemName = Component.text("Wireless Terminal");
    item.meta.lore = List.of(Component.text("Battery: 42%"), Component.text("Not linked"));

    ItemStack localized = harness.service.localize(item, "de_de");

    assertNotSame(item, localized);
    assertEquals("Wireless Terminal", plain(item.getItemMeta().itemName()));
    assertEquals(List.of("Battery: 42%", "Not linked"), lore(item));
    assertNotEquals(
        plain(item.getItemMeta().itemName()), plain(localized.getItemMeta().itemName()));
    assertEquals(2, lore(localized).size());
    assertTrue(lore(localized).getFirst().contains("42"));
  }

  @Test
  void localizesStorageNameAndTierLoreForRequestedLanguage() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("rare.maxItems", "5p");
    config.set("rare.material", "CHEST");
    config.set("rare.name", "{tier.rare}");
    config.set("rare.color", "red");
    StorageTier.loadFromConfig(config, Logger.getLogger("test"));

    Harness harness = harness("en_us");
    TestItemStack item = item(Material.PAPER, 1);
    item.meta.pdc.set(harness.keys.type(), "storage");
    item.meta.pdc.set(harness.keys.storageTier(), "rare");
    item.meta.pdc.set(harness.keys.storageTierMaxItems(), 45L * 64L * 5L);
    item.meta.pdc.set(harness.keys.nestedCount(), 64L);
    item.meta.pdc.set(harness.keys.storageName(), "Main Vault");
    item.meta.itemName = Component.text("Rare Storage");
    item.meta.customName = Component.text("Main Vault");
    item.meta.lore = List.of(Component.text("64 / 14,400 (0.4%)"));

    ItemStack localized = harness.service.localize(item, "ru_ru");

    assertNotSame(item, localized);
    assertEquals("Rare Storage", plain(item.getItemMeta().itemName()));
    assertEquals(List.of("64 / 14,400 (0.4%)"), lore(item));
    assertFalse(plain(localized.getItemMeta().itemName()).isBlank());
    assertTrue(plain(localized.getItemMeta().customName()).contains("Main Vault"));
    assertEquals(
        TextDecoration.State.FALSE,
        localized.getItemMeta().customName().decoration(TextDecoration.ITALIC));
    assertEquals(
        TextDecoration.State.TRUE,
        localized.getItemMeta().customName().children().get(1).decoration(TextDecoration.ITALIC));
    assertEquals(
        NamedTextColor.WHITE, localized.getItemMeta().customName().children().get(1).color());
    assertEquals(NamedTextColor.RED, firstColor(localized.getItemMeta().itemName()));
    assertEquals(2, lore(localized).size());
    assertEquals("64 / 14,400 (0.4%)", lore(localized).getFirst());
    assertFalse(lore(localized).getLast().isBlank());
    assertEquals(NamedTextColor.RED, firstColor(localized.getItemMeta().lore().getLast()));
  }

  @Test
  void ignoresNonExortItems() {
    Harness harness = harness("en_us");
    TestItemStack item = item(Material.STONE, 1);

    assertSame(item, harness.service.localize(item, "de_de"));
  }

  private static Harness harness(String configuredLanguage) {
    StorageKeys keys = new StorageKeys(plugin());
    Lang lang = new Lang(null, null, Path.of("src/main/resources"));
    lang.load(configuredLanguage);
    return new Harness(keys, new ExortItemLocalizationService(keys, lang));
  }

  private static Plugin plugin() {
    InvocationHandler handler =
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "getName" -> "Exort";
            case "namespace" -> "exort";
            case "getLogger" -> Logger.getLogger("test");
            case "isEnabled" -> true;
            case "getDescription" -> null;
            case "getCommand" -> null;
            case "onCommand" -> false;
            case "onTabComplete" -> List.of();
            default -> defaultValue(method.getReturnType());
          };
        };
    return (Plugin)
        Proxy.newProxyInstance(
            ExortItemLocalizationServiceTest.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            handler);
  }

  private static TestItemStack item(Material material, int amount) {
    return new TestItemStack(material, amount, new MetaState());
  }

  private static String plain(Component component) {
    return PLAIN.serialize(component);
  }

  private static List<String> lore(ItemStack item) {
    List<Component> lore = item.getItemMeta().lore();
    if (lore == null) {
      return List.of();
    }
    return lore.stream().map(ExortItemLocalizationServiceTest::plain).toList();
  }

  private static TextColor firstColor(Component component) {
    TextColor color = component.color();
    if (color != null) {
      return color;
    }
    for (Component child : component.children()) {
      TextColor childColor = firstColor(child);
      if (childColor != null) {
        return childColor;
      }
    }
    return null;
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

  private record Harness(StorageKeys keys, ExortItemLocalizationService service) {}

  private static final class TestItemStack extends ItemStack {
    private final Material material;
    private int amount;
    private MetaState meta;

    private TestItemStack(Material material, int amount, MetaState meta) {
      this.material = material;
      this.amount = amount;
      this.meta = meta;
    }

    @Override
    public Material getType() {
      return material;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public boolean hasItemMeta() {
      return meta != null;
    }

    @Override
    public ItemMeta getItemMeta() {
      return meta == null ? null : meta.copy().proxy();
    }

    @Override
    public boolean setItemMeta(ItemMeta itemMeta) {
      meta = MetaState.from(itemMeta).copy();
      return true;
    }

    @Override
    public ItemStack clone() {
      return new TestItemStack(material, amount, meta == null ? null : meta.copy());
    }
  }

  private static final class MetaState implements InvocationHandler {
    private Component itemName;
    private Component customName;
    private List<Component> lore;
    private final PdcState pdc;

    private MetaState() {
      this(new PdcState());
    }

    private MetaState(PdcState pdc) {
      this.pdc = pdc;
    }

    private ItemMeta proxy() {
      return (ItemMeta)
          Proxy.newProxyInstance(
              ExortItemLocalizationServiceTest.class.getClassLoader(),
              new Class<?>[] {ItemMeta.class},
              this);
    }

    private MetaState copy() {
      MetaState copy = new MetaState(pdc.copy());
      copy.itemName = itemName;
      copy.customName = customName;
      copy.lore = lore == null ? null : new ArrayList<>(lore);
      return copy;
    }

    private static MetaState from(ItemMeta meta) {
      InvocationHandler handler = Proxy.getInvocationHandler(meta);
      return (MetaState) handler;
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
        case "customName" -> {
          if (args == null || args.length == 0) {
            yield customName;
          }
          customName = (Component) args[0];
          yield null;
        }
        case "hasCustomName" -> customName != null;
        case "lore" -> {
          if (args == null || args.length == 0) {
            yield lore;
          }
          @SuppressWarnings("unchecked")
          List<Component> next = (List<Component>) args[0];
          lore = next == null ? null : new ArrayList<>(next);
          yield null;
        }
        case "hasLore" -> lore != null && !lore.isEmpty();
        case "getPersistentDataContainer" -> pdc.proxy();
        case "clone" -> copy().proxy();
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
              ExortItemLocalizationServiceTest.class.getClassLoader(),
              new Class<?>[] {PersistentDataContainer.class},
              this);
    }

    private PdcState copy() {
      PdcState copy = new PdcState();
      copy.values.putAll(values);
      return copy;
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
