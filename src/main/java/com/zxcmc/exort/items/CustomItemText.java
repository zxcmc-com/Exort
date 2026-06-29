package com.zxcmc.exort.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public final class CustomItemText {
  private static final TextColor CHUNK_LOADER_NAME_COLOR = TextColor.color(0xFF55FF);

  private CustomItemText() {}

  public static Component chunkLoaderName(Component name) {
    return name.color(CHUNK_LOADER_NAME_COLOR);
  }

  public static TextColor chunkLoaderNameColor() {
    return CHUNK_LOADER_NAME_COLOR;
  }
}
