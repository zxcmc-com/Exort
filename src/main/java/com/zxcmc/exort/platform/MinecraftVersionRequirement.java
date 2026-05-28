package com.zxcmc.exort.platform;

import java.util.Optional;

public final class MinecraftVersionRequirement {
  private final Version minimum;

  private MinecraftVersionRequirement(Version minimum) {
    this.minimum = minimum;
  }

  public static MinecraftVersionRequirement atLeast(int major, int minor, int patch) {
    return new MinecraftVersionRequirement(new Version(major, minor, patch));
  }

  public boolean accepts(Version version) {
    return version.compareTo(minimum) >= 0;
  }

  public String displayName() {
    return minimum.displayName() + "+";
  }

  public record Version(int major, int minor, int patch) implements Comparable<Version> {
    public static Optional<Version> parse(String raw) {
      if (raw == null) {
        return Optional.empty();
      }
      String[] parts = raw.split("\\.");
      if (parts.length < 2) {
        return Optional.empty();
      }
      try {
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9].*$", "")) : 0;
        return Optional.of(new Version(major, minor, patch));
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
    }

    public String displayName() {
      return major + "." + minor + "." + patch;
    }

    @Override
    public int compareTo(Version other) {
      if (major != other.major) {
        return Integer.compare(major, other.major);
      }
      if (minor != other.minor) {
        return Integer.compare(minor, other.minor);
      }
      return Integer.compare(patch, other.patch);
    }
  }
}
