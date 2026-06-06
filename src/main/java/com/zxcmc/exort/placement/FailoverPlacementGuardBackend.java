package com.zxcmc.exort.placement;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class FailoverPlacementGuardBackend implements PlacementGuardBackend {
  private final PlacementGuardBackend packetBackend;
  private final PlacementGuardBackend paperBackend;
  private boolean paperFallback;
  private boolean warned;

  public FailoverPlacementGuardBackend(
      PacketPlacementGuardBackend packetBackend, PaperEntityPlacementGuardBackend paperBackend) {
    this.packetBackend = packetBackend;
    this.paperBackend = paperBackend;
  }

  @Override
  public String name() {
    return paperFallback ? paperBackend.name() : packetBackend.name();
  }

  @Override
  public boolean usesServerEntities() {
    return paperFallback ? paperBackend.usesServerEntities() : packetBackend.usesServerEntities();
  }

  public boolean usingPacketBackend() {
    return !paperFallback;
  }

  public boolean usingPaperFallback() {
    return paperFallback;
  }

  @Override
  public PlacementGuardHandle createGuard(Player player, GuardTarget target) {
    if (paperFallback) {
      return wrapPaper(paperBackend.createGuard(player, target));
    }
    PlacementGuardHandle packetHandle = packetBackend.createGuard(player, target);
    if (packetHandle != null && !paperFallback) {
      return wrapPacket(packetHandle);
    }
    if (paperFallback) {
      return wrapPaper(paperBackend.createGuard(player, target));
    }
    return null;
  }

  public void switchToPaperFallback(String reason) {
    if (!paperFallback) {
      paperFallback = true;
    }
    if (warned) {
      return;
    }
    warned = true;
    ExortLog.warn(
        "[PacketEvents] Placement guard packet backend failed at runtime; switching to Paper entity"
            + " placement guard. Cause: "
            + (reason == null || reason.isBlank() ? "unknown" : reason));
  }

  private PlacementGuardHandle wrapPacket(PlacementGuardHandle handle) {
    return handle == null ? null : new FailoverHandle(handle, false);
  }

  private PlacementGuardHandle wrapPaper(PlacementGuardHandle handle) {
    return handle == null ? null : new FailoverHandle(handle, true);
  }

  private final class FailoverHandle implements PlacementGuardHandle {
    private final PlacementGuardHandle delegate;
    private final boolean paperHandle;

    private FailoverHandle(PlacementGuardHandle delegate, boolean paperHandle) {
      this.delegate = delegate;
      this.paperHandle = paperHandle;
    }

    @Override
    public boolean isValid() {
      return delegate.isValid() && (paperHandle || !paperFallback);
    }

    @Override
    public void move(Player player, GuardTarget target) {
      if (!isValid()) {
        delegate.remove();
        return;
      }
      delegate.move(player, target);
    }

    @Override
    public void remove() {
      delegate.remove();
    }

    @Override
    public UUID bukkitEntityUuid() {
      return delegate.bukkitEntityUuid();
    }
  }
}
