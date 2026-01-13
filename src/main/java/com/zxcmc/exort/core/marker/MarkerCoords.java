package com.zxcmc.exort.core.marker;

public final class MarkerCoords {
  private MarkerCoords() {}

  public static int[] parseXYZ(String suffix) {
    if (suffix == null) return null;
    String[] parts = suffix.split("_");
    if (parts.length != 3) return null;
    try {
      return new int[] {
        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
      };
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
