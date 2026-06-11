package com.zxcmc.exort.integration.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPickItemFromBlock;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPickItemFromEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.zxcmc.exort.display.DisplayTags;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.items.listener.PickListener;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PacketEventsEnhancements implements PacketEnhancements {
  private static final String PICK_ITEM_FROM_BLOCK = "PICK_ITEM_FROM_BLOCK";
  private static final String PICK_ITEM_FROM_ENTITY = "PICK_ITEM_FROM_ENTITY";
  private static final double ENTITY_PICK_LOOKUP_RANGE = 8.0;
  private static final PacketEventsMetadataValueAdapter METADATA_ADAPTER =
      new PacketEventsMetadataValueAdapter();

  private final JavaPlugin plugin;
  private final PacketEventsAPI<?> api;
  private final String packetEventsVersion;
  private final Consumer<String> pickDebugSink;
  private final List<PacketListenerCommon> packetListeners = new ArrayList<>();
  private final Map<Feature, FeatureProbe> featureProbes = new EnumMap<>(Feature.class);
  private boolean localizationRegistered;
  private boolean pickBridgeRegistered;
  private boolean customBreakingRegistered;
  private PacketEventsCustomBreakingPackets customBreakingPackets;

  private PacketEventsEnhancements(
      JavaPlugin plugin,
      PacketEventsAPI<?> api,
      String packetEventsVersion,
      Consumer<String> pickDebugSink) {
    this.plugin = plugin;
    this.api = api;
    this.packetEventsVersion = packetEventsVersion;
    this.pickDebugSink = pickDebugSink == null ? message -> {} : pickDebugSink;
  }

  static PacketEventsEnhancements tryCreate(
      JavaPlugin plugin, Plugin packetEventsPlugin, Consumer<String> pickDebugSink) {
    PacketEventsAPI<?> api = PacketEvents.getAPI();
    if (api == null || !api.isLoaded() || !api.isInitialized()) {
      throw new IllegalStateException("PacketEvents API is not initialized");
    }
    if (api.getEventManager() == null || api.getPlayerManager() == null) {
      throw new IllegalStateException("PacketEvents API managers are unavailable");
    }
    String version = packetEventsPlugin.getPluginMeta().getVersion();
    PacketEventsEnhancements enhancements =
        new PacketEventsEnhancements(plugin, api, version, pickDebugSink);
    enhancements.setProbe(Feature.BASE, FeatureStatus.ENABLED, "PacketEvents " + version);
    ExortLog.success("[PacketEvents] Integration enabled.");
    return enhancements;
  }

  @Override
  public Diagnostics diagnostics() {
    return new Diagnostics(
        Bukkit.getMinecraftVersion(),
        packetEventsVersion,
        probe(Feature.BASE),
        probe(Feature.PICK_BRIDGE),
        probe(Feature.ENTITY_PICK),
        probe(Feature.PLACEMENT_GUARD),
        probe(Feature.PLACEMENT_GUARD_SCALE),
        probe(Feature.PLACEMENT_GUARD_TELEPORT),
        probe(Feature.CUSTOM_BREAKING),
        probe(Feature.LOCALIZATION));
  }

  @Override
  public boolean registerLocalization(
      ItemLocalizer itemLocalizer,
      DisplayLocalizer displayLocalizer,
      boolean resourceMode,
      PacketLocalizationLevel requestedLevel) {
    PacketLocalizationLevel level =
        requestedLevel == null ? PacketLocalizationLevel.SIMPLE : requestedLevel;
    if (resourceMode) {
      setProbe(
          Feature.LOCALIZATION,
          FeatureStatus.DISABLED_BY_CONFIG,
          "RESOURCE mode uses resource-pack translations.");
      return false;
    }
    if (itemLocalizer == null) {
      setProbe(Feature.LOCALIZATION, FeatureStatus.UNAVAILABLE, "Missing item localizer.");
      return false;
    }
    if (localizationRegistered) {
      return level == PacketLocalizationLevel.FULL && displayLocalizer != null;
    }

    boolean full = level == PacketLocalizationLevel.FULL && displayLocalizer != null;
    PacketListener listener =
        new PacketListener() {
          @Override
          public void onPacketSend(PacketSendEvent event) {
            handleLocalizationPacket(event, itemLocalizer, full ? displayLocalizer : null);
          }
        };
    packetListeners.add(
        api.getEventManager().registerListener(listener, PacketListenerPriority.NORMAL));
    localizationRegistered = true;
    setProbe(
        Feature.LOCALIZATION,
        full ? FeatureStatus.ENABLED : FeatureStatus.PARTIAL,
        full
            ? "SET_SLOT, WINDOW_ITEMS, and ENTITY_METADATA enabled."
            : "Inventory packets enabled.");
    ExortLog.success(
        "[PacketEvents] VANILLA packet localization enabled"
            + (full ? " for inventory and display metadata." : " for inventory packets."));
    return full;
  }

  @Override
  public void registerPickBridge(PickListener pickListener) {
    if (pickListener == null || pickBridgeRegistered) {
      return;
    }
    PacketListener listener =
        new PacketListener() {
          @Override
          public void onPacketReceive(PacketReceiveEvent event) {
            handlePickPacket(event, pickListener);
          }
        };
    packetListeners.add(
        api.getEventManager().registerListener(listener, PacketListenerPriority.NORMAL));
    pickBridgeRegistered = true;
    setProbe(
        Feature.PICK_BRIDGE,
        FeatureStatus.ENABLED,
        PICK_ITEM_FROM_BLOCK + " and " + PICK_ITEM_FROM_ENTITY + " enabled.");
    setProbe(Feature.ENTITY_PICK, FeatureStatus.ENABLED, PICK_ITEM_FROM_ENTITY + " enabled.");
    ExortLog.success("[PacketEvents] Pick bridge enabled.");
  }

  @Override
  public PlacementGuardPackets tryCreatePlacementGuardPackets(double guardScale) {
    try {
      PacketEventsPlacementGuardPackets packets =
          new PacketEventsPlacementGuardPackets(
              api, guardScale, resolvePlacementGuardMetadataIndexes());
      packets.validate();
      setProbe(
          Feature.PLACEMENT_GUARD,
          FeatureStatus.ENABLED,
          "fake ArmorStand packets available; " + packets.capabilitySummary());
      setProbe(
          Feature.PLACEMENT_GUARD_SCALE,
          packets.attributesSupported() ? FeatureStatus.ENABLED : FeatureStatus.UNAVAILABLE,
          packets.attributesSupported()
              ? "UPDATE_ATTRIBUTES scale available."
              : "Missing scale attribute.");
      setProbe(
          Feature.PLACEMENT_GUARD_TELEPORT,
          packets.teleportSupported() ? FeatureStatus.ENABLED : FeatureStatus.UNAVAILABLE,
          packets.teleportSupported() ? "ENTITY_TELEPORT available." : "Missing entity teleport.");
      return packets;
    } catch (LinkageError | RuntimeException e) {
      String detail = describeError(e);
      setProbe(Feature.PLACEMENT_GUARD, FeatureStatus.UNAVAILABLE, detail);
      ExortLog.warn(
          "[PacketEvents] Placement guard packet backend unavailable; using Paper entity placement"
              + " guard. Cause: "
              + detail);
      return null;
    }
  }

  @Override
  public DisplayCullingPackets tryCreateDisplayCullingPackets() {
    try {
      PacketEventsDisplayCullingPackets packets = new PacketEventsDisplayCullingPackets(api);
      packets.validate();
      return packets;
    } catch (LinkageError | RuntimeException e) {
      ExortLog.warn(
          "[PacketEvents] Display culling metadata backend unavailable; using Paper entity"
              + " culling. Cause: "
              + describeError(e));
      return null;
    }
  }

  @Override
  public CustomBreakingPackets tryCreateCustomBreakingPackets(CustomBreakingController controller) {
    if (controller == null) {
      setProbe(Feature.CUSTOM_BREAKING, FeatureStatus.UNAVAILABLE, "Missing controller.");
      return null;
    }
    if (customBreakingRegistered) {
      return customBreakingPackets;
    }
    try {
      PacketEventsCustomBreakingPackets packets = new PacketEventsCustomBreakingPackets(api);
      packets.validate();
      PacketListener listener =
          new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
              handleCustomBreakingPacket(event, controller, packets);
            }
          };
      packetListeners.add(
          api.getEventManager().registerListener(listener, PacketListenerPriority.NORMAL));
      customBreakingRegistered = true;
      customBreakingPackets = packets;
      setProbe(
          Feature.CUSTOM_BREAKING,
          FeatureStatus.ENABLED,
          "PLAYER_DIGGING START/CANCEL/FINISH observer enabled.");
      ExortLog.success("[PacketEvents] Custom breaking controls enabled.");
      return packets;
    } catch (LinkageError | RuntimeException e) {
      String detail = describeError(e);
      setProbe(Feature.CUSTOM_BREAKING, FeatureStatus.UNAVAILABLE, detail);
      ExortLog.warn(
          "[PacketEvents] Custom breaking packet backend unavailable; using Paper fallback. Cause:"
              + " "
              + detail);
      return null;
    }
  }

  @Override
  public void markPlacementGuardDisabledByConfig() {
    setProbe(Feature.PLACEMENT_GUARD, FeatureStatus.DISABLED_BY_CONFIG, "Disabled by config.");
  }

  @Override
  public void markPlacementGuardRuntimeFallback(String reason) {
    setProbe(
        Feature.PLACEMENT_GUARD,
        FeatureStatus.FALLBACK,
        reason == null || reason.isBlank() ? "Runtime send failure." : reason);
  }

  @Override
  public void markCustomBreakingRuntimeFallback(String reason) {
    setProbe(
        Feature.CUSTOM_BREAKING,
        FeatureStatus.FALLBACK,
        reason == null || reason.isBlank() ? "Runtime failure." : reason);
  }

  @Override
  public void unregister() {
    for (PacketListenerCommon listener : List.copyOf(packetListeners)) {
      try {
        api.getEventManager().unregisterListener(listener);
      } catch (LinkageError | RuntimeException e) {
        ExortLog.warn("[PacketEvents] Failed to unregister packet listener: " + e.getMessage());
      }
    }
    packetListeners.clear();
    localizationRegistered = false;
    pickBridgeRegistered = false;
    customBreakingRegistered = false;
    customBreakingPackets = null;
  }

  private void handleLocalizationPacket(
      PacketSendEvent event, ItemLocalizer itemLocalizer, DisplayLocalizer displayLocalizer) {
    if (event == null || event.isCancelled()) {
      return;
    }
    Object rawPlayer = event.getPlayer();
    if (!(rawPlayer instanceof Player player)) {
      return;
    }
    PacketTypeCommon type = event.getPacketType();
    try {
      if (type == PacketType.Play.Server.SET_SLOT) {
        localizeSetSlot(event, player, itemLocalizer);
      } else if (type == PacketType.Play.Server.WINDOW_ITEMS) {
        localizeWindowItems(event, player, itemLocalizer);
      } else if (type == PacketType.Play.Server.ENTITY_METADATA && displayLocalizer != null) {
        localizeEntityMetadata(event, player, displayLocalizer);
      }
    } catch (LinkageError | RuntimeException e) {
      setProbe(Feature.LOCALIZATION, FeatureStatus.UNAVAILABLE, describeError(e));
    }
  }

  private static void localizeSetSlot(
      PacketSendEvent event, Player player, ItemLocalizer itemLocalizer) {
    WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
    ItemStack original = fromPacketItem(packet.getItem());
    ItemStack localized = PacketItemLocalizer.localizeSlot(player, original, itemLocalizer);
    if (localized == original) {
      return;
    }
    packet.setItem(toPacketItem(localized));
    event.markForReEncode(true);
  }

  private static void localizeWindowItems(
      PacketSendEvent event, Player player, ItemLocalizer itemLocalizer) {
    WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
    List<com.github.retrooper.packetevents.protocol.item.ItemStack> packetItems = packet.getItems();
    if (packetItems == null || packetItems.isEmpty()) {
      return;
    }
    List<ItemStack> bukkitItems = new ArrayList<>(packetItems.size());
    for (com.github.retrooper.packetevents.protocol.item.ItemStack packetItem : packetItems) {
      bukkitItems.add(fromPacketItem(packetItem));
    }

    boolean changed = false;
    List<ItemStack> localizedItems =
        PacketItemLocalizer.localizeItems(player, bukkitItems, itemLocalizer);
    if (localizedItems != bukkitItems) {
      List<com.github.retrooper.packetevents.protocol.item.ItemStack> encoded =
          new ArrayList<>(localizedItems.size());
      for (ItemStack item : localizedItems) {
        encoded.add(toPacketItem(item));
      }
      packet.setItems(encoded);
      changed = true;
    }

    Optional<com.github.retrooper.packetevents.protocol.item.ItemStack> carried =
        packet.getCarriedItem();
    if (carried.isPresent()) {
      ItemStack carriedItem = fromPacketItem(carried.get());
      ItemStack localizedCarried =
          PacketItemLocalizer.localizeSlot(player, carriedItem, itemLocalizer);
      if (localizedCarried != carriedItem) {
        packet.setCarriedItem(toPacketItem(localizedCarried));
        changed = true;
      }
    }

    if (changed) {
      event.markForReEncode(true);
    }
  }

  private static void localizeEntityMetadata(
      PacketSendEvent event, Player player, DisplayLocalizer displayLocalizer) {
    WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
    List<EntityData<?>> metadata = packet.getEntityMetadata();
    List<EntityData<?>> localized =
        PacketDisplayLocalizer.localizeValues(
            player, packet.getEntityId(), metadata, METADATA_ADAPTER, displayLocalizer);
    if (localized == metadata) {
      return;
    }
    packet.setEntityMetadata(localized);
    event.markForReEncode(true);
  }

  private void handlePickPacket(PacketReceiveEvent event, PickListener pickListener) {
    if (event == null) {
      return;
    }
    Object rawPlayer = event.getPlayer();
    if (!(rawPlayer instanceof Player player)) {
      return;
    }
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.PICK_ITEM_FROM_BLOCK) {
      handleBlockPickPacket(event, player, pickListener);
    } else if (type == PacketType.Play.Client.PICK_ITEM_FROM_ENTITY) {
      handleEntityPickPacket(event, player, pickListener);
    }
  }

  private void handleCustomBreakingPacket(
      PacketReceiveEvent event,
      CustomBreakingController controller,
      PacketEventsCustomBreakingPackets packets) {
    if (event == null || event.isCancelled()) {
      return;
    }
    Object rawPlayer = event.getPlayer();
    if (!(rawPlayer instanceof Player player)) {
      return;
    }
    if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
      return;
    }
    DiggingAction action;
    Vector3i blockPos;
    int sequence;
    try {
      WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
      action = packet.getAction();
      blockPos = packet.getBlockPosition();
      sequence = packet.getSequence();
    } catch (LinkageError | RuntimeException e) {
      packets.recordRuntimeFailure("read PLAYER_DIGGING", e);
      markCustomBreakingRuntimeFallback(packets.lastFailure());
      return;
    }
    if (blockPos == null || !isDestroyAction(action)) {
      return;
    }
    PacketTarget target = new PacketTarget(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    if (!plugin.isEnabled()) {
      return;
    }
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () ->
                handleScheduledCustomBreaking(
                    player, controller, packets, action, target, sequence));
  }

  private void handleScheduledCustomBreaking(
      Player player,
      CustomBreakingController controller,
      PacketEventsCustomBreakingPackets packets,
      DiggingAction action,
      PacketTarget target,
      int sequence) {
    if (!player.isOnline()) {
      return;
    }
    Block block = player.getWorld().getBlockAt(target.x(), target.y(), target.z());
    boolean handled =
        switch (action) {
          case START_DIGGING -> controller.handleDestroyStart(player, block, sequence);
          case CANCELLED_DIGGING -> controller.handleDestroyAbort(player, block, sequence);
          case FINISHED_DIGGING -> controller.handleDestroyFinish(player, block, sequence);
          default -> false;
        };
    if (handled && !packets.acknowledge(player, sequence)) {
      markCustomBreakingRuntimeFallback(packets.lastFailure());
    }
  }

  private static boolean isDestroyAction(DiggingAction action) {
    return action == DiggingAction.START_DIGGING
        || action == DiggingAction.CANCELLED_DIGGING
        || action == DiggingAction.FINISHED_DIGGING;
  }

  private void handleBlockPickPacket(
      PacketReceiveEvent event, Player player, PickListener pickListener) {
    Vector3i blockPos;
    boolean cancelled = event.isCancelled();
    try {
      blockPos = new WrapperPlayClientPickItemFromBlock(event).getBlockPos();
    } catch (LinkageError | RuntimeException e) {
      debugFull("packet block pick read failed error=" + e.getClass().getSimpleName());
      return;
    }
    if (blockPos == null) {
      return;
    }
    PacketTarget target = new PacketTarget(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    Bukkit.getScheduler()
        .runTask(plugin, () -> handleScheduledBlockPick(player, pickListener, target, cancelled));
  }

  private void handleEntityPickPacket(
      PacketReceiveEvent event, Player player, PickListener pickListener) {
    int entityId;
    boolean cancelled = event.isCancelled();
    try {
      entityId = new WrapperPlayClientPickItemFromEntity(event).getEntityId();
    } catch (LinkageError | RuntimeException e) {
      debugFull("packet entity pick read failed error=" + e.getClass().getSimpleName());
      return;
    }
    Bukkit.getScheduler()
        .runTask(
            plugin, () -> handleScheduledEntityPick(player, pickListener, entityId, cancelled));
  }

  private void handleScheduledBlockPick(
      Player player, PickListener pickListener, PacketTarget target, boolean cancelledBefore) {
    if (!player.isOnline()) {
      return;
    }
    Block packetBlock = player.getWorld().getBlockAt(target.x(), target.y(), target.z());
    Block playerTarget = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
    debugFull(
        "packet event "
            + PICK_ITEM_FROM_BLOCK
            + " player="
            + player.getName()
            + " cancelledBefore="
            + cancelledBefore
            + " packetBlock="
            + pickListener.describeDebugBlock(packetBlock)
            + " playerTarget="
            + pickListener.describeDebugBlock(playerTarget));
    boolean handled =
        pickListener.handleDirectPick(player, packetBlock, "PacketEvents " + PICK_ITEM_FROM_BLOCK);
    if (handled) {
      debugFull(
          "packet handled "
              + PICK_ITEM_FROM_BLOCK
              + " player="
              + player.getName()
              + " packetBlock="
              + pickListener.describeDebugBlock(packetBlock));
    }
  }

  private void handleScheduledEntityPick(
      Player player, PickListener pickListener, int entityId, boolean cancelledBefore) {
    if (!player.isOnline()) {
      return;
    }
    Entity entity = findNearbyEntity(player, entityId);
    Block target = resolveDisplayTarget(pickListener, entity);
    debugFull(
        "packet event "
            + PICK_ITEM_FROM_ENTITY
            + " player="
            + player.getName()
            + " cancelledBefore="
            + cancelledBefore
            + " entity="
            + describeEntity(entity)
            + " target="
            + pickListener.describeDebugBlock(target));
    if (target == null) {
      return;
    }
    boolean handled =
        pickListener.handleDirectPick(player, target, "PacketEvents " + PICK_ITEM_FROM_ENTITY);
    if (handled) {
      debugFull(
          "packet handled "
              + PICK_ITEM_FROM_ENTITY
              + " player="
              + player.getName()
              + " target="
              + pickListener.describeDebugBlock(target));
    }
  }

  private static Entity findNearbyEntity(Player player, int entityId) {
    for (Entity entity :
        player
            .getWorld()
            .getNearbyEntities(
                player.getLocation(),
                ENTITY_PICK_LOOKUP_RANGE,
                ENTITY_PICK_LOOKUP_RANGE,
                ENTITY_PICK_LOOKUP_RANGE)) {
      if (entity.getEntityId() == entityId) {
        return entity;
      }
    }
    return null;
  }

  private static Block resolveDisplayTarget(PickListener pickListener, Entity entity) {
    if (entity == null) {
      return null;
    }
    Set<String> tags = entity.getScoreboardTags();
    if (!tags.contains(DisplayTags.DISPLAY_TAG)
        || tags.contains(DisplayTags.HOLOGRAM_TAG)
        || tags.contains(DisplayTags.BREAK_OVERLAY_TAG)) {
      return null;
    }
    Location loc = entity.getLocation();
    World world = loc.getWorld();
    if (world == null) {
      return null;
    }
    Block best = null;
    double bestDistance = Double.MAX_VALUE;
    int baseX = loc.getBlockX();
    int baseY = loc.getBlockY();
    int baseZ = loc.getBlockZ();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        for (int dz = -1; dz <= 1; dz++) {
          Block candidate = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
          if (!pickListener.isPickTargetBlock(candidate)) {
            continue;
          }
          double distance = candidate.getLocation().add(0.5, 0.5, 0.5).distanceSquared(loc);
          if (distance < bestDistance) {
            best = candidate;
            bestDistance = distance;
          }
        }
      }
    }
    return best;
  }

  private static ItemStack fromPacketItem(
      com.github.retrooper.packetevents.protocol.item.ItemStack packetItem) {
    if (packetItem == null || packetItem.isEmpty()) {
      return null;
    }
    return SpigotConversionUtil.toBukkitItemStack(packetItem);
  }

  private static com.github.retrooper.packetevents.protocol.item.ItemStack toPacketItem(
      ItemStack item) {
    if (item == null || item.getType() == Material.AIR || item.getType().isAir()) {
      return com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
    }
    return SpigotConversionUtil.fromBukkitItemStack(item);
  }

  private static MetadataIndexes resolvePlacementGuardMetadataIndexes() {
    return new MetadataIndexes(
        resolveDataAccessorId("net.minecraft.world.entity.Entity", "DATA_SHARED_FLAGS_ID", 0),
        resolveDataAccessorId("net.minecraft.world.entity.Entity", "DATA_NO_GRAVITY", 5),
        resolveDataAccessorId(
            "net.minecraft.world.entity.decoration.ArmorStand", "DATA_CLIENT_FLAGS", 15));
  }

  private static int resolveDataAccessorId(String className, String fieldName, int fallback) {
    try {
      Class<?> owner = Class.forName(className);
      var field = owner.getDeclaredField(fieldName);
      field.setAccessible(true);
      Object accessor = field.get(null);
      return ((Number) accessor.getClass().getMethod("id").invoke(accessor)).intValue();
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      return fallback;
    }
  }

  private void setProbe(Feature feature, FeatureStatus status, String detail) {
    featureProbes.put(feature, new FeatureProbe(status, detail == null ? "" : detail));
  }

  private FeatureProbe probe(Feature feature) {
    return featureProbes.getOrDefault(feature, new FeatureProbe(FeatureStatus.UNAVAILABLE, ""));
  }

  private void debugFull(String message) {
    pickDebugSink.accept(message);
  }

  private static String describeError(Throwable error) {
    String message = error.getMessage();
    return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
  }

  private static String describeEntity(Entity entity) {
    if (entity == null) {
      return "null";
    }
    Location loc = entity.getLocation();
    return entity.getType()
        + "#"
        + entity.getEntityId()
        + "@"
        + loc.getWorld().getName()
        + ","
        + loc.getBlockX()
        + ","
        + loc.getBlockY()
        + ","
        + loc.getBlockZ()
        + " tags="
        + entity.getScoreboardTags();
  }

  private enum Feature {
    BASE,
    PICK_BRIDGE,
    ENTITY_PICK,
    PLACEMENT_GUARD,
    PLACEMENT_GUARD_SCALE,
    PLACEMENT_GUARD_TELEPORT,
    CUSTOM_BREAKING,
    LOCALIZATION
  }

  private record PacketTarget(int x, int y, int z) {}

  private record MetadataIndexes(int entityFlags, int noGravity, int armorStandFlags) {}

  private static final class PacketEventsMetadataValueAdapter
      implements PacketDisplayLocalizer.MetadataValueAdapter<EntityData<?>> {
    @Override
    public int index(EntityData<?> value) {
      return value.getIndex();
    }

    @Override
    public Object value(EntityData<?> value) {
      return value.getValue();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public EntityData<?> withValue(EntityData<?> value, Object replacement) {
      return new EntityData(value.getIndex(), (EntityDataType) value.getType(), replacement);
    }

    @Override
    public ItemStack itemStackFromValue(Object previousValue) {
      if (previousValue
          instanceof com.github.retrooper.packetevents.protocol.item.ItemStack stack) {
        return fromPacketItem(stack);
      }
      if (previousValue instanceof Optional<?> optional
          && optional.orElse(null)
              instanceof com.github.retrooper.packetevents.protocol.item.ItemStack stack) {
        return fromPacketItem(stack);
      }
      return null;
    }

    @Override
    public Object itemStackValue(Object previousValue, ItemStack localizedStack) {
      com.github.retrooper.packetevents.protocol.item.ItemStack packetItem =
          toPacketItem(localizedStack);
      return previousValue instanceof Optional<?> ? Optional.of(packetItem) : packetItem;
    }

    @Override
    public Object customNameValue(Object previousValue, String localizedName) {
      return null;
    }

    @Override
    public Object customNameValue(
        EntityData<?> metadataValue, Object previousValue, String localizedName) {
      if (!(previousValue instanceof Optional<?> optional)) {
        return null;
      }
      Component component = Component.text(localizedName).decoration(TextDecoration.ITALIC, false);
      if (metadataValue.getType() == EntityDataTypes.OPTIONAL_ADV_COMPONENT) {
        return Optional.of(component);
      }
      if (optional.orElse(null) instanceof Component) {
        return Optional.of(component);
      }
      return null;
    }
  }

  private static final class PacketEventsPlacementGuardPackets
      implements PacketEnhancements.PlacementGuardPackets {
    private static final byte ENTITY_FLAG_INVISIBLE = 0x20;
    private static final byte ARMOR_STAND_FLAGS_SMALL_NO_BASEPLATE = 0x01 | 0x08;

    private final PacketEventsAPI<?> api;
    private final double guardScale;
    private final MetadataIndexes metadataIndexes;
    private String lastFailure = "";
    private boolean attributesSupported = true;
    private boolean teleportSupported = true;

    private PacketEventsPlacementGuardPackets(
        PacketEventsAPI<?> api, double guardScale, MetadataIndexes metadataIndexes) {
      this.api = api;
      this.guardScale = guardScale;
      this.metadataIndexes = metadataIndexes;
    }

    @Override
    public boolean spawnArmorStand(
        Player player, int entityId, UUID entityUuid, Location location) {
      try {
        send(player, createSpawnPacket(entityId, entityUuid, location));
        send(player, createMetadataPacket(entityId));
        if (attributesSupported) {
          send(player, createAttributesPacket(entityId));
        }
        return true;
      } catch (LinkageError | RuntimeException e) {
        recordRuntimeFailure("spawn fake placement guard", e);
        try {
          send(player, createDestroyPacket(entityId));
        } catch (LinkageError | RuntimeException ignored) {
        }
        return false;
      }
    }

    @Override
    public boolean teleportEntity(Player player, int entityId, Location location) {
      try {
        if (teleportSupported) {
          send(player, createTeleportPacket(entityId, location));
        } else {
          send(player, createDestroyPacket(entityId));
          send(player, createSpawnPacket(entityId, UUID.randomUUID(), location));
          send(player, createMetadataPacket(entityId));
          if (attributesSupported) {
            send(player, createAttributesPacket(entityId));
          }
        }
        return true;
      } catch (LinkageError | RuntimeException e) {
        recordRuntimeFailure("move fake placement guard", e);
        return false;
      }
    }

    @Override
    public boolean destroyEntity(Player player, int entityId) {
      try {
        send(player, createDestroyPacket(entityId));
        return true;
      } catch (LinkageError | RuntimeException e) {
        recordRuntimeFailure("destroy fake placement guard", e);
        return false;
      }
    }

    @Override
    public boolean attributesSupported() {
      return attributesSupported;
    }

    @Override
    public boolean teleportSupported() {
      return teleportSupported;
    }

    @Override
    public String lastFailure() {
      return lastFailure;
    }

    @Override
    public String capabilitySummary() {
      return "scale=" + attributesSupported + ", teleport=" + teleportSupported + ".";
    }

    private void validate() {
      Location origin = new Location(null, 0.0, 0.0, 0.0);
      createSpawnPacket(1, new UUID(0L, 1L), origin);
      createMetadataPacket(1);
      createDestroyPacket(1);
      attributesSupported = canCreateAttributesPacket(1);
      if (!attributesSupported) {
        ExortLog.warn(
            "[PacketEvents] Placement guard scale attribute packet unavailable; fake guards will"
                + " use small ArmorStand metadata only.");
      }
      teleportSupported = canCreateTeleportPacket(origin);
    }

    private boolean canCreateAttributesPacket(int entityId) {
      try {
        createAttributesPacket(entityId);
        return true;
      } catch (LinkageError | RuntimeException e) {
        return false;
      }
    }

    private boolean canCreateTeleportPacket(Location origin) {
      try {
        createTeleportPacket(1, origin);
        return true;
      } catch (LinkageError | RuntimeException e) {
        return false;
      }
    }

    private WrapperPlayServerSpawnEntity createSpawnPacket(
        int entityId, UUID entityUuid, Location location) {
      return new WrapperPlayServerSpawnEntity(
          entityId,
          Optional.ofNullable(entityUuid),
          EntityTypes.ARMOR_STAND,
          vector(location),
          0.0f,
          0.0f,
          0.0f,
          0,
          Optional.of(Vector3d.zero()));
    }

    private WrapperPlayServerEntityMetadata createMetadataPacket(int entityId) {
      List<EntityData<?>> metadata =
          List.of(
              new EntityData<>(
                  metadataIndexes.entityFlags(),
                  EntityDataTypes.BYTE,
                  Byte.valueOf(ENTITY_FLAG_INVISIBLE)),
              new EntityData<>(metadataIndexes.noGravity(), EntityDataTypes.BOOLEAN, Boolean.TRUE),
              new EntityData<>(
                  metadataIndexes.armorStandFlags(),
                  EntityDataTypes.BYTE,
                  Byte.valueOf(ARMOR_STAND_FLAGS_SMALL_NO_BASEPLATE)));
      return new WrapperPlayServerEntityMetadata(entityId, metadata);
    }

    private WrapperPlayServerUpdateAttributes createAttributesPacket(int entityId) {
      return new WrapperPlayServerUpdateAttributes(
          entityId,
          List.of(
              new WrapperPlayServerUpdateAttributes.Property(
                  Attributes.SCALE, guardScale, List.of())));
    }

    private WrapperPlayServerEntityTeleport createTeleportPacket(int entityId, Location location) {
      return new WrapperPlayServerEntityTeleport(entityId, vector(location), 0.0f, 0.0f, false);
    }

    private WrapperPlayServerDestroyEntities createDestroyPacket(int entityId) {
      return new WrapperPlayServerDestroyEntities(entityId);
    }

    private void send(Player player, PacketWrapper<?> packet) {
      api.getPlayerManager().sendPacket(player, packet);
    }

    private static Vector3d vector(Location location) {
      return new Vector3d(location.getX(), location.getY(), location.getZ());
    }

    private void recordRuntimeFailure(String action, Throwable error) {
      lastFailure = action + ": " + describeError(error);
    }
  }

  private static final class PacketEventsDisplayCullingPackets
      implements PacketEnhancements.DisplayCullingPackets {
    private final PacketEventsAPI<?> api;
    private String lastFailure = "";

    private PacketEventsDisplayCullingPackets(PacketEventsAPI<?> api) {
      this.api = api;
    }

    @Override
    public boolean sendViewRange(Player player, int entityId, float viewRange) {
      try {
        api.getPlayerManager()
            .sendPacket(
                player,
                new WrapperPlayServerEntityMetadata(
                    entityId,
                    List.of(
                        new EntityData<>(
                            VIEW_RANGE_METADATA_INDEX,
                            EntityDataTypes.FLOAT,
                            Float.valueOf(Math.max(0.0f, viewRange))))));
        return true;
      } catch (LinkageError | RuntimeException e) {
        lastFailure = "send view range metadata: " + describeError(e);
        return false;
      }
    }

    @Override
    public String lastFailure() {
      return lastFailure;
    }

    private void validate() {
      new WrapperPlayServerEntityMetadata(
          1,
          List.of(
              new EntityData<>(
                  VIEW_RANGE_METADATA_INDEX, EntityDataTypes.FLOAT, Float.valueOf(1.0f))));
    }
  }

  private static final class PacketEventsCustomBreakingPackets
      implements PacketEnhancements.CustomBreakingPackets {
    private final PacketEventsAPI<?> api;
    private String lastFailure = "";

    private PacketEventsCustomBreakingPackets(PacketEventsAPI<?> api) {
      this.api = api;
    }

    private boolean acknowledge(Player player, int sequence) {
      try {
        api.getPlayerManager()
            .sendPacket(player, new WrapperPlayServerAcknowledgeBlockChanges(sequence));
        return true;
      } catch (LinkageError | RuntimeException e) {
        recordRuntimeFailure("acknowledge block changes", e);
        return false;
      }
    }

    @Override
    public String lastFailure() {
      return lastFailure;
    }

    private void validate() {
      new WrapperPlayServerAcknowledgeBlockChanges(0);
    }

    private void recordRuntimeFailure(String action, Throwable error) {
      lastFailure = action + ": " + describeError(error);
    }
  }
}
