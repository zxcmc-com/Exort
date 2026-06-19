package com.zxcmc.exort.items.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class StorageAnvilRenameListenerTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void nonBlankRenameWritesStorageNamePdcAndCustomName() {
    StorageKeys keys = new StorageKeys(BukkitTestDoubles.plugin());
    TestItemStack item = storageItem(keys);
    item.setAmount(8);

    ItemStack result =
        StorageAnvilRenameListener.prepareResult(keys, null, item, null, "  Main Vault  ");

    assertNotSame(item, result);
    assertEquals(1, result.getAmount());
    assertEquals(
        "Main Vault",
        result
            .getItemMeta()
            .getPersistentDataContainer()
            .get(keys.storageName(), PersistentDataType.STRING));
    assertEquals("Storage: Main Vault", plain(result.getItemMeta().customName()));
    assertEquals(
        TextDecoration.State.FALSE,
        result.getItemMeta().customName().decoration(TextDecoration.ITALIC));
    assertEquals(
        TextDecoration.State.TRUE,
        result.getItemMeta().customName().children().get(1).decoration(TextDecoration.ITALIC));
    assertEquals(NamedTextColor.WHITE, result.getItemMeta().customName().children().get(1).color());
    assertFalse(
        item.getItemMeta()
            .getPersistentDataContainer()
            .has(keys.storageName(), PersistentDataType.STRING));
  }

  @Test
  void blankRenameClearsStorageNamePdcAndCustomName() {
    StorageKeys keys = new StorageKeys(BukkitTestDoubles.plugin());
    TestItemStack item = storageItem(keys);
    item.meta.pdc.set(keys.storageName(), "Old Name");
    item.meta.customName = Component.text("Old Name");

    ItemStack result = StorageAnvilRenameListener.prepareResult(keys, null, item, null, "   ");

    assertFalse(
        result
            .getItemMeta()
            .getPersistentDataContainer()
            .has(keys.storageName(), PersistentDataType.STRING));
    assertNull(result.getItemMeta().customName());
  }

  @Test
  void anvilInputShowsOnlyRawStorageName() {
    StorageKeys keys = new StorageKeys(BukkitTestDoubles.plugin());
    TestItemStack item = storageItem(keys);
    item.meta.pdc.set(keys.storageName(), "Main Vault");
    item.meta.customName = Component.text("Storage: Main Vault");

    assertTrue(StorageAnvilRenameListener.showRawNameInAnvilInput(keys, item));

    assertEquals("Main Vault", plain(item.getItemMeta().customName()));
    assertEquals(
        TextDecoration.State.TRUE,
        item.getItemMeta().customName().decoration(TextDecoration.ITALIC));
    assertEquals(
        "Main Vault",
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(keys.storageName(), PersistentDataType.STRING));
  }

  @Test
  void secondInputLeavesVanillaAnvilResultUntouched() {
    StorageKeys keys = new StorageKeys(BukkitTestDoubles.plugin());

    assertNull(
        StorageAnvilRenameListener.prepareResult(
            keys,
            null,
            storageItem(keys),
            new TestItemStack(Material.PAPER, 1, new MetaState()),
            "X"));
  }

  private static TestItemStack storageItem(StorageKeys keys) {
    TestItemStack item = new TestItemStack(Material.PAPER, 1, new MetaState());
    item.meta.pdc.set(keys.type(), "storage");
    return item;
  }

  private static String plain(Component component) {
    return component == null ? null : PLAIN.serialize(component);
  }

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
    private Component customName;
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
              StorageAnvilRenameListenerTest.class.getClassLoader(),
              new Class<?>[] {ItemMeta.class},
              this);
    }

    private MetaState copy() {
      MetaState copy = new MetaState(pdc.copy());
      copy.customName = customName;
      return copy;
    }

    private static MetaState from(ItemMeta meta) {
      return (MetaState) Proxy.getInvocationHandler(meta);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "customName" -> {
          if (args == null || args.length == 0) {
            yield customName;
          }
          customName = (Component) args[0];
          yield null;
        }
        case "hasCustomName" -> customName != null;
        case "getPersistentDataContainer" -> pdc.proxy();
        case "clone" -> copy().proxy();
        case "serialize" -> Map.of();
        default -> BukkitTestDoubles.defaultValue(method.getReturnType());
      };
    }
  }

  private static final class PdcState implements InvocationHandler {
    private final Map<NamespacedKey, Object> values = new LinkedHashMap<>();

    private PersistentDataContainer proxy() {
      return (PersistentDataContainer)
          Proxy.newProxyInstance(
              StorageAnvilRenameListenerTest.class.getClassLoader(),
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
        default -> BukkitTestDoubles.defaultValue(method.getReturnType());
      };
    }
  }
}
