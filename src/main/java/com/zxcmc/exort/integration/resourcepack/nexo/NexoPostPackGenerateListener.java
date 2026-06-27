package com.zxcmc.exort.integration.resourcepack.nexo;

import com.nexomc.nexo.api.events.resourcepack.NexoPostPackGenerateEvent;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class NexoPostPackGenerateListener implements Listener {
  private final NexoResourcePackIntegration integration;

  public NexoPostPackGenerateListener(NexoResourcePackIntegration integration) {
    this.integration = Objects.requireNonNull(integration, "integration");
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onNexoPostPackGenerate(NexoPostPackGenerateEvent event) {
    integration.addCurrentPack(rawPack -> event.addResourcePack(rawPack));
  }
}
