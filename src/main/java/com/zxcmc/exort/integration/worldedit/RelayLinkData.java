package com.zxcmc.exort.integration.worldedit;

import java.util.UUID;

record RelayLinkData(UUID worldId, int x, int y, int z) {
  RelayLinkData {
    java.util.Objects.requireNonNull(worldId, "worldId");
  }
}
