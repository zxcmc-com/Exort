package io.papermc.paper.configuration;

public final class GlobalConfiguration {
  private static GlobalConfiguration instance;

  public final BlockUpdates blockUpdates = new BlockUpdates();

  private GlobalConfiguration(boolean disableChorusPlantUpdates) {
    blockUpdates.disableChorusPlantUpdates = disableChorusPlantUpdates;
  }

  public static GlobalConfiguration get() {
    return instance;
  }

  public static void setRuntimeDisabled(boolean disabled) {
    instance = new GlobalConfiguration(disabled);
  }

  public static void reset() {
    instance = null;
  }

  public static final class BlockUpdates {
    public boolean disableChorusPlantUpdates;
  }
}
