package com.zxcmc.exort.core.protocol;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.listeners.PickListener;
import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.display.DisplayTags;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ProtocolLibEnhancements {
  private static final String PROTOCOL_LIB = "ProtocolLib";
  private static final String PICK_ITEM_FROM_BLOCK = "PICK_ITEM_FROM_BLOCK";
  private static final String PICK_ITEM = "PICK_ITEM";
  private static final double ENTITY_PICK_LOOKUP_RANGE = 8.0;

  private final ExortPlugin plugin;
  private final ClassLoader loader;
  private final Object protocolManager;
  private final Class<?> packetTypeClass;
  private final Class<?> packetListenerClass;
  private final Class<?> listeningWhitelistClass;
  private final Method addPacketListener;
  private final Method removePacketListener;
  private final Method eventGetPlayer;
  private final Method eventGetPacket;
  private final Method eventGetPacketType;
  private final Method eventIsCancelled;
  private final Method packetGetBlockPositionModifier;
  private final Method packetGetIntegers;
  private final Method blockPositionGetX;
  private final Method blockPositionGetY;
  private final Method blockPositionGetZ;
  private final Object emptyWhitelist;
  private final List<Object> packetListeners = new ArrayList<>();

  private ProtocolLibEnhancements(
      ExortPlugin plugin,
      ClassLoader loader,
      Object protocolManager,
      Class<?> packetTypeClass,
      Class<?> packetListenerClass,
      Class<?> listeningWhitelistClass,
      Method addPacketListener,
      Method removePacketListener,
      Method eventGetPlayer,
      Method eventGetPacket,
      Method eventGetPacketType,
      Method eventIsCancelled,
      Method packetGetBlockPositionModifier,
      Method packetGetIntegers,
      Method blockPositionGetX,
      Method blockPositionGetY,
      Method blockPositionGetZ,
      Object emptyWhitelist) {
    this.plugin = plugin;
    this.loader = loader;
    this.protocolManager = protocolManager;
    this.packetTypeClass = packetTypeClass;
    this.packetListenerClass = packetListenerClass;
    this.listeningWhitelistClass = listeningWhitelistClass;
    this.addPacketListener = addPacketListener;
    this.removePacketListener = removePacketListener;
    this.eventGetPlayer = eventGetPlayer;
    this.eventGetPacket = eventGetPacket;
    this.eventGetPacketType = eventGetPacketType;
    this.eventIsCancelled = eventIsCancelled;
    this.packetGetBlockPositionModifier = packetGetBlockPositionModifier;
    this.packetGetIntegers = packetGetIntegers;
    this.blockPositionGetX = blockPositionGetX;
    this.blockPositionGetY = blockPositionGetY;
    this.blockPositionGetZ = blockPositionGetZ;
    this.emptyWhitelist = emptyWhitelist;
  }

  public static ProtocolLibEnhancements tryCreate(ExortPlugin plugin) {
    if (!plugin.getConfig().getBoolean("protocolLib.enabled", true)) {
      return null;
    }
    Plugin protocolPlugin = Bukkit.getPluginManager().getPlugin(PROTOCOL_LIB);
    if (protocolPlugin == null || !protocolPlugin.isEnabled()) {
      return null;
    }

    try {
      ClassLoader loader = protocolPlugin.getClass().getClassLoader();
      Class<?> protocolLibraryClass = loader.loadClass("com.comphenix.protocol.ProtocolLibrary");
      Class<?> protocolManagerClass = loader.loadClass("com.comphenix.protocol.ProtocolManager");
      Class<?> packetListenerClass =
          loader.loadClass("com.comphenix.protocol.events.PacketListener");
      Class<?> packetEventClass = loader.loadClass("com.comphenix.protocol.events.PacketEvent");
      Class<?> listeningWhitelistClass =
          loader.loadClass("com.comphenix.protocol.events.ListeningWhitelist");
      Class<?> packetTypeClass = loader.loadClass("com.comphenix.protocol.PacketType");
      Class<?> packetContainerClass =
          loader.loadClass("com.comphenix.protocol.events.PacketContainer");
      Class<?> blockPositionClass =
          loader.loadClass("com.comphenix.protocol.wrappers.BlockPosition");

      Object protocolManager = protocolLibraryClass.getMethod("getProtocolManager").invoke(null);
      Method addPacketListener =
          protocolManagerClass.getMethod("addPacketListener", packetListenerClass);
      Method removePacketListener =
          protocolManagerClass.getMethod("removePacketListener", packetListenerClass);
      Method packetGetBlockPositionModifier =
          packetContainerClass.getMethod("getBlockPositionModifier");
      Method packetGetIntegers = packetContainerClass.getMethod("getIntegers");
      Object emptyWhitelist = listeningWhitelistClass.getField("EMPTY_WHITELIST").get(null);

      return new ProtocolLibEnhancements(
          plugin,
          loader,
          protocolManager,
          packetTypeClass,
          packetListenerClass,
          listeningWhitelistClass,
          addPacketListener,
          removePacketListener,
          packetEventClass.getMethod("getPlayer"),
          packetEventClass.getMethod("getPacket"),
          packetEventClass.getMethod("getPacketType"),
          packetEventClass.getMethod("isCancelled"),
          packetGetBlockPositionModifier,
          packetGetIntegers,
          blockPositionClass.getMethod("getX"),
          blockPositionClass.getMethod("getY"),
          blockPositionClass.getMethod("getZ"),
          emptyWhitelist);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      ExortLog.warn("[ProtocolLib] Enhancements failed to initialize: " + e.getMessage());
      return null;
    }
  }

  public void unregister() {
    for (Object listener : List.copyOf(packetListeners)) {
      try {
        removePacketListener.invoke(protocolManager, listener);
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        ExortLog.warn("[ProtocolLib] Failed to unregister packet listener: " + e.getMessage());
      }
    }
    packetListeners.clear();
  }

  public void registerPickBridge(PickListener pickListener) {
    if (!plugin.getConfig().getBoolean("protocolLib.pickBridge.enabled", true)) {
      return;
    }

    boolean entityPick = plugin.getConfig().getBoolean("protocolLib.pickBridge.entityPick", true);
    try {
      Class<?> clientPacketTypesClass =
          loader.loadClass("com.comphenix.protocol.PacketType$Play$Client");
      Object blockPacketType = clientPacketTypesClass.getField(PICK_ITEM_FROM_BLOCK).get(null);
      Object entityPacketType = null;
      List<Object> packetTypes = new ArrayList<>();
      packetTypes.add(blockPacketType);
      if (entityPick) {
        try {
          entityPacketType = clientPacketTypesClass.getField(PICK_ITEM).get(null);
          packetTypes.add(entityPacketType);
        } catch (NoSuchFieldException e) {
          ExortLog.warn(
              "[ProtocolLib] Entity pick bridge disabled: ProtocolLib does not expose "
                  + PICK_ITEM
                  + ".");
        }
      }

      PickBridge bridge =
          new PickBridge(pickListener, blockPacketType, entityPacketType, entityPick);
      Object listener =
          createPacketListener(
              "Exort ProtocolLib pick bridge",
              createReceivingWhitelist(packetTypes),
              bridge::onPacketReceiving);
      addPacketListener.invoke(protocolManager, listener);
      packetListeners.add(listener);
      ExortLog.info(
          "[ProtocolLib] Pick bridge enabled"
              + (entityPacketType != null
                  ? " for block and display picking."
                  : " for block picking."));
    } catch (NoSuchFieldException e) {
      ExortLog.warn(
          "[ProtocolLib] Pick bridge disabled: ProtocolLib does not expose "
              + PICK_ITEM_FROM_BLOCK
              + ".");
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      ExortLog.warn("[ProtocolLib] Pick bridge failed to initialize: " + e.getMessage());
    }
  }

  private Object createPacketListener(
      String name, Object receivingWhitelist, PacketReceiver receiver) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          String methodName = method.getName();
          if ("onPacketReceiving".equals(methodName)) {
            receiver.onPacketReceiving(args[0]);
            return null;
          }
          if ("onPacketSending".equals(methodName)) {
            return null;
          }
          if ("getReceivingWhitelist".equals(methodName)) {
            return receivingWhitelist;
          }
          if ("getSendingWhitelist".equals(methodName)) {
            return emptyWhitelist;
          }
          if ("getPlugin".equals(methodName)) {
            return plugin;
          }
          if ("toString".equals(methodName)) {
            return name;
          }
          if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
          }
          if ("equals".equals(methodName)) {
            return args != null && args.length > 0 && proxy == args[0];
          }
          return null;
        };
    return java.lang.reflect.Proxy.newProxyInstance(
        loader, new Class<?>[] {packetListenerClass}, handler);
  }

  private Object createReceivingWhitelist(List<Object> packetTypes)
      throws ReflectiveOperationException {
    Object builder = listeningWhitelistClass.getMethod("newBuilder").invoke(null);
    builder.getClass().getMethod("highest").invoke(builder);
    Object packetTypeArray = Array.newInstance(packetTypeClass, packetTypes.size());
    for (int i = 0; i < packetTypes.size(); i++) {
      Array.set(packetTypeArray, i, packetTypes.get(i));
    }
    builder
        .getClass()
        .getMethod("types", packetTypeArray.getClass())
        .invoke(builder, packetTypeArray);
    return builder.getClass().getMethod("build").invoke(builder);
  }

  private PacketTarget readBlockTarget(Object event)
      throws ReflectiveOperationException, LinkageError {
    Object packet = eventGetPacket.invoke(event);
    Object modifier = packetGetBlockPositionModifier.invoke(packet);
    Object position = readModifier(modifier, 0);
    if (position == null) {
      return null;
    }
    int x = ((Number) blockPositionGetX.invoke(position)).intValue();
    int y = ((Number) blockPositionGetY.invoke(position)).intValue();
    int z = ((Number) blockPositionGetZ.invoke(position)).intValue();
    return new PacketTarget(x, y, z);
  }

  private Integer readEntityId(Object event) throws ReflectiveOperationException, LinkageError {
    Object packet = eventGetPacket.invoke(event);
    Object integers = packetGetIntegers.invoke(packet);
    Object value = readModifier(integers, 0);
    return value instanceof Number number ? number.intValue() : null;
  }

  private void debugFull(String message) {
    var service = plugin.getPickDebugService();
    if (service != null) {
      service.recordFull(message);
    }
  }

  private static Object readModifier(Object modifier, int index)
      throws ReflectiveOperationException {
    return modifier.getClass().getMethod("read", int.class).invoke(modifier, index);
  }

  private interface PacketReceiver {
    void onPacketReceiving(Object event);
  }

  private final class PickBridge {
    private final PickListener pickListener;
    private final Object blockPacketType;
    private final Object entityPacketType;
    private final boolean entityPick;

    PickBridge(
        PickListener pickListener,
        Object blockPacketType,
        Object entityPacketType,
        boolean entityPick) {
      this.pickListener = pickListener;
      this.blockPacketType = blockPacketType;
      this.entityPacketType = entityPacketType;
      this.entityPick = entityPick;
    }

    void onPacketReceiving(Object event) {
      Player player;
      Object packetType;
      boolean cancelled;
      try {
        player = (Player) eventGetPlayer.invoke(event);
        packetType = eventGetPacketType.invoke(event);
        cancelled = Boolean.TRUE.equals(eventIsCancelled.invoke(event));
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        debugFull("protocol pick event read failed error=" + e.getClass().getSimpleName());
        return;
      }
      if (player == null || packetType == null) {
        return;
      }
      if (blockPacketType.equals(packetType)) {
        handleBlockPacket(event, player, cancelled);
        return;
      }
      if (entityPick && entityPacketType != null && entityPacketType.equals(packetType)) {
        handleEntityPacket(event, player, cancelled);
      }
    }

    private void handleBlockPacket(Object event, Player player, boolean cancelled) {
      PacketTarget target;
      try {
        target = readBlockTarget(event);
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        debugFull("protocol block pick read failed error=" + e.getClass().getSimpleName());
        return;
      }
      if (target == null) {
        return;
      }
      Bukkit.getScheduler()
          .runTask(plugin, () -> handleScheduledBlockPick(player, target, cancelled));
    }

    private void handleEntityPacket(Object event, Player player, boolean cancelled) {
      Integer entityId;
      try {
        entityId = readEntityId(event);
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        debugFull("protocol entity pick read failed error=" + e.getClass().getSimpleName());
        return;
      }
      if (entityId == null) {
        return;
      }
      Bukkit.getScheduler()
          .runTask(plugin, () -> handleScheduledEntityPick(player, entityId, cancelled));
    }

    private void handleScheduledBlockPick(
        Player player, PacketTarget target, boolean cancelledBefore) {
      if (!player.isOnline()) {
        return;
      }
      Block packetBlock = player.getWorld().getBlockAt(target.x(), target.y(), target.z());
      Block playerTarget = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
      debugFull(
          "protocol event "
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
          pickListener.handleDirectPick(player, packetBlock, "ProtocolLib " + PICK_ITEM_FROM_BLOCK);
      if (handled) {
        debugFull(
            "protocol handled "
                + PICK_ITEM_FROM_BLOCK
                + " player="
                + player.getName()
                + " packetBlock="
                + pickListener.describeDebugBlock(packetBlock));
      }
    }

    private void handleScheduledEntityPick(Player player, int entityId, boolean cancelledBefore) {
      if (!player.isOnline()) {
        return;
      }
      Entity entity = findNearbyEntity(player, entityId);
      Block target = resolveDisplayTarget(entity);
      debugFull(
          "protocol event "
              + PICK_ITEM
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
      boolean handled = pickListener.handleDirectPick(player, target, "ProtocolLib " + PICK_ITEM);
      if (handled) {
        debugFull(
            "protocol handled "
                + PICK_ITEM
                + " player="
                + player.getName()
                + " target="
                + pickListener.describeDebugBlock(target));
      }
    }

    private Entity findNearbyEntity(Player player, int entityId) {
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

    private Block resolveDisplayTarget(Entity entity) {
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

    private String describeEntity(Entity entity) {
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
  }

  private record PacketTarget(int x, int y, int z) {}
}
