package com.zxcmc.exort.chunkloader;

public enum ChunkLoaderRuntimeState {
  TICKETED("ticketed"),
  FEATURE_DISABLED("feature_disabled"),
  DISABLED("disabled"),
  REGISTERED("registered"),
  SLEEPING("sleeping"),
  OWNER_GRACE("owner_grace"),
  OWNER_OFFLINE("owner_offline"),
  WORLD_UNAVAILABLE("world_unavailable"),
  MISSING("missing");

  private final String id;

  ChunkLoaderRuntimeState(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
