package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkLoaderTypeTest {
  @Test
  void parsesKnownFixedIdsCaseInsensitively() {
    assertEquals(
        ChunkLoaderType.CHUNK_LOADER, ChunkLoaderType.fromId("CHUNK_LOADER").orElseThrow());
    assertEquals(
        ChunkLoaderType.PERSONAL_CHUNK_LOADER,
        ChunkLoaderType.fromId("personal_chunk_loader").orElseThrow());
    assertEquals(
        ChunkLoaderType.DORMANT_CHUNK_LOADER,
        ChunkLoaderType.fromId("Dormant_Chunk_Loader").orElseThrow());
  }

  @Test
  void nullableIdsDefaultOnlyWhenMissing() {
    assertEquals(ChunkLoaderType.CHUNK_LOADER, ChunkLoaderType.fromNullableId(null).orElseThrow());
    assertEquals(ChunkLoaderType.CHUNK_LOADER, ChunkLoaderType.fromNullableId(" ").orElseThrow());
    assertFalse(ChunkLoaderType.fromNullableId("unknown").isPresent());
  }

  @Test
  void rejectsRemovedVariantIds() {
    assertFalse(ChunkLoaderType.fromId("immortal").isPresent());
    assertFalse(ChunkLoaderType.fromId("personal").isPresent());
    assertFalse(ChunkLoaderType.fromId("dormant").isPresent());
    assertFalse(ChunkLoaderType.fromNullableId("immortal").isPresent());
    assertFalse(ChunkLoaderType.fromNullableId("personal").isPresent());
    assertFalse(ChunkLoaderType.fromNullableId("dormant").isPresent());
    assertTrue(ChunkLoaderType.isChunkLoaderId("chunk_loader"));
  }
}
