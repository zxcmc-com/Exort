package com.zxcmc.exort.wireless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.runtime.RuntimeItemModelConfig;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class WirelessTerminalServiceStoredChargeTest {
  @Test
  void inactiveStoredTerminalPausesChargingAtComputedCharge() {
    Harness harness = harness();
    TestItemStack terminal = terminal(harness, 25);
    terminal.meta.pdc.set(harness.keys.wirelessStoredAt(), System.currentTimeMillis() - 30_000L);

    WirelessTerminalService.StoredChargeState state =
        harness.service.reconcileStoredCharge(terminal, false);

    assertFalse(state.charging());
    assertFalse(terminal.meta.pdc.has(harness.keys.wirelessStoredAt()));
    assertTrue(state.charge() >= 25);
    assertEquals(state.charge(), harness.service.currentCharge(terminal));
  }

  @Test
  void activePartialTerminalStartsCharging() {
    Harness harness = harness();
    TestItemStack terminal = terminal(harness, 25);

    WirelessTerminalService.StoredChargeState state =
        harness.service.reconcileStoredCharge(terminal, true);

    assertTrue(state.charging());
    assertNotNull(terminal.meta.pdc.get(harness.keys.wirelessStoredAt()));
    assertTrue(state.chargingEndsAtMillis() > System.currentTimeMillis());
  }

  @Test
  void activeFullTerminalDoesNotCharge() {
    Harness harness = harness();
    TestItemStack terminal = terminal(harness, 100);

    WirelessTerminalService.StoredChargeState state =
        harness.service.reconcileStoredCharge(terminal, true);

    assertFalse(state.charging());
    assertFalse(terminal.meta.pdc.has(harness.keys.wirelessStoredAt()));
    assertEquals(100, state.charge());
  }

  @Test
  void reconnectingResumesChargingFromPausedCharge() {
    Harness harness = harness();
    TestItemStack terminal = terminal(harness, 25);

    WirelessTerminalService.StoredChargeState paused =
        harness.service.reconcileStoredCharge(terminal, false);
    assertFalse(paused.charging());

    WirelessTerminalService.StoredChargeState resumed =
        harness.service.reconcileStoredCharge(terminal, true);

    assertTrue(resumed.charging());
    assertEquals(paused.charge(), resumed.charge());
    assertTrue(terminal.meta.pdc.has(harness.keys.wirelessStoredAt()));
  }

  private static Harness harness() {
    Plugin plugin = BukkitTestDoubles.plugin();
    StorageKeys keys = new StorageKeys(plugin);
    Lang lang = new Lang(null);
    RuntimeItemModelConfig itemModels = RuntimeItemModelConfig.forMode(true);
    CustomItems customItems =
        new CustomItems(
            keys,
            lang,
            itemModels.wireItemModel(),
            itemModels.storageItemModel(),
            itemModels.terminalItemModel(),
            itemModels.craftingTerminalItemModel(),
            itemModels.monitorItemModel(),
            itemModels.importBusItemModel(),
            itemModels.exportBusItemModel(),
            itemModels.relayItemModel(),
            itemModels.transmitterItemModel(),
            itemModels.chunkLoaderItemModel(),
            itemModels.personalChunkLoaderItemModel(),
            itemModels.dormantChunkLoaderItemModel(),
            itemModels.wirelessItemModel(),
            itemModels.wirelessDisabledModel(),
            "minecraft:target",
            true);
    return new Harness(keys, new WirelessTerminalService(lang, keys, customItems, true, 48));
  }

  private static TestItemStack terminal(Harness harness, int charge) {
    MetaState meta = new MetaState();
    meta.pdc.set(harness.keys.type(), "wireless_terminal");
    meta.pdc.set(harness.keys.wirelessCharge(), charge);
    return new TestItemStack(meta);
  }

  private record Harness(StorageKeys keys, WirelessTerminalService service) {}

  private static final class TestItemStack extends ItemStack {
    private final MetaState meta;

    TestItemStack(MetaState meta) {
      this.meta = meta;
    }

    @Override
    public Material getType() {
      return Material.SHIELD;
    }

    @Override
    public boolean hasItemMeta() {
      return true;
    }

    @Override
    public ItemMeta getItemMeta() {
      return meta.proxy();
    }

    @Override
    public boolean setItemMeta(ItemMeta itemMeta) {
      return true;
    }

    @Override
    public TestItemStack clone() {
      return new TestItemStack(meta.copy());
    }
  }

  private static final class MetaState implements InvocationHandler {
    private final SimplePdc pdc = new SimplePdc();
    private Component itemName;
    private List<Component> lore;

    ItemMeta proxy() {
      return (ItemMeta)
          Proxy.newProxyInstance(
              WirelessTerminalServiceStoredChargeTest.class.getClassLoader(),
              new Class<?>[] {ItemMeta.class},
              this);
    }

    MetaState copy() {
      MetaState copy = new MetaState();
      copy.pdc.values.putAll(pdc.values);
      copy.itemName = itemName;
      copy.lore = lore;
      return copy;
    }

    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
      return switch (method.getName()) {
        case "getPersistentDataContainer" -> pdc.proxy();
        case "itemName" -> {
          if (args == null || args.length == 0) {
            yield itemName;
          }
          itemName = (Component) args[0];
          yield null;
        }
        case "lore" -> {
          if (args == null || args.length == 0) {
            yield lore;
          }
          @SuppressWarnings("unchecked")
          List<Component> value = (List<Component>) args[0];
          lore = value;
          yield null;
        }
        case "addItemFlags" -> null;
        case "setUnbreakable" -> null;
        case "toString" -> "meta";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> args != null && args.length == 1 && proxy == args[0];
        default -> BukkitTestDoubles.defaultValue(method.getReturnType());
      };
    }
  }

  private static final class SimplePdc {
    private final Map<NamespacedKey, Object> values = new HashMap<>();

    <T> void set(NamespacedKey key, T value) {
      values.put(key, value);
    }

    boolean has(NamespacedKey key) {
      return values.containsKey(key);
    }

    Object get(NamespacedKey key) {
      return values.get(key);
    }

    private PersistentDataContainer proxy() {
      return (PersistentDataContainer)
          Proxy.newProxyInstance(
              WirelessTerminalServiceStoredChargeTest.class.getClassLoader(),
              new Class<?>[] {PersistentDataContainer.class},
              this::invoke);
    }

    private Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
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
        case "isEmpty" -> values.isEmpty();
        case "getKeys" -> Set.copyOf(values.keySet());
        case "getAdapterContext" -> adapterContext();
        case "toString" -> "pdc" + values;
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> args != null && args.length == 1 && proxy == args[0];
        default -> BukkitTestDoubles.defaultValue(method.getReturnType());
      };
    }

    private PersistentDataAdapterContext adapterContext() {
      return (PersistentDataAdapterContext)
          Proxy.newProxyInstance(
              WirelessTerminalServiceStoredChargeTest.class.getClassLoader(),
              new Class<?>[] {PersistentDataAdapterContext.class},
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "newPersistentDataContainer" -> new SimplePdc().proxy();
                    case "toString" -> "pdc-adapter";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
    }
  }
}
