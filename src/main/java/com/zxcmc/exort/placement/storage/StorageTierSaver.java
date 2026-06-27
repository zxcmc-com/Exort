package com.zxcmc.exort.placement.storage;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface StorageTierSaver {
  CompletableFuture<Void> save(
      String storageId, String tierKey, long tierMaxItems, String displayName);
}
