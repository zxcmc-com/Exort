package com.zxcmc.exort.integration.worldedit;

record MonitorData(String facing, String itemKey, byte[] itemBlob) implements FacingOwner {
  int estimatedPayloadBytes() {
    return itemBlob == null ? 0 : itemBlob.length;
  }
}
