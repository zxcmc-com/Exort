package com.zxcmc.exort.gui;

import java.util.List;

final class StorageSortOrderState {
  private List<String> snapshot = List.of();

  List<String> snapshot() {
    return snapshot;
  }

  void publish(List<String> sortOrder) {
    snapshot = List.copyOf(sortOrder);
  }

  void reset() {
    snapshot = List.of();
  }
}
