package com.zxcmc.exort.display.core;

import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

/** Keeps the display index aligned when a display is removed outside a display manager. */
public final class DisplayEntityIndexCleanupListener implements Listener {
  private final DisplayEntityIndex index;

  public DisplayEntityIndexCleanupListener(DisplayEntityIndex index) {
    this.index = index;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onEntityRemove(EntityRemoveEvent event) {
    if (event.getEntity() instanceof Display display) {
      index.unregister(display.getUniqueId());
    }
  }
}
