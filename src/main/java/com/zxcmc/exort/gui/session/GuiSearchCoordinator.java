package com.zxcmc.exort.gui.session;

import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SearchableSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class GuiSearchCoordinator {
  private final Map<UUID, SearchableSession> pendingSearch = new HashMap<>();
  private final Map<UUID, SearchableSession> closeProtectedSearch = new HashMap<>();

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
    closeProtectedSearch.put(viewerId, parent);
  }

  public boolean isPending(Player player, SearchableSession parent) {
    return pendingFor(player) == parent;
  }

  public SearchableSession discard(Player player) {
    if (player == null) {
      return null;
    }
    UUID viewerId = player.getUniqueId();
    SearchableSession pending = pendingSearch.remove(viewerId);
    closeProtectedSearch.remove(viewerId);
    return pending;
  }

  public SearchableSession complete(Player player) {
    if (player == null) {
      return null;
    }
    UUID viewerId = player.getUniqueId();
    return pendingSearch.remove(viewerId);
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

  public boolean protectsClose(Player player, GuiSession parent) {
    if (!(parent instanceof SearchableSession searchable) || player == null) {
      return false;
    }
    return closeProtectedSearch.get(player.getUniqueId()) == searchable;
  }

  public void clearCloseProtectionIfParent(Player player, SearchableSession parent) {
    if (player == null || parent == null) {
      return;
    }
    UUID viewerId = player.getUniqueId();
    if (closeProtectedSearch.get(viewerId) == parent) {
      closeProtectedSearch.remove(viewerId);
    }
  }
}
