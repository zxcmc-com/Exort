package com.zxcmc.exort.gui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SessionType;
import com.zxcmc.exort.gui.SortEvent;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

class GuiRenderSchedulerTest {
  @Test
  void requestSchedulesOneFlushForMultipleStorageUpdates() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    CapturingScheduler scheduler = new CapturingScheduler();
    GuiRenderScheduler renderScheduler = new GuiRenderScheduler(registry, scheduler);

    renderScheduler.request("storage-a", SortEvent.DEPOSIT);
    renderScheduler.request("storage-a", SortEvent.NONE);

    assertEquals(1, scheduler.tasks.size());
  }

  @Test
  void flushRendersRegisteredSessionsWithPendingSortEvent() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    CapturingScheduler scheduler = new CapturingScheduler();
    GuiRenderScheduler renderScheduler = new GuiRenderScheduler(registry, scheduler);
    TestSession session = new TestSession(player(UUID.randomUUID()), "storage-a");
    registry.register(session);

    renderScheduler.request("storage-a", SortEvent.DEPOSIT);
    scheduler.tasks.getFirst().run();

    assertEquals(SortEvent.DEPOSIT, session.lastSortEvent);
    assertEquals(1, session.renderCount);
  }

  @Test
  void latestNonNoneSortEventWinsBeforeFlush() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    CapturingScheduler scheduler = new CapturingScheduler();
    GuiRenderScheduler renderScheduler = new GuiRenderScheduler(registry, scheduler);
    TestSession session = new TestSession(player(UUID.randomUUID()), "storage-a");
    registry.register(session);

    renderScheduler.request("storage-a", SortEvent.WITHDRAW);
    renderScheduler.request("storage-a", SortEvent.DEPOSIT);
    renderScheduler.request("storage-a", SortEvent.NONE);
    scheduler.tasks.getFirst().run();

    assertEquals(SortEvent.DEPOSIT, session.lastSortEvent);
    assertEquals(1, session.renderCount);
  }

  @Test
  void snapshotRenderVisitsEverySessionWhenOneUnregistersItself() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    CapturingScheduler scheduler = new CapturingScheduler();
    GuiRenderScheduler renderScheduler = new GuiRenderScheduler(registry, scheduler);
    Player firstPlayer = player(UUID.randomUUID());
    TestSession first =
        new TestSession(
            firstPlayer, "storage-a", true, () -> registry.unregister(firstPlayer, null));
    TestSession second = new TestSession(player(UUID.randomUUID()), "storage-a", true, () -> {});
    registry.register(first);
    registry.register(second);

    renderScheduler.request("storage-a", SortEvent.NONE);
    scheduler.tasks.getFirst().run();

    assertEquals(1, first.renderCount);
    assertEquals(1, second.renderCount);
    assertEquals(1, registry.allSessions().size());
  }

  @Test
  void nullStorageIdDoesNotScheduleFlush() {
    GuiSessionRegistry registry = new GuiSessionRegistry();
    CapturingScheduler scheduler = new CapturingScheduler();
    GuiRenderScheduler renderScheduler = new GuiRenderScheduler(registry, scheduler);

    renderScheduler.request(null, SortEvent.DEPOSIT);

    assertEquals(0, scheduler.tasks.size());
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

  private static final class CapturingScheduler implements GuiRenderScheduler.TaskScheduler {
    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public int schedule(Runnable task) {
      tasks.add(task);
      return tasks.size();
    }
  }

  private static final class TestSession implements GuiSession {
    private final Player viewer;
    private final String storageId;
    private final Runnable onRender;
    private boolean readOnly;
    private SortEvent lastSortEvent = SortEvent.NONE;
    private int renderCount;

    private TestSession(Player viewer, String storageId) {
      this(viewer, storageId, false, () -> {});
    }

    private TestSession(Player viewer, String storageId, boolean readOnly, Runnable onRender) {
      this.viewer = viewer;
      this.storageId = storageId;
      this.readOnly = readOnly;
      this.onRender = onRender;
    }

    @Override
    public SessionType type() {
      return SessionType.STORAGE;
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
      onRender.run();
    }

    @Override
    public void onClose() {}

    @Override
    public void onSortEvent(SortEvent event) {
      lastSortEvent = event;
    }

    @Override
    public Inventory getInventory() {
      return null;
    }
  }
}
