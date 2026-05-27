package com.zxcmc.exort.gui.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SearchableSession;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class GuiSearchCoordinatorTest {
  @Test
  void beginTracksPendingParentAndSwitchingState() {
    GuiSearchCoordinator coordinator = new GuiSearchCoordinator();
    Player player = player(UUID.randomUUID());
    SearchableSession parent = searchableSession();

    coordinator.begin(player, parent);

    assertSame(parent, coordinator.pendingFor(player));
    assertTrue(coordinator.hasPending(player));
    assertTrue(coordinator.isPending(player, parent));
    assertTrue(coordinator.isSwitching(player));
  }

  @Test
  void discardReturnsPendingParentAndClearsState() {
    GuiSearchCoordinator coordinator = new GuiSearchCoordinator();
    Player player = player(UUID.randomUUID());
    SearchableSession parent = searchableSession();
    coordinator.begin(player, parent);

    assertSame(parent, coordinator.discard(player));

    assertNull(coordinator.pendingFor(player));
    assertFalse(coordinator.hasPending(player));
    assertFalse(coordinator.isSwitching(player));
  }

  @Test
  void clearSwitchingKeepsPendingSearch() {
    GuiSearchCoordinator coordinator = new GuiSearchCoordinator();
    Player player = player(UUID.randomUUID());
    SearchableSession parent = searchableSession();
    coordinator.begin(player, parent);

    coordinator.clearSwitching(player);

    assertSame(parent, coordinator.pendingFor(player));
    assertFalse(coordinator.isSwitching(player));
  }

  @Test
  void discardIfParentRequiresMatchingSearchableParent() {
    GuiSearchCoordinator coordinator = new GuiSearchCoordinator();
    Player player = player(UUID.randomUUID());
    SearchableSession parent = searchableSession();
    SearchableSession other = searchableSession();
    coordinator.begin(player, parent);

    assertFalse(coordinator.discardIfParent(player, other));
    assertSame(parent, coordinator.pendingFor(player));

    assertTrue(coordinator.discardIfParent(player, parent));
    assertFalse(coordinator.hasPending(player));
  }

  @Test
  void discardIfParentIgnoresNonSearchableSession() {
    GuiSearchCoordinator coordinator = new GuiSearchCoordinator();
    Player player = player(UUID.randomUUID());
    SearchableSession parent = searchableSession();
    coordinator.begin(player, parent);

    assertFalse(coordinator.discardIfParent(player, guiSession()));

    assertSame(parent, coordinator.pendingFor(player));
  }

  private static Player player(UUID id) {
    return (Player)
        Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> {
              return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> true;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "Player[" + id + "]";
                default -> defaultValue(method.getReturnType());
              };
            });
  }

  private static SearchableSession searchableSession() {
    return (SearchableSession)
        Proxy.newProxyInstance(
            SearchableSession.class.getClassLoader(),
            new Class<?>[] {SearchableSession.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
  }

  private static GuiSession guiSession() {
    return (GuiSession)
        Proxy.newProxyInstance(
            GuiSession.class.getClassLoader(),
            new Class<?>[] {GuiSession.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    return 0;
  }
}
