package com.zxcmc.exort.items;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class CustomItemTextTest {
  @Test
  void chunkLoaderNameColorsAreTypeSpecific() {
    assertEquals(
        TextColor.color(0xB28A33),
        CustomItemText.chunkLoaderNameColor(ChunkLoaderType.CHUNK_LOADER));
    assertEquals(
        TextColor.color(0x8847FF),
        CustomItemText.chunkLoaderNameColor(ChunkLoaderType.PERSONAL_CHUNK_LOADER));
    assertEquals(
        TextColor.color(0xD32CE6),
        CustomItemText.chunkLoaderNameColor(ChunkLoaderType.DORMANT_CHUNK_LOADER));
  }

  @Test
  void chunkLoaderNameAppliesTypeColorToComponent() {
    Component name =
        CustomItemText.chunkLoaderName(
            ChunkLoaderType.DORMANT_CHUNK_LOADER, Component.text("Dormant Chunk Loader"));

    assertEquals(TextColor.color(0xD32CE6), name.color());
  }
}
