package com.zxcmc.exort.core.protocol;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProtocolLibCompatibility {
  private static final Pattern VERSION_PREFIX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
  private static final Version MIN_1_21 = new Version(5, 4, 0);
  private static final Version MIN_26 = new Version(5, 5, 0);

  private ProtocolLibCompatibility() {}

  public static Optional<Version> parseVersion(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    Matcher matcher = VERSION_PREFIX.matcher(raw.trim());
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new Version(
              Integer.parseInt(matcher.group(1)),
              Integer.parseInt(matcher.group(2)),
              Integer.parseInt(matcher.group(3))));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  public static Optional<Version> recommendedMinimum(String minecraftVersion) {
    Optional<Version> parsed = parseVersion(minecraftVersion);
    if (parsed.isEmpty()) {
      return Optional.empty();
    }
    Version version = parsed.get();
    if (version.major() == 26) {
      return Optional.of(MIN_26);
    }
    if (version.major() == 1
        && version.minor() == 21
        && version.patch() >= 7
        && version.patch() <= 11) {
      return Optional.of(MIN_1_21);
    }
    return Optional.empty();
  }

  public static boolean isBelowRecommendedMinimum(
      String minecraftVersion, String protocolLibVersion) {
    Optional<Version> minimum = recommendedMinimum(minecraftVersion);
    Optional<Version> actual = parseVersion(protocolLibVersion);
    return minimum.isPresent() && actual.isPresent() && actual.get().compareTo(minimum.get()) < 0;
  }

  public static String failureAdvice(String minecraftVersion, String protocolLibVersion) {
    Optional<Version> minimum = recommendedMinimum(minecraftVersion);
    Optional<Version> actual = parseVersion(protocolLibVersion);
    if (minimum.isPresent() && actual.isPresent() && actual.get().compareTo(minimum.get()) < 0) {
      return "Update ProtocolLib to " + minimum.get() + "+ for Minecraft " + minecraftVersion + ".";
    }
    return "This ProtocolLib build does not expose the required packet API; using fallback.";
  }

  public record Version(int major, int minor, int patch) implements Comparable<Version> {
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

    @Override
    public String toString() {
      return major + "." + minor + "." + patch;
    }
  }
}
