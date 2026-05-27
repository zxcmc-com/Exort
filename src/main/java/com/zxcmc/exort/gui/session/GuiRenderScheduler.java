package com.zxcmc.exort.gui.session;

import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SortEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GuiRenderScheduler {
  private final GuiSessionRegistry registry;
  private final TaskScheduler taskScheduler;
  private final Set<String> pendingRenders = new HashSet<>();
  private final Map<String, SortEvent> pendingSortEvents = new HashMap<>();
  private int taskId = -1;

  public GuiRenderScheduler(GuiSessionRegistry registry, TaskScheduler taskScheduler) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
  }

  public void request(String storageId) {
    request(storageId, SortEvent.NONE);
  }

  public void request(String storageId, SortEvent event) {
    if (storageId == null) {
      return;
    }
    if (event != SortEvent.NONE) {
      pendingSortEvents.put(storageId, event);
    }
    pendingRenders.add(storageId);
    scheduleFlush();
  }

  private void scheduleFlush() {
    if (taskId != -1) {
      return;
    }
    taskId = taskScheduler.schedule(this::flush);
  }

  void flush() {
    taskId = -1;
    if (pendingRenders.isEmpty()) {
      pendingSortEvents.clear();
      return;
    }
    Set<String> storageIds = new HashSet<>(pendingRenders);
    Map<String, SortEvent> sortEvents = new HashMap<>(pendingSortEvents);
    pendingRenders.clear();
    pendingSortEvents.clear();
    for (String storageId : storageIds) {
      Set<GuiSession> sessions = registry.sessionsForStorage(storageId);
      if (sessions.isEmpty()) {
        continue;
      }
      SortEvent event = sortEvents.getOrDefault(storageId, SortEvent.NONE);
      for (GuiSession session : new ArrayList<>(sessions)) {
        session.onSortEvent(event);
        session.render();
      }
    }
  }

  @FunctionalInterface
  public interface TaskScheduler {
    int schedule(Runnable task);
  }
}
