package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.storage.StorageCache;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class StorageDisplayListBuilderTest {
  @Test
  void capsExpandedDisplayEntries() {
    ItemStack sample = new TestItemStack();
    StorageCache.StorageItem item =
        new StorageCache.StorageItem("minecraft:stone", sample, 640_064L, 1L, null);

    StorageDisplayListBuilder.Result result =
        StorageDisplayListBuilder.build(
            List.of(item),
            SortMode.AMOUNT,
            false,
            List.of(),
            SearchQuery.empty(),
            null,
            0,
            45,
            10,
            StorageCache.StorageItem::sample);

    assertEquals(45, result.displayList().size());
    assertTrue(result.truncated());
  }

  @Test
  void buildsOnlyRequestedPageWindowWithVirtualSize() {
    ItemStack sample = new TestItemStack();
    StorageCache.StorageItem item =
        new StorageCache.StorageItem("minecraft:stone", sample, 6_400L, 1L, null);

    StorageDisplayListBuilder.Result result =
        StorageDisplayListBuilder.build(
            List.of(item),
            SortMode.AMOUNT,
            false,
            List.of(),
            SearchQuery.empty(),
            null,
            1,
            45,
            StorageDisplayListBuilder.DEFAULT_MAX_DISPLAY_ENTRIES,
            StorageCache.StorageItem::sample);

    assertEquals(100, result.displayList().size());
    assertNull(result.displayList().get(44));
    assertEquals(64, result.displayList().get(45).amount());
  }

  @Test
  void categoryModeAllowsVisibleNullPadding() {
    ItemStack sample = new TestItemStack();
    StorageCache.StorageItem item =
        new StorageCache.StorageItem("minecraft:stone", sample, 64L, 1L, null);

    StorageDisplayListBuilder.Result result =
        StorageDisplayListBuilder.build(
            List.of(item),
            SortMode.CATEGORY,
            false,
            List.of(),
            SearchQuery.from("diamond"),
            null,
            0,
            9,
            StorageDisplayListBuilder.DEFAULT_MAX_DISPLAY_ENTRIES,
            StorageCache.StorageItem::sample);

    assertEquals(10, result.displayList().size());
    assertNull(result.displayList().get(0));
    assertNull(result.displayCategories().get(0));
    assertEquals(0, result.searchResultsCount());
  }

  private static final class TestItemStack extends ItemStack {
    @Override
    public Material getType() {
      return Material.STONE;
    }

    @Override
    public int getMaxStackSize() {
      return 64;
    }

    @Override
    public ItemMeta getItemMeta() {
      return null;
    }
  }
}
