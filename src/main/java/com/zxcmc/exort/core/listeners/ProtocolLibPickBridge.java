package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.logging.ExortLog;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ProtocolLibPickBridge {
  private static final String PROTOCOL_LIB = "ProtocolLib";
  private static final String PACKET_NAME = "PICK_ITEM_FROM_BLOCK";

  private final ExortPlugin plugin;
  private final PickListener pickListener;
  private final Object protocolManager;
  private final Object packetListener;
  private final Method removePacketListener;
  private final Method eventGetPlayer;
  private final Method eventGetPacket;
  private final Method eventIsCancelled;
  private final Method packetGetBlockPositionModifier;
  private final Method blockPositionGetX;
  private final Method blockPositionGetY;
  private final Method blockPositionGetZ;

  private ProtocolLibPickBridge(
      ExortPlugin plugin,
      PickListener pickListener,
      Object protocolManager,
      Object packetListener,
      Method removePacketListener,
      Method eventGetPlayer,
      Method eventGetPacket,
      Method eventIsCancelled,
      Method packetGetBlockPositionModifier,
      Method blockPositionGetX,
      Method blockPositionGetY,
      Method blockPositionGetZ) {
    this.plugin = plugin;
    this.pickListener = pickListener;
    this.protocolManager = protocolManager;
    this.packetListener = packetListener;
    this.removePacketListener = removePacketListener;
    this.eventGetPlayer = eventGetPlayer;
    this.eventGetPacket = eventGetPacket;
    this.eventIsCancelled = eventIsCancelled;
    this.packetGetBlockPositionModifier = packetGetBlockPositionModifier;
    this.blockPositionGetX = blockPositionGetX;
    this.blockPositionGetY = blockPositionGetY;
    this.blockPositionGetZ = blockPositionGetZ;
  }

  public static ProtocolLibPickBridge tryRegister(ExortPlugin plugin, PickListener pickListener) {
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
      Class<?> clientPacketTypesClass =
          loader.loadClass("com.comphenix.protocol.PacketType$Play$Client");
      Class<?> blockPositionClass =
          loader.loadClass("com.comphenix.protocol.wrappers.BlockPosition");

      Object pickPacketType = clientPacketTypesClass.getField(PACKET_NAME).get(null);
      Object receivingWhitelist =
          createReceivingWhitelist(listeningWhitelistClass, packetTypeClass, pickPacketType);
      Object emptyWhitelist = listeningWhitelistClass.getField("EMPTY_WHITELIST").get(null);

      Method getProtocolManager = protocolLibraryClass.getMethod("getProtocolManager");
      Object protocolManager = getProtocolManager.invoke(null);
      Method addPacketListener =
          protocolManagerClass.getMethod("addPacketListener", packetListenerClass);
      Method removePacketListener =
          protocolManagerClass.getMethod("removePacketListener", packetListenerClass);

      Method eventGetPlayer = packetEventClass.getMethod("getPlayer");
      Method eventGetPacket = packetEventClass.getMethod("getPacket");
      Method eventIsCancelled = packetEventClass.getMethod("isCancelled");
      Method packetGetBlockPositionModifier =
          loader
              .loadClass("com.comphenix.protocol.events.PacketContainer")
              .getMethod("getBlockPositionModifier");
      Method blockPositionGetX = blockPositionClass.getMethod("getX");
      Method blockPositionGetY = blockPositionClass.getMethod("getY");
      Method blockPositionGetZ = blockPositionClass.getMethod("getZ");

      ProtocolLibPickBridge[] holder = new ProtocolLibPickBridge[1];
      InvocationHandler handler =
          (proxy, method, args) -> {
            String name = method.getName();
            if ("onPacketReceiving".equals(name)) {
              holder[0].onPacketReceiving(args[0]);
              return null;
            }
            if ("onPacketSending".equals(name)) {
              return null;
            }
            if ("getReceivingWhitelist".equals(name)) {
              return receivingWhitelist;
            }
            if ("getSendingWhitelist".equals(name)) {
              return emptyWhitelist;
            }
            if ("getPlugin".equals(name)) {
              return plugin;
            }
            if ("toString".equals(name)) {
              return "Exort ProtocolLib pick bridge";
            }
            if ("hashCode".equals(name)) {
              return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
              return proxy == args[0];
            }
            return null;
          };
      Object packetListener =
          java.lang.reflect.Proxy.newProxyInstance(
              loader, new Class<?>[] {packetListenerClass}, handler);
      ProtocolLibPickBridge bridge =
          new ProtocolLibPickBridge(
              plugin,
              pickListener,
              protocolManager,
              packetListener,
              removePacketListener,
              eventGetPlayer,
              eventGetPacket,
              eventIsCancelled,
              packetGetBlockPositionModifier,
              blockPositionGetX,
              blockPositionGetY,
              blockPositionGetZ);
      holder[0] = bridge;
      addPacketListener.invoke(protocolManager, packetListener);
      ExortLog.info("[ProtocolLib] Pick bridge enabled for provider-compatible barrier picking.");
      return bridge;
    } catch (NoSuchFieldException e) {
      ExortLog.warn(
          "[ProtocolLib] Pick bridge disabled: ProtocolLib does not expose " + PACKET_NAME + ".");
      return null;
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      ExortLog.warn("[ProtocolLib] Pick bridge failed to initialize: " + e.getMessage());
      return null;
    }
  }

  public void unregister() {
    try {
      removePacketListener.invoke(protocolManager, packetListener);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      ExortLog.warn("[ProtocolLib] Pick bridge failed to unregister: " + e.getMessage());
    }
  }

  private void onPacketReceiving(Object event) {
    Player player;
    PacketTarget target;
    boolean cancelled;
    try {
      player = (Player) eventGetPlayer.invoke(event);
      target = readPacketTarget(event);
      cancelled = Boolean.TRUE.equals(eventIsCancelled.invoke(event));
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      debugFull("protocol event read failed error=" + e.getClass().getSimpleName());
      return;
    }
    if (player == null || target == null) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> handleScheduledPick(player, target, cancelled));
  }

  private PacketTarget readPacketTarget(Object event)
      throws ReflectiveOperationException, LinkageError {
    Object packet = eventGetPacket.invoke(event);
    Object modifier = packetGetBlockPositionModifier.invoke(packet);
    Object position = modifier.getClass().getMethod("read", int.class).invoke(modifier, 0);
    if (position == null) {
      return null;
    }
    int x = ((Number) blockPositionGetX.invoke(position)).intValue();
    int y = ((Number) blockPositionGetY.invoke(position)).intValue();
    int z = ((Number) blockPositionGetZ.invoke(position)).intValue();
    return new PacketTarget(x, y, z);
  }

  private void handleScheduledPick(Player player, PacketTarget target, boolean cancelledBefore) {
    if (!player.isOnline()) {
      return;
    }
    Block packetBlock = player.getWorld().getBlockAt(target.x(), target.y(), target.z());
    Block playerTarget = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
    debugFull(
        "protocol event "
            + PACKET_NAME
            + " player="
            + player.getName()
            + " cancelledBefore="
            + cancelledBefore
            + " packetBlock="
            + pickListener.describeDebugBlock(packetBlock)
            + " playerTarget="
            + pickListener.describeDebugBlock(playerTarget));
    boolean handled =
        pickListener.handleDirectPick(player, packetBlock, "ProtocolLib " + PACKET_NAME);
    if (handled) {
      debugFull(
          "protocol handled "
              + PACKET_NAME
              + " player="
              + player.getName()
              + " packetBlock="
              + pickListener.describeDebugBlock(packetBlock));
    }
  }

  private void debugFull(String message) {
    var service = plugin.getPickDebugService();
    if (service != null) {
      service.recordFull(message);
    }
  }

  private static Object createReceivingWhitelist(
      Class<?> listeningWhitelistClass, Class<?> packetTypeClass, Object pickPacketType)
      throws ReflectiveOperationException {
    Object builder = listeningWhitelistClass.getMethod("newBuilder").invoke(null);
    builder.getClass().getMethod("highest").invoke(builder);
    Object packetTypes = Array.newInstance(packetTypeClass, 1);
    Array.set(packetTypes, 0, pickPacketType);
    builder.getClass().getMethod("types", packetTypes.getClass()).invoke(builder, packetTypes);
    return builder.getClass().getMethod("build").invoke(builder);
  }

  private record PacketTarget(int x, int y, int z) {}
}
