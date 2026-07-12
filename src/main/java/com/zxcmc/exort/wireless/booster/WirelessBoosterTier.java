package com.zxcmc.exort.wireless.booster;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.format.TextColor;

public enum WirelessBoosterTier {
  RARE("rare", "#4b69ff", 1.5D),
  MYTHICAL("mythical", "#8847ff", 2.0D),
  LEGENDARY("legendary", "#d32ce6", 6.0D),
  IMMORTAL("immortal", "#b28a33", -1.0D);

  private final String id;
  private final TextColor color;
  private final double defaultRangeMultiplier;

  WirelessBoosterTier(String id, String color, double defaultRangeMultiplier) {
    this.id = id;
    this.color = TextColor.fromHexString(color);
    this.defaultRangeMultiplier = defaultRangeMultiplier;
  }

  public String id() {
    return id;
  }

  public String translationKey() {
    return "tier." + id;
  }

  public TextColor color() {
    return color;
  }

  public double defaultRangeMultiplier() {
    return defaultRangeMultiplier;
  }

  public String resourceModelId() {
    return "wireless_booster/" + id;
  }

  public static List<WirelessBoosterTier> all() {
    return List.of(values());
  }

  public static Optional<WirelessBoosterTier> fromId(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (WirelessBoosterTier tier : values()) {
      if (tier.id.equals(normalized)) {
        return Optional.of(tier);
      }
    }
    return Optional.empty();
  }
}
