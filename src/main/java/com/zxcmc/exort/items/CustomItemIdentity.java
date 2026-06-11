package com.zxcmc.exort.items;

import java.util.Objects;

public record CustomItemIdentity(String id, String translationKey) {
  public CustomItemIdentity {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(translationKey, "translationKey");
  }

  public String namespacedId() {
    return "exort:" + id;
  }
}
