package com.zxcmc.exort.storage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface StorageClaimStore {
  CompletableFuture<List<StorageClaim>> loadStorageClaims();

  CompletableFuture<Void> insertStorageClaim(
      StorageClaim claim, String tierKey, long tierMaxItems, String displayName);

  CompletableFuture<Boolean> deleteStorageClaimExact(
      String storageId, StorageClaimLocation location);

  CompletableFuture<Boolean> moveStorageClaimExact(StorageClaim source, StorageClaim destination);
}
