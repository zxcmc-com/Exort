package com.zxcmc.exort.integration.protocol;

import com.zxcmc.exort.items.listener.PickListener;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface PacketEnhancements {
  enum FeatureStatus {
    ENABLED,
    PARTIAL,
    DISABLED_BY_CONFIG,
    UNAVAILABLE,
    FALLBACK
  }

  record FeatureProbe(FeatureStatus status, String detail) {}

  record Diagnostics(
      String minecraftVersion,
      String packetPluginVersion,
      FeatureProbe base,
      FeatureProbe pickBridge,
      FeatureProbe entityPick,
      FeatureProbe placementGuard,
      FeatureProbe placementGuardScale,
      FeatureProbe placementGuardTeleport,
      FeatureProbe customBreaking,
      FeatureProbe localization) {}

  @FunctionalInterface
  interface ItemLocalizer extends PacketItemLocalizer.ItemLocalizer {}

  @FunctionalInterface
  interface DisplayLocalizer extends PacketDisplayLocalizer.DisplayLocalizer {}

  Diagnostics diagnostics();

  boolean registerLocalization(
      ItemLocalizer itemLocalizer,
      DisplayLocalizer displayLocalizer,
      boolean resourceMode,
      PacketLocalizationLevel requestedLevel);

  void registerPickBridge(PickListener pickListener);

  PlacementGuardPackets tryCreatePlacementGuardPackets(double guardScale);

  DisplayCullingPackets tryCreateDisplayCullingPackets();

  CustomBreakingPackets tryCreateCustomBreakingPackets(CustomBreakingController controller);

  void markPlacementGuardDisabledByConfig();

  void markPlacementGuardRuntimeFallback(String reason);

  void markCustomBreakingRuntimeFallback(String reason);

  void unregister();

  interface PlacementGuardPackets {
    boolean spawnArmorStand(Player player, int entityId, UUID entityUuid, Location location);

    boolean teleportEntity(Player player, int entityId, Location location);

    boolean destroyEntity(Player player, int entityId);

    boolean attributesSupported();

    boolean teleportSupported();

    String lastFailure();

    String capabilitySummary();
  }

  interface DisplayCullingPackets {
    int VIEW_RANGE_METADATA_INDEX = 17;

    boolean sendViewRange(Player player, int entityId, float viewRange);

    String lastFailure();
  }

  interface CustomBreakingController {
    boolean handleDestroyStart(Player player, Block block, int sequence);

    boolean handleDestroyAbort(Player player, Block block, int sequence);

    boolean handleDestroyFinish(Player player, Block block, int sequence);
  }

  interface CustomBreakingPackets {
    String lastFailure();
  }
}
