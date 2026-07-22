package com.zxcmc.exort.storage;

/** Supplies the immutable tier catalog from the currently published runtime generation. */
public interface StorageTierCatalogSource {
  StorageTierCatalog storageTierCatalog();
}
