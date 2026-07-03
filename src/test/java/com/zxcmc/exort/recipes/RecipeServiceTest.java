package com.zxcmc.exort.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.items.CustomItems;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RecipeServiceTest {
  @Test
  void parseNamespacedKeyReturnsNullForBlankOrMalformedInput() {
    assertNull(RecipeService.parseNamespacedKey(null));
    assertNull(RecipeService.parseNamespacedKey(" "));
    assertNull(RecipeService.parseNamespacedKey("bad key"));
    assertNull(RecipeService.parseNamespacedKey("minecraft:bad key"));
  }

  @Test
  void parseNamespacedKeyNormalizesUppercaseInput() {
    var key = RecipeService.parseNamespacedKey("Minecraft:Stone");

    assertNotNull(key);
    assertEquals("minecraft", key.getNamespace());
    assertEquals("stone", key.getKey());
  }

  @Test
  void resolvesChunkLoaderFixedIdsAndRejectsOldVariantIds() {
    RecordingCustomItems customItems = new RecordingCustomItems();
    RecipeService service = new RecipeService(null, customItems, null);

    assertNotNull(service.resolveExortItem("exort:chunk_loader"));
    assertEquals(ChunkLoaderType.CHUNK_LOADER, customItems.lastType);

    assertNotNull(service.resolveExortItem("personal_chunk_loader"));
    assertEquals(ChunkLoaderType.PERSONAL_CHUNK_LOADER, customItems.lastType);

    assertNotNull(service.resolveExortItem("dormant_chunk_loader"));
    assertEquals(ChunkLoaderType.DORMANT_CHUNK_LOADER, customItems.lastType);

    assertNull(service.resolveExortItem("exort:chunk_loader:personal"));
  }

  private static final class RecordingCustomItems extends CustomItems {
    private ChunkLoaderType lastType;

    private RecordingCustomItems() {
      super(null, null, "", "", "", "", "", "", "", "", "", "", "", "", "", "", false);
    }

    @Override
    public ItemStack chunkLoaderItem(ChunkLoaderType type) {
      lastType = type;
      return new TestItemStack();
    }
  }

  private static final class TestItemStack extends ItemStack {}
}
