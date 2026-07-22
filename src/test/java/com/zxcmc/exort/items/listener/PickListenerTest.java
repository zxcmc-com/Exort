package com.zxcmc.exort.items.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierTestFixtures;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PickListenerTest {
  @Test
  void vanillaTargetUsesHeldSlotWhenHeldSlotIsEmpty() {
    InventoryState inventory = new InventoryState(4);

    assertEquals(4, PickListener.findVanillaPickTargetSlot(inventory.proxy()));
  }

  @Test
  void vanillaTargetUsesFirstEmptySlotFromHeldSlotWithWrap() {
    InventoryState inventory = new InventoryState(7);
    inventory.set(7, stack("stone"));
    inventory.set(8, stack("cobble"));
    inventory.set(0, stack("chest"));

    assertEquals(1, PickListener.findVanillaPickTargetSlot(inventory.proxy()));
  }

  @Test
  void vanillaTargetUsesNextRightEmptySlotWhenHeldSlotIsOccupied() {
    InventoryState inventory = new InventoryState(4);
    inventory.set(4, stack("stone"));

    assertEquals(5, PickListener.findVanillaPickTargetSlot(inventory.proxy()));
  }

  @Test
  void vanillaTargetUsesFirstNonEnchantedSlotWhenHotbarIsFull() {
    InventoryState inventory = new InventoryState(4);
    for (int slot = 0; slot <= 8; slot++) {
      inventory.set(slot, stack("enchanted-" + slot, true));
    }
    inventory.set(6, stack("plain"));

    assertEquals(6, PickListener.findVanillaPickTargetSlot(inventory.proxy()));
  }

  @Test
  void vanillaTargetKeepsHeldSlotWhenHotbarIsFullOfEnchantedItems() {
    InventoryState inventory = new InventoryState(4);
    for (int slot = 0; slot <= 8; slot++) {
      inventory.set(slot, stack("enchanted-" + slot, true));
    }

    assertEquals(4, PickListener.findVanillaPickTargetSlot(inventory.proxy()));
  }

  @Test
  void existingHotbarPickOnlySelectsTheExistingSlot() {
    InventoryState inventory = new InventoryState(4);
    ItemStack existing = stack("existing");
    ItemStack held = stack("held");
    inventory.set(2, existing);
    inventory.set(4, held);

    PickListener.PickApplyResult result =
        PickListener.pickExistingFromInventory(inventory.proxy(), 2);

    assertEquals("select", result.action());
    assertEquals(2, inventory.held());
    assertSame(existing, inventory.item(2));
    assertSame(held, inventory.item(4));
  }

  @Test
  void existingMainInventoryPickSwapsWholeStackIntoVanillaTargetSlot() {
    InventoryState inventory = new InventoryState(4);
    ItemStack existing = stack("existing", 12);
    ItemStack held = stack("held");
    inventory.set(4, held);
    inventory.set(20, existing);

    PickListener.PickApplyResult result =
        PickListener.pickExistingFromInventory(inventory.proxy(), 20);

    assertEquals("swap", result.action());
    assertEquals(5, result.targetSlot());
    assertEquals(5, inventory.held());
    assertSame(existing, inventory.item(5));
    assertNull(inventory.item(20));
    assertSame(held, inventory.item(4));
  }

  @Test
  void creativeMissWithFreeHotbarSlotPutsNewItemIntoVanillaTargetAndKeepsHeldStack() {
    InventoryState inventory = new InventoryState(4);
    ItemStack held = stack("held");
    inventory.set(4, held);
    int target = PickListener.findVanillaPickTargetSlot(inventory.proxy());

    PickListener.PickApplyResult result =
        PickListener.addAndPickCreative(inventory.proxy(), stack("picked", 7), target);

    assertEquals("give_replace", result.action());
    assertEquals(5, result.targetSlot());
    assertEquals(5, inventory.held());
    assertSame(held, inventory.item(4));
    assertEquals("picked", ((TestStack) inventory.item(5)).id());
    assertEquals(1, inventory.item(5).getAmount());
  }

  @Test
  void creativeMissWithFullHotbarMovesDisplacedTargetStackIntoFirstFreeInventorySlot() {
    InventoryState inventory = new InventoryState(4);
    for (int slot = 0; slot <= 9; slot++) {
      inventory.set(slot, stack("filled-" + slot));
    }
    ItemStack displaced = inventory.item(4);
    int target = PickListener.findVanillaPickTargetSlot(inventory.proxy());

    PickListener.PickApplyResult result =
        PickListener.addAndPickCreative(inventory.proxy(), stack("picked"), target);

    assertEquals("give_move_displaced", result.action());
    assertEquals(4, result.targetSlot());
    assertEquals(10, result.displacedSlot());
    assertEquals(4, inventory.held());
    assertEquals("picked", ((TestStack) inventory.item(4)).id());
    assertSame(displaced, inventory.item(10));
  }

  @Test
  void creativeMissWithFullInventoryReplacesTargetAndReportsReplacedStack() {
    InventoryState inventory = new InventoryState(4);
    for (int slot = 0; slot < 36; slot++) {
      inventory.set(slot, stack("filled-" + slot));
    }
    ItemStack replaced = inventory.item(4);
    int target = PickListener.findVanillaPickTargetSlot(inventory.proxy());

    PickListener.PickApplyResult result =
        PickListener.addAndPickCreative(inventory.proxy(), stack("picked"), target);

    assertEquals("give_replace", result.action());
    assertEquals(4, result.targetSlot());
    assertEquals(-1, result.displacedSlot());
    assertSame(replaced, result.replacedStack());
    assertEquals("picked", ((TestStack) inventory.item(4)).id());
  }

  @Test
  void chunkLoaderPickReusesMatchingTypeRegardlessOfUuid() {
    StorageKeys keys = new StorageKeys(BukkitTestDoubles.plugin());
    CustomItems customItems = customItems(keys);
    ItemStack reusable = chunkLoaderStack(keys, ChunkLoaderType.CHUNK_LOADER, null);
    ItemStack assigned =
        chunkLoaderStack(
            keys,
            ChunkLoaderType.CHUNK_LOADER,
            UUID.fromString("00000000-0000-0000-0000-000000000031"));
    ItemStack wrongType = chunkLoaderStack(keys, ChunkLoaderType.PERSONAL_CHUNK_LOADER, null);

    assertTrue(
        PickListener.isReusableChunkLoader(customItems, reusable, ChunkLoaderType.CHUNK_LOADER));
    assertTrue(
        PickListener.isReusableChunkLoader(customItems, assigned, ChunkLoaderType.CHUNK_LOADER));
    assertFalse(
        PickListener.isReusableChunkLoader(customItems, wrongType, ChunkLoaderType.CHUNK_LOADER));
  }

  @Test
  void survivalChunkLoaderPickSwapsFirstMatchingExistingStackEvenWhenAssigned() {
    Plugin plugin = BukkitTestDoubles.plugin();
    StorageKeys keys = new StorageKeys(plugin);
    CustomItems customItems = customItems(keys);
    PickListener listener = pickListener(plugin, customItems, keys);
    Block block = chunkLoaderBlock(plugin, ChunkLoaderType.PERSONAL_CHUNK_LOADER);
    InventoryState inventory = new InventoryState(4);
    ItemStack held = stack("held");
    ItemStack assigned =
        chunkLoaderStack(
            keys,
            ChunkLoaderType.PERSONAL_CHUNK_LOADER,
            UUID.fromString("00000000-0000-0000-0000-000000000031"));
    ItemStack laterMatch = chunkLoaderStack(keys, ChunkLoaderType.PERSONAL_CHUNK_LOADER, null);
    inventory.set(4, held);
    inventory.set(20, assigned);
    inventory.set(21, laterMatch);
    PlayerState player = new PlayerState(inventory, GameMode.SURVIVAL);
    PlayerPickBlockEvent event = new PlayerPickBlockEvent(player.proxy(), block, false, 4, -1);

    listener.onPick(event);

    assertTrue(event.isCancelled());
    assertEquals(5, inventory.held());
    assertSame(held, inventory.item(4));
    assertSame(assigned, inventory.item(5));
    assertNull(inventory.item(20));
    assertSame(laterMatch, inventory.item(21));
    assertEquals(1, player.updateInventoryCalls());
  }

  @Test
  void survivalChunkLoaderPickDoesNotGiveWhenOnlyDifferentTypeExists() {
    Plugin plugin = BukkitTestDoubles.plugin();
    StorageKeys keys = new StorageKeys(plugin);
    CustomItems customItems = customItems(keys);
    PickListener listener = pickListener(plugin, customItems, keys);
    Block block = chunkLoaderBlock(plugin, ChunkLoaderType.PERSONAL_CHUNK_LOADER);
    InventoryState inventory = new InventoryState(4);
    ItemStack held = stack("held");
    ItemStack wrongType = chunkLoaderStack(keys, ChunkLoaderType.CHUNK_LOADER, null);
    inventory.set(4, held);
    inventory.set(20, wrongType);
    PlayerState player = new PlayerState(inventory, GameMode.SURVIVAL);
    PlayerPickBlockEvent event = new PlayerPickBlockEvent(player.proxy(), block, false, 4, -1);

    listener.onPick(event);

    assertTrue(event.isCancelled());
    assertEquals(4, inventory.held());
    assertSame(held, inventory.item(4));
    assertNull(inventory.item(5));
    assertSame(wrongType, inventory.item(20));
    assertEquals(0, player.updateInventoryCalls());
  }

  @Test
  void survivalStoragePickSwapsExistingSameTierStackWithoutGivingNewItem() {
    Plugin plugin = BukkitTestDoubles.plugin();
    StorageKeys keys = new StorageKeys(plugin);
    CustomItems customItems = customItems(keys);
    PickListener listener = pickListener(plugin, customItems, keys);
    Block block = storageBlock(plugin);
    InventoryState inventory = new InventoryState(4);
    ItemStack held = stack("held");
    ItemStack existing = storageStack(keys, "BASIC", "storage-a");
    inventory.set(4, held);
    inventory.set(20, existing);
    PlayerState player = new PlayerState(inventory, GameMode.SURVIVAL);
    PlayerPickBlockEvent event = new PlayerPickBlockEvent(player.proxy(), block, false, 4, -1);

    listener.onPick(event);

    assertTrue(event.isCancelled());
    assertEquals(5, inventory.held());
    assertSame(held, inventory.item(4));
    assertSame(existing, inventory.item(5));
    assertNull(inventory.item(20));
    assertEquals(1, player.updateInventoryCalls());
  }

  private static TestStack stack(String id) {
    return stack(id, 1);
  }

  private static TestStack stack(String id, int amount) {
    return new TestStack(id, amount, false);
  }

  private static TestStack stack(String id, boolean enchanted) {
    return new TestStack(id, 1, enchanted);
  }

  private static CustomItems customItems(StorageKeys keys) {
    return new TestCustomItems(keys);
  }

  private static PickListener pickListener(
      Plugin plugin, CustomItems customItems, StorageKeys keys) {
    return new PickListener(
        plugin,
        customItems,
        keys,
        ignored -> {},
        Material.CHORUS_PLANT,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        null);
  }

  private static Block chunkLoaderBlock(Plugin plugin, ChunkLoaderType type) {
    BukkitTestDoubles.TestWorld world =
        BukkitTestDoubles.world("pick-chunk-loader-" + UUID.randomUUID(), UUID.randomUUID());
    Block block = world.block(0, 64, 0, Material.BARRIER);
    ChunkLoaderMarker.set(
        plugin, block, UUID.randomUUID(), type, UUID.randomUUID(), "PickTester", 1L, true);
    return block;
  }

  private static Block storageBlock(Plugin plugin) {
    loadStorageTier();
    BukkitTestDoubles.TestWorld world =
        BukkitTestDoubles.world("pick-storage-" + UUID.randomUUID(), UUID.randomUUID());
    Block block = world.block(1, 64, 0, Material.BARRIER);
    StorageMarker.set(
        plugin, block, "storage-block", StorageTierTestFixtures.find("basic").orElseThrow());
    return block;
  }

  private static void loadStorageTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", 64);
    config.set("basic.material", "CHEST");
    config.set("basic.name", "Basic");
    StorageTierTestFixtures.load(config, Logger.getLogger("test"));
  }

  private static ItemStack chunkLoaderStack(StorageKeys keys, ChunkLoaderType type, UUID loaderId) {
    SimplePdc pdc = new SimplePdc();
    pdc.set(keys.type(), type.id());
    if (loaderId != null) {
      pdc.set(keys.chunkLoaderId(), loaderId.toString());
    }
    return new PdcStack(pdc);
  }

  private static ItemStack storageStack(StorageKeys keys, String tier, String storageId) {
    SimplePdc pdc = new SimplePdc();
    pdc.set(keys.type(), "storage");
    pdc.set(keys.storageTier(), tier);
    if (storageId != null) {
      pdc.set(keys.storageId(), storageId);
    }
    return new PdcStack(pdc);
  }

  private static final class PlayerState {
    private final Player proxy;
    private int updateInventoryCalls;

    PlayerState(InventoryState inventory, GameMode gameMode) {
      UUID playerId = UUID.randomUUID();
      this.proxy =
          BukkitTestDoubles.proxy(
              Player.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getInventory" -> inventory.proxy();
                    case "getGameMode" -> gameMode;
                    case "updateInventory" -> {
                      updateInventoryCalls++;
                      yield null;
                    }
                    case "getUniqueId" -> playerId;
                    case "getName" -> "PickTester";
                    case "toString" -> "player(PickTester)";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
    }

    Player proxy() {
      return proxy;
    }

    int updateInventoryCalls() {
      return updateInventoryCalls;
    }
  }

  private static final class TestCustomItems extends CustomItems {
    private final StorageKeys keys;

    TestCustomItems(StorageKeys keys) {
      super(
          keys,
          null,
          com.zxcmc.exort.items.CustomItemModelConfig.empty(),
          com.zxcmc.exort.wireless.WirelessRuntimeConfig.defaults(),
          false,
          StorageTierTestFixtures.current());
      this.keys = keys;
    }

    @Override
    public ItemStack chunkLoaderItem(ChunkLoaderType type) {
      return chunkLoaderStack(keys, type, null);
    }

    @Override
    public ItemStack storageItem(StorageTier tier, String storageId) {
      return storageStack(keys, tier.key(), storageId);
    }
  }

  private static final class InventoryState {
    private final ItemStack[] contents = new ItemStack[36];
    private final PlayerInventory proxy;
    private int held;

    InventoryState(int held) {
      this.held = held;
      this.proxy =
          BukkitTestDoubles.proxy(
              PlayerInventory.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getHeldItemSlot" -> this.held;
                    case "setHeldItemSlot" -> {
                      this.held = (Integer) args[0];
                      yield null;
                    }
                    case "getItem" -> contents[(Integer) args[0]];
                    case "setItem" -> {
                      contents[(Integer) args[0]] = (ItemStack) args[1];
                      yield null;
                    }
                    case "getContents" -> contents.clone();
                    case "toString" -> "inventory(held=" + this.held + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
    }

    PlayerInventory proxy() {
      return proxy;
    }

    int held() {
      return held;
    }

    ItemStack item(int slot) {
      return contents[slot];
    }

    void set(int slot, ItemStack stack) {
      contents[slot] = stack;
    }
  }

  private static final class TestStack extends ItemStack {
    private final String id;
    private final boolean enchanted;
    private int amount;

    TestStack(String id, int amount, boolean enchanted) {
      this.id = id;
      this.amount = amount;
      this.enchanted = enchanted;
    }

    String id() {
      return id;
    }

    @Override
    public Material getType() {
      return Material.STONE;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public boolean hasItemMeta() {
      return false;
    }

    @Override
    public ItemMeta getItemMeta() {
      return null;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public Map<Enchantment, Integer> getEnchantments() {
      return enchanted ? Collections.singletonMap(null, 1) : Map.of();
    }

    @Override
    public ItemStack clone() {
      return new TestStack(id, amount, enchanted);
    }

    @Override
    public String toString() {
      return "stack(" + id + ", amount=" + amount + ", enchanted=" + enchanted + ")";
    }
  }

  private static final class PdcStack extends ItemStack {
    private final ItemMeta meta;

    PdcStack(SimplePdc pdc) {
      this.meta =
          BukkitTestDoubles.proxy(
              ItemMeta.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getPersistentDataContainer" -> pdc.proxy();
                    case "clone" -> proxy;
                    case "toString" -> "pdc-meta";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                  });
    }

    @Override
    public Material getType() {
      return Material.PAPER;
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

    void set(NamespacedKey key, String value) {
      values.put(key, value);
    }

    PersistentDataContainer proxy() {
      return BukkitTestDoubles.proxy(PersistentDataContainer.class, this::invoke);
    }

    private Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
      return switch (method.getName()) {
        case "get" -> values.get((NamespacedKey) args[0]);
        case "has" -> values.containsKey((NamespacedKey) args[0]);
        case "getOrDefault" -> values.getOrDefault((NamespacedKey) args[0], args[2]);
        case "set" -> {
          values.put((NamespacedKey) args[0], args[2]);
          yield null;
        }
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
      return BukkitTestDoubles.proxy(
          PersistentDataAdapterContext.class,
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
