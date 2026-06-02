package com.zxcmc.exort.integration.auth;

import org.bukkit.entity.Player;

public interface AuthenticationGate {
  boolean blocks(Player player);

  static AuthenticationGate allowAll() {
    return player -> false;
  }
}
