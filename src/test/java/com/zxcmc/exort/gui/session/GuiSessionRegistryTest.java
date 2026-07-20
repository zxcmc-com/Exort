package com.zxcmc.exort.gui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SessionType;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

class GuiSessionRegistryTest {
  @Test
  void registerIndexesSessionByViewerAndStorage() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    Player player = player(UUID.randomUUID());
    TestSession session = new TestSession(player, "storage-a", SessionType.STORAGE, false);

    registry.register(session);

    assertSame(session, registry.sessionFor(player.getUniqueId()));
    assertEquals(1, registry.sessionsForStorage("storage-a").size());
    assertTrue(registry.sessionsForStorage("storage-a").contains(session));
    assertEquals(1, registry.allSessions().size());
    assertTrue(registry.allSessions().contains(session));
  }

  @Test
  void unregisterPromotesNextSessionForStorage() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    Player writer = player(UUID.randomUUID());
    Player reader = player(UUID.randomUUID());
    TestSession writerSession = new TestSession(writer, "storage-a", SessionType.STORAGE, false);
    TestSession readerSession = new TestSession(reader, "storage-a", SessionType.STORAGE, true);
    registry.register(writerSession);
    registry.register(readerSession);

    GuiSessionRegistry.Removal removal = registry.unregister(writer, null);

    assertSame(writerSession, removal.session());
    assertTrue(removal.storageStillOpen());
    assertFalse(readerSession.isReadOnly());
    assertEquals(1, readerSession.renderCount);
    assertNull(registry.sessionFor(writer.getUniqueId()));
    assertSame(readerSession, registry.sessionFor(reader.getUniqueId()));
  }

  @Test
  void unregisterWithDifferentExpectedSessionKeepsCurrentSession() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    Player player = player(UUID.randomUUID());
    TestSession current = new TestSession(player, "storage-a", SessionType.STORAGE, false);
    TestSession expected = new TestSession(player, "storage-a", SessionType.STORAGE, false);
    registry.register(current);

    assertNull(registry.unregister(player, expected));

    assertSame(current, registry.sessionFor(player.getUniqueId()));
    assertTrue(registry.sessionsForStorage("storage-a").contains(current));
  }

  @Test
  void snapshotsRemainStableWhileRegistryChanges() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    Player firstPlayer = player(UUID.randomUUID());
    TestSession first = new TestSession(firstPlayer, "storage-a", SessionType.STORAGE, true);
    TestSession second =
        new TestSession(player(UUID.randomUUID()), "storage-a", SessionType.STORAGE, true);
    registry.register(first);
    registry.register(second);
    Collection<GuiSession> allSnapshot = registry.allSessions();
    Set<GuiSession> storageSnapshot = registry.sessionsForStorage("storage-a");

    registry.unregister(firstPlayer, null);

    assertEquals(2, allSnapshot.size());
    assertEquals(2, storageSnapshot.size());
    assertEquals(1, registry.allSessions().size());
    assertEquals(1, registry.sessionsForStorage("storage-a").size());
  }

  @Test
  void forcedWriterLocksOthersUntilForcedSessionCloses() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    Player first = player(UUID.randomUUID());
    Player forced = player(UUID.randomUUID());
    TestSession firstSession = new TestSession(first, "storage-a", SessionType.STORAGE, false);
    TestSession forcedSession = new TestSession(forced, "storage-a", SessionType.STORAGE, false);
    registry.register(firstSession);

    registry.makeForcedWriter("storage-a", forced.getUniqueId());
    registry.register(forcedSession);

    assertTrue(firstSession.isReadOnly());
    assertTrue(registry.isModeratorLocked("storage-a", first.getUniqueId()));
    assertFalse(registry.isModeratorLocked("storage-a", forced.getUniqueId()));

    registry.unregister(forced, null);

    assertFalse(firstSession.isReadOnly());
    assertEquals(1, firstSession.renderCount);
    assertFalse(registry.isModeratorLocked("storage-a", first.getUniqueId()));
  }

  @Test
  void forceWriterMakesSelectedSessionWritable() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    TestSession firstSession =
        new TestSession(player(UUID.randomUUID()), "storage-a", SessionType.STORAGE, false);
    TestSession secondSession =
        new TestSession(player(UUID.randomUUID()), "storage-a", SessionType.STORAGE, true);
    registry.register(firstSession);
    registry.register(secondSession);

    assertTrue(registry.forceWriter(secondSession));

    assertTrue(firstSession.isReadOnly());
    assertFalse(secondSession.isReadOnly());
  }

  @Test
  void craftingSessionDetectionFollowsRegisteredSessions() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    Player player = player(UUID.randomUUID());
    TestSession session = new TestSession(player, "storage-a", SessionType.CRAFTING, false);
    registry.register(session);

    assertTrue(registry.hasCraftingSessions("storage-a"));

    registry.unregister(player, null);

    assertFalse(registry.hasCraftingSessions("storage-a"));
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

  private static final class TestSession implements GuiSession {
    private final Player viewer;
    private final String storageId;
    private final SessionType type;
    private boolean readOnly;
    private int renderCount;

    private TestSession(Player viewer, String storageId, SessionType type, boolean readOnly) {
      this.viewer = viewer;
      this.storageId = storageId;
      this.type = type;
      this.readOnly = readOnly;
    }

    @Override
    public SessionType type() {
      return type;
    }

    @Override
    public Player getViewer() {
      return viewer;
    }

    @Override
    public StorageCache getCache() {
      return null;
    }

    @Override
    public StorageTier getTier() {
      return null;
    }

    @Override
    public String getStorageId() {
      return storageId;
    }

    @Override
    public Block getTerminalBlock() {
      return null;
    }

    @Override
    public Location getStorageLocation() {
      return null;
    }

    @Override
    public boolean isReadOnly() {
      return readOnly;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
    }

    @Override
    public void render() {
      renderCount++;
    }

    @Override
    public void onClose() {}

    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}
