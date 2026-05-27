package com.zxcmc.exort.gui.session;

import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SearchableSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class GuiSearchCoordinator {
  private final Map<UUID, SearchableSession> pendingSearch = new HashMap<>();
  private final Set<UUID> switchingToSearch = new HashSet<>();

  public SearchableSession pendingFor(Player player) {
    if (player == null) {
      return null;
    }
    return pendingSearch.get(player.getUniqueId());
  }

  public boolean hasPending(Player player) {
    if (player == null) {
      return false;
    }
    return pendingSearch.containsKey(player.getUniqueId());
  }

  public void begin(Player player, SearchableSession parent) {
    if (player == null || parent == null) {
      return;
    }
    UUID viewerId = player.getUniqueId();
    pendingSearch.put(viewerId, parent);
    switchingToSearch.add(viewerId);
  }

  public boolean isPending(Player player, SearchableSession parent) {
    return pendingFor(player) == parent;
  }

  public boolean isSwitching(Player player) {
    if (player == null) {
      return false;
    }
    return switchingToSearch.contains(player.getUniqueId());
  }

  public SearchableSession discard(Player player) {
    if (player == null) {
      return null;
    }
    UUID viewerId = player.getUniqueId();
    switchingToSearch.remove(viewerId);
    return pendingSearch.remove(viewerId);
  }

  public void clearSwitching(Player player) {
    if (player == null) {
      return;
    }
    switchingToSearch.remove(player.getUniqueId());
  }

  public boolean discardIfParent(Player player, GuiSession parent) {
    if (!(parent instanceof SearchableSession searchable)) {
      return false;
    }
    if (!isPending(player, searchable)) {
      return false;
    }
    discard(player);
    return true;
  }
}
