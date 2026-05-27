package com.zxcmc.exort.gui.session;

import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SessionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class GuiSessionRegistry {
  private final Map<UUID, GuiSession> byPlayer = new HashMap<>();
  private final Map<String, LinkedHashSet<GuiSession>> byStorage = new HashMap<>();
  private final Map<TerminalPos, Set<GuiSession>> byTerminal = new HashMap<>();
  private final Map<String, UUID> forcedWriters = new HashMap<>();

  public boolean containsViewer(UUID viewerId) {
    return byPlayer.containsKey(viewerId);
  }

  public GuiSession sessionFor(UUID viewerId) {
    return byPlayer.get(viewerId);
  }

  public boolean hasStorageSessions(String storageId) {
    LinkedHashSet<GuiSession> sessions = byStorage.get(storageId);
    return sessions != null && !sessions.isEmpty();
  }

  public void register(GuiSession session) {
    byPlayer.put(session.getViewer().getUniqueId(), session);
    byStorage.computeIfAbsent(session.getStorageId(), k -> new LinkedHashSet<>()).add(session);
    Block terminal = session.getTerminalBlock();
    if (terminal != null) {
      byTerminal.computeIfAbsent(TerminalPos.of(terminal), k -> new HashSet<>()).add(session);
    }
  }

  public Removal unregister(Player player, GuiSession expectedSession) {
    if (player == null) {
      return null;
    }
    UUID viewerId = player.getUniqueId();
    if (expectedSession != null && byPlayer.get(viewerId) != expectedSession) {
      return null;
    }
    GuiSession session = byPlayer.remove(viewerId);
    if (session == null) {
      return null;
    }
    LinkedHashSet<GuiSession> sessions = byStorage.get(session.getStorageId());
    boolean wasWriter = !session.isReadOnly();
    if (sessions != null) {
      sessions.remove(session);
      if (sessions.isEmpty()) {
        byStorage.remove(session.getStorageId());
      }
    }
    UUID forced = forcedWriters.get(session.getStorageId());
    if (forced != null && forced.equals(session.getViewer().getUniqueId())) {
      forcedWriters.remove(session.getStorageId());
      promoteWriter(session.getStorageId());
    } else if (wasWriter && (forced == null || sessions == null || sessions.isEmpty())) {
      promoteWriter(session.getStorageId());
    }
    removeTerminalSession(session);
    return new Removal(session, sessions != null && !sessions.isEmpty());
  }

  public Collection<GuiSession> allSessions() {
    return List.copyOf(byPlayer.values());
  }

  public Set<GuiSession> sessionsForStorage(String storageId) {
    Set<GuiSession> sessions = byStorage.get(storageId);
    return sessions == null ? Set.of() : Set.copyOf(sessions);
  }

  public List<GuiSession> sessionsForTerminal(Block block) {
    if (block == null) {
      return List.of();
    }
    Set<GuiSession> sessions = byTerminal.get(TerminalPos.of(block));
    return sessions == null ? List.of() : new ArrayList<>(sessions);
  }

  public void makeForcedWriter(String storageId, UUID viewerId) {
    forcedWriters.put(storageId, viewerId);
    Set<GuiSession> sessions = byStorage.get(storageId);
    if (sessions == null) {
      return;
    }
    for (GuiSession session : sessions) {
      session.setReadOnly(true);
    }
  }

  public boolean isModeratorLocked(String storageId, UUID viewerId) {
    UUID forced = forcedWriters.get(storageId);
    return forced != null && !forced.equals(viewerId);
  }

  public boolean forceWriter(GuiSession session) {
    if (session == null) {
      return false;
    }
    String storageId = session.getStorageId();
    if (isModeratorLocked(storageId, session.getViewer().getUniqueId())) {
      return false;
    }
    Set<GuiSession> sessions = byStorage.get(storageId);
    if (sessions == null || sessions.isEmpty()) {
      return false;
    }
    for (GuiSession registeredSession : sessions) {
      registeredSession.setReadOnly(registeredSession != session);
    }
    session.setReadOnly(false);
    return true;
  }

  public boolean hasCraftingSessions(String storageId) {
    Set<GuiSession> sessions = byStorage.get(storageId);
    if (sessions == null || sessions.isEmpty()) {
      return false;
    }
    for (GuiSession session : sessions) {
      if (session.type() == SessionType.CRAFTING) {
        return true;
      }
    }
    return false;
  }

  private void removeTerminalSession(GuiSession session) {
    Block terminal = session.getTerminalBlock();
    if (terminal == null) {
      return;
    }
    Set<GuiSession> sessions = byTerminal.get(TerminalPos.of(terminal));
    if (sessions == null) {
      return;
    }
    sessions.remove(session);
    if (sessions.isEmpty()) {
      byTerminal.remove(TerminalPos.of(terminal));
    }
  }

  private void promoteWriter(String storageId) {
    LinkedHashSet<GuiSession> sessions = byStorage.get(storageId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }
    GuiSession next = sessions.iterator().next();
    next.setReadOnly(false);
    next.render();
  }

  public record Removal(GuiSession session, boolean storageStillOpen) {}

  private record TerminalPos(UUID world, int x, int y, int z) {
    static TerminalPos of(Block block) {
      return new TerminalPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
