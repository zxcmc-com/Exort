package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class ChunkLoaderItemSnapshotTest {
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final ChunkLoaderItemSnapshot.Resolver RESOLVER =
      new ChunkLoaderItemSnapshot.Resolver() {
        @Override
        public boolean isChunkLoader(ItemStack stack) {
          return stack instanceof TestItemStack test && test.chunkLoader;
        }

        @Override
        public Optional<UUID> chunkLoaderId(ItemStack stack) {
          return stack instanceof TestItemStack test
              ? Optional.ofNullable(test.id)
              : Optional.empty();
        }

        @Override
        public ChunkLoaderType chunkLoaderType(ItemStack stack) {
          return stack instanceof TestItemStack test
              ? test.loaderType
              : ChunkLoaderType.defaultType();
        }
      };

  @Test
  void countsChunkLoadersInsideReadableNestedContainers() {
    ChunkLoaderItemSnapshot snapshot =
        ChunkLoaderItemSnapshot.of(
            List.of(bundle(loader(FIRST, 2)), shulker(loader(SECOND, 1))), RESOLVER);

    assertEquals(2, snapshot.count(FIRST));
    assertEquals(1, snapshot.count(SECOND));
  }

  @Test
  void supportsUnassignedChunkLoaderWithoutUuid() {
    ChunkLoaderItemSnapshot snapshot =
        ChunkLoaderItemSnapshot.of(List.of(loader(null, 1)), RESOLVER);

    assertEquals(1, snapshot.count((UUID) null));
  }

  @Test
  void keepsUnassignedChunkLoaderTypesSeparate() {
    ChunkLoaderItemSnapshot snapshot =
        ChunkLoaderItemSnapshot.of(
            List.of(
                loader(null, ChunkLoaderType.PERSONAL_CHUNK_LOADER, 1),
                loader(null, ChunkLoaderType.DORMANT_CHUNK_LOADER, 2)),
            RESOLVER);

    assertEquals(
        1,
        snapshot.count(
            new ChunkLoaderItemSnapshot.Key(null, ChunkLoaderType.PERSONAL_CHUNK_LOADER)));
    assertEquals(
        2,
        snapshot.count(
            new ChunkLoaderItemSnapshot.Key(null, ChunkLoaderType.DORMANT_CHUNK_LOADER)));
    assertEquals(3, snapshot.count((UUID) null));
  }

  private static TestItemStack loader(UUID id, int amount) {
    return loader(id, ChunkLoaderType.defaultType(), amount);
  }

  private static TestItemStack loader(UUID id, ChunkLoaderType type, int amount) {
    TestItemStack stack = new TestItemStack(Material.PAPER, amount);
    stack.chunkLoader = true;
    stack.id = id;
    stack.loaderType = type;
    return stack;
  }

  private static TestItemStack bundle(ItemStack... items) {
    TestItemStack stack = new TestItemStack(Material.BUNDLE, 1);
    List<ItemStack> nested = List.copyOf(Arrays.asList(items));
    stack.meta =
        (BundleMeta)
            Proxy.newProxyInstance(
                ChunkLoaderItemSnapshotTest.class.getClassLoader(),
                new Class<?>[] {BundleMeta.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "hasItems" -> !nested.isEmpty();
                      case "getItems" -> nested;
                      case "clone" -> proxy;
                      default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                    });
    return stack;
  }

  private static TestItemStack shulker(ItemStack... items) {
    TestItemStack stack = new TestItemStack(Material.SHULKER_BOX, 1);
    Inventory inventory =
        BukkitTestDoubles.proxy(
            Inventory.class,
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getContents" -> items;
                  case "toString" -> "inventory(shulker)";
                  default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                });
    Object state =
        Proxy.newProxyInstance(
            ChunkLoaderItemSnapshotTest.class.getClassLoader(),
            new Class<?>[] {BlockState.class, InventoryHolder.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getInventory" -> inventory;
                  case "toString" -> "state(shulker)";
                  default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                });
    stack.meta =
        (BlockStateMeta)
            Proxy.newProxyInstance(
                ChunkLoaderItemSnapshotTest.class.getClassLoader(),
                new Class<?>[] {BlockStateMeta.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "hasBlockState" -> true;
                      case "getBlockState" -> state;
                      case "clone" -> proxy;
                      default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                    });
    return stack;
  }

  private static final class TestItemStack extends ItemStack {
    private final Material material;
    private final int amount;
    private boolean chunkLoader;
    private UUID id;
    private ChunkLoaderType loaderType = ChunkLoaderType.defaultType();
    private ItemMeta meta;

    private TestItemStack(Material material, int amount) {
      this.material = material;
      this.amount = amount;
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
    public boolean hasItemMeta() {
      return meta != null;
    }

    @Override
    public ItemMeta getItemMeta() {
      return meta;
    }
  }
}
