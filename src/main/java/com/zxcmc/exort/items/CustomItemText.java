package com.zxcmc.exort.items;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public final class CustomItemText {
  private static final TextColor CHUNK_LOADER_NAME_COLOR = TextColor.color(0xB28A33);
  private static final TextColor PERSONAL_CHUNK_LOADER_NAME_COLOR = TextColor.color(0x8847FF);
  private static final TextColor DORMANT_CHUNK_LOADER_NAME_COLOR = TextColor.color(0xD32CE6);

  private CustomItemText() {}

  public static Component chunkLoaderName(Component name) {
    return chunkLoaderName(ChunkLoaderType.defaultType(), name);
  }

  public static Component chunkLoaderName(ChunkLoaderType type, Component name) {
    return name.color(chunkLoaderNameColor(type));
  }

  public static TextColor chunkLoaderNameColor() {
    return chunkLoaderNameColor(ChunkLoaderType.defaultType());
  }

  public static TextColor chunkLoaderNameColor(ChunkLoaderType type) {
    return switch (type == null ? ChunkLoaderType.defaultType() : type) {
      case PERSONAL_CHUNK_LOADER -> PERSONAL_CHUNK_LOADER_NAME_COLOR;
      case DORMANT_CHUNK_LOADER -> DORMANT_CHUNK_LOADER_NAME_COLOR;
      case CHUNK_LOADER -> CHUNK_LOADER_NAME_COLOR;
    };
  }
}
