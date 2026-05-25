package com.zxcmc.exort.core.protocol;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.listeners.PickListener;
import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.display.DisplayTags;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class ProtocolLibEnhancements {
  private static final String PROTOCOL_LIB = "ProtocolLib";
  private static final String PICK_ITEM_FROM_BLOCK = "PICK_ITEM_FROM_BLOCK";
  private static final String PICK_ITEM = "PICK_ITEM";
  private static final String SPAWN_ENTITY = "SPAWN_ENTITY";
  private static final String ENTITY_METADATA = "ENTITY_METADATA";
  private static final String UPDATE_ATTRIBUTES = "UPDATE_ATTRIBUTES";
  private static final String ENTITY_TELEPORT = "ENTITY_TELEPORT";
  private static final String ENTITY_DESTROY = "ENTITY_DESTROY";
  private static final double ENTITY_PICK_LOOKUP_RANGE = 8.0;

  public enum FeatureStatus {
    ENABLED,
    PARTIAL,
    DISABLED_BY_CONFIG,
    UNAVAILABLE,
    FALLBACK
  }

  public record FeatureProbe(FeatureStatus status, String detail) {}

  public record Diagnostics(
      String minecraftVersion,
      String protocolLibVersion,
      FeatureProbe base,
      FeatureProbe pickBridge,
      FeatureProbe entityPick,
      FeatureProbe placementGuard,
      FeatureProbe placementGuardScale,
      FeatureProbe placementGuardTeleport) {}

  private enum Feature {
    BASE,
    PICK_BRIDGE,
    ENTITY_PICK,
    PLACEMENT_GUARD,
    PLACEMENT_GUARD_SCALE,
    PLACEMENT_GUARD_TELEPORT
  }

  private final ExortPlugin plugin;
  private final ClassLoader loader;
  private final String minecraftVersion;
  private final String protocolLibVersion;
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
  private final Map<Feature, FeatureProbe> featureProbes = new EnumMap<>(Feature.class);

  private ProtocolLibEnhancements(
      ExortPlugin plugin,
      ClassLoader loader,
      String minecraftVersion,
      String protocolLibVersion,
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
    this.minecraftVersion = minecraftVersion;
    this.protocolLibVersion = protocolLibVersion;
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

    String minecraftVersion = Bukkit.getMinecraftVersion();
    String protocolLibVersion = protocolPlugin.getPluginMeta().getVersion();
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

      ProtocolLibEnhancements enhancements =
          new ProtocolLibEnhancements(
              plugin,
              loader,
              minecraftVersion,
              protocolLibVersion,
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
      enhancements.setProbe(
          Feature.BASE, FeatureStatus.ENABLED, "ProtocolLib " + protocolLibVersion);
      ExortLog.success("[ProtocolLib] Integration enabled.");
      return enhancements;
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      ExortLog.warn(
          "[ProtocolLib] Enhancements failed to initialize: "
              + describeError(e)
              + " "
              + ProtocolLibCompatibility.failureAdvice(minecraftVersion, protocolLibVersion));
      return null;
    }
  }

  public Diagnostics diagnostics() {
    return new Diagnostics(
        minecraftVersion,
        protocolLibVersion,
        probe(Feature.BASE),
        probe(Feature.PICK_BRIDGE),
        probe(Feature.ENTITY_PICK),
        probe(Feature.PLACEMENT_GUARD),
        probe(Feature.PLACEMENT_GUARD_SCALE),
        probe(Feature.PLACEMENT_GUARD_TELEPORT));
  }

  public void markPlacementGuardDisabledByConfig() {
    setProbe(Feature.PLACEMENT_GUARD, FeatureStatus.DISABLED_BY_CONFIG, "Disabled by config.");
    setProbe(
        Feature.PLACEMENT_GUARD_SCALE, FeatureStatus.DISABLED_BY_CONFIG, "Disabled by config.");
    setProbe(
        Feature.PLACEMENT_GUARD_TELEPORT, FeatureStatus.DISABLED_BY_CONFIG, "Disabled by config.");
  }

  public void markPlacementGuardRuntimeFallback(String reason) {
    setProbe(
        Feature.PLACEMENT_GUARD,
        FeatureStatus.FALLBACK,
        reason == null || reason.isBlank() ? "Runtime packet failure." : reason);
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
      setProbe(Feature.PICK_BRIDGE, FeatureStatus.DISABLED_BY_CONFIG, "Disabled by config.");
      setProbe(Feature.ENTITY_PICK, FeatureStatus.DISABLED_BY_CONFIG, "Disabled by config.");
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
          setProbe(Feature.ENTITY_PICK, FeatureStatus.UNAVAILABLE, "Missing " + PICK_ITEM + ".");
          ExortLog.warn(
              "[ProtocolLib] Entity pick bridge disabled: ProtocolLib does not expose "
                  + PICK_ITEM
                  + ". "
                  + fallbackAdvice());
        }
      } else {
        setProbe(Feature.ENTITY_PICK, FeatureStatus.DISABLED_BY_CONFIG, "Disabled by config.");
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
      setProbe(
          Feature.PICK_BRIDGE,
          entityPick && entityPacketType == null ? FeatureStatus.PARTIAL : FeatureStatus.ENABLED,
          entityPacketType != null
              ? "Block and entity pick packets registered."
              : "Block pick packet registered.");
      if (entityPacketType != null) {
        setProbe(Feature.ENTITY_PICK, FeatureStatus.ENABLED, "Entity pick packet registered.");
      }
      ExortLog.info(
          "[ProtocolLib] Pick bridge enabled"
              + (entityPacketType != null
                  ? " for block and display picking."
                  : " for block picking."));
    } catch (NoSuchFieldException e) {
      setProbe(
          Feature.PICK_BRIDGE, FeatureStatus.UNAVAILABLE, "Missing " + PICK_ITEM_FROM_BLOCK + ".");
      setProbe(Feature.ENTITY_PICK, FeatureStatus.UNAVAILABLE, "Pick bridge unavailable.");
      ExortLog.warn(
          "[ProtocolLib] Pick bridge disabled: ProtocolLib does not expose "
              + PICK_ITEM_FROM_BLOCK
              + ". "
              + fallbackAdvice());
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      String detail = describeError(e);
      setProbe(Feature.PICK_BRIDGE, FeatureStatus.UNAVAILABLE, detail);
      setProbe(Feature.ENTITY_PICK, FeatureStatus.UNAVAILABLE, "Pick bridge unavailable.");
      ExortLog.warn(
          "[ProtocolLib] Pick bridge failed to initialize: " + detail + " " + fallbackAdvice());
    }
  }

  public PlacementGuardPackets tryCreatePlacementGuardPackets(double guardScale) {
    try {
      Class<?> serverPacketTypesClass =
          loader.loadClass("com.comphenix.protocol.PacketType$Play$Server");
      Class<?> protocolManagerClass = loader.loadClass("com.comphenix.protocol.ProtocolManager");
      Class<?> packetContainerClass =
          loader.loadClass("com.comphenix.protocol.events.PacketContainer");
      Class<?> wrappedDataValueClass =
          loader.loadClass("com.comphenix.protocol.wrappers.WrappedDataValue");
      Class<?> serializerClass =
          loader.loadClass("com.comphenix.protocol.wrappers.WrappedDataWatcher$Serializer");
      Class<?> wrappedAttributeClass =
          loader.loadClass("com.comphenix.protocol.wrappers.WrappedAttribute");

      Method createPacket = protocolManagerClass.getMethod("createPacket", packetTypeClass);
      Method sendServerPacket =
          protocolManagerClass.getMethod(
              "sendServerPacket", Player.class, packetContainerClass, boolean.class);
      Constructor<?> dataValueConstructor =
          wrappedDataValueClass.getConstructor(int.class, serializerClass, Object.class);
      MetadataIndexes metadataIndexes = resolvePlacementGuardMetadataIndexes();

      PlacementGuardPackets packets =
          new PlacementGuardPackets(
              protocolManager,
              createPacket,
              sendServerPacket,
              packetContainerClass,
              dataValueConstructor,
              wrappedAttributeClass,
              tryFindPositionMoveRotationFactory(loader),
              serverPacketTypesClass.getField(SPAWN_ENTITY).get(null),
              serverPacketTypesClass.getField(ENTITY_METADATA).get(null),
              serverPacketTypesClass.getField(UPDATE_ATTRIBUTES).get(null),
              serverPacketTypesClass.getField(ENTITY_TELEPORT).get(null),
              serverPacketTypesClass.getField(ENTITY_DESTROY).get(null),
              createDataWatcherSerializer(loader, serializerClass, Byte.class),
              createDataWatcherSerializer(loader, serializerClass, Boolean.class),
              metadataIndexes,
              guardScale);
      packets.validate();
      FeatureStatus placementStatus =
          packets.attributesSupported() && packets.teleportSupported()
              ? FeatureStatus.ENABLED
              : FeatureStatus.PARTIAL;
      setProbe(Feature.PLACEMENT_GUARD, placementStatus, packets.capabilitySummary());
      setProbe(
          Feature.PLACEMENT_GUARD_SCALE,
          packets.attributesSupported() ? FeatureStatus.ENABLED : FeatureStatus.UNAVAILABLE,
          packets.attributesSupported()
              ? "Scale attribute packet available."
              : "Scale attribute packet unavailable.");
      setProbe(
          Feature.PLACEMENT_GUARD_TELEPORT,
          packets.teleportSupported() ? FeatureStatus.ENABLED : FeatureStatus.FALLBACK,
          packets.teleportSupported()
              ? "Teleport packet available."
              : "Teleport packet unavailable; using destroy/spawn fallback.");
      return packets;
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      String detail = describeError(e);
      setProbe(Feature.PLACEMENT_GUARD, FeatureStatus.FALLBACK, detail);
      setProbe(Feature.PLACEMENT_GUARD_SCALE, FeatureStatus.UNAVAILABLE, detail);
      setProbe(Feature.PLACEMENT_GUARD_TELEPORT, FeatureStatus.UNAVAILABLE, detail);
      ExortLog.warn(
          "[ProtocolLib] Placement guard packet backend unavailable; using Paper entity placement"
              + " guard. Cause: "
              + detail
              + " "
              + fallbackAdvice());
      return null;
    }
  }

  private void setProbe(Feature feature, FeatureStatus status, String detail) {
    featureProbes.put(feature, new FeatureProbe(status, detail == null ? "" : detail));
  }

  private FeatureProbe probe(Feature feature) {
    return featureProbes.getOrDefault(feature, new FeatureProbe(FeatureStatus.UNAVAILABLE, ""));
  }

  private String fallbackAdvice() {
    return ProtocolLibCompatibility.failureAdvice(minecraftVersion, protocolLibVersion);
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

  private static String describeError(Throwable error) {
    Throwable root = error;
    while (root instanceof InvocationTargetException invocation && invocation.getCause() != null) {
      root = invocation.getCause();
    }
    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      return root.getClass().getSimpleName();
    }
    return root.getClass().getSimpleName() + ": " + message;
  }

  private interface PacketReceiver {
    void onPacketReceiving(Object event);
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

  private static Method tryFindPositionMoveRotationFactory(ClassLoader loader) {
    try {
      Class<?> wrappedPositionMoveRotationClass =
          loader.loadClass("com.comphenix.protocol.wrappers.WrappedPositionMoveRotation");
      return wrappedPositionMoveRotationClass.getMethod(
          "create", Vector.class, Vector.class, float.class, float.class);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      return null;
    }
  }

  private static Object createDataWatcherSerializer(
      ClassLoader loader, Class<?> serializerClass, Class<?> valueClass)
      throws ReflectiveOperationException {
    Constructor<?> serializerConstructor =
        serializerClass.getConstructor(Type.class, Object.class, boolean.class);
    Object handle = null;
    try {
      handle = findDataWatcherSerializerHandle(loader, valueClass);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      // ProtocolLib's own registry path below is still valid on older versions.
    }
    if (handle == null) {
      handle = findProtocolLibRegistrySerializerHandle(loader, valueClass);
    }
    if (handle == null) {
      throw new IllegalStateException("Data watcher serializer is unavailable for " + valueClass);
    }
    return serializerConstructor.newInstance(valueClass, handle, Boolean.FALSE);
  }

  private static Object findDataWatcherSerializerHandle(ClassLoader loader, Class<?> valueClass)
      throws ReflectiveOperationException {
    Class<?> minecraftReflectionClass =
        loader.loadClass("com.comphenix.protocol.utility.MinecraftReflection");
    Class<?> registryClass =
        (Class<?>) minecraftReflectionClass.getMethod("getDataWatcherRegistryClass").invoke(null);
    Class<?> serializerHandleClass =
        (Class<?>) minecraftReflectionClass.getMethod("getDataWatcherSerializerClass").invoke(null);
    for (Field field : registryClass.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())
          || !serializerHandleClass.isAssignableFrom(field.getType())
          || !valueClass.equals(rawSerializerType(field.getGenericType()))) {
        continue;
      }
      field.setAccessible(true);
      Object handle = field.get(null);
      if (handle != null) {
        return handle;
      }
    }
    return null;
  }

  private static Object findProtocolLibRegistrySerializerHandle(
      ClassLoader loader, Class<?> valueClass) {
    try {
      Class<?> registryClass =
          loader.loadClass("com.comphenix.protocol.wrappers.WrappedDataWatcher$Registry");
      Object serializer = registryClass.getMethod("get", Class.class).invoke(null, valueClass);
      return serializer == null
          ? null
          : serializer.getClass().getMethod("getHandle").invoke(serializer);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      return null;
    }
  }

  private static Type rawSerializerType(Type type) {
    if (!(type instanceof ParameterizedType parameterized)) {
      return null;
    }
    Type[] arguments = parameterized.getActualTypeArguments();
    if (arguments.length != 1) {
      return null;
    }
    Type argument = arguments[0];
    if (argument instanceof ParameterizedType nested
        && Optional.class.equals(nested.getRawType())
        && nested.getActualTypeArguments().length == 1) {
      return nested.getActualTypeArguments()[0];
    }
    return argument;
  }

  private record MetadataIndexes(int entityFlags, int noGravity, int armorStandFlags) {}

  public static final class PlacementGuardPackets {
    private static final byte ENTITY_FLAG_INVISIBLE = 0x20;
    private static final byte ARMOR_STAND_FLAGS_SMALL_NO_BASEPLATE = 0x01 | 0x08;
    private static final String[] SCALE_ATTRIBUTE_KEYS = {
      "minecraft:scale", "scale", "minecraft:generic.scale", "generic.scale"
    };

    private final Object protocolManager;
    private final Method createPacket;
    private final Method sendServerPacket;
    private final Class<?> packetContainerClass;
    private final Constructor<?> dataValueConstructor;
    private final Class<?> wrappedAttributeClass;
    private final Method createPositionMoveRotation;
    private final Object spawnEntityPacketType;
    private final Object entityMetadataPacketType;
    private final Object updateAttributesPacketType;
    private final Object entityTeleportPacketType;
    private final Object entityDestroyPacketType;
    private final Object byteSerializer;
    private final Object booleanSerializer;
    private final MetadataIndexes metadataIndexes;
    private final double guardScale;
    private String lastFailure = "";
    private boolean attributesSupported = true;
    private boolean teleportSupported;

    private PlacementGuardPackets(
        Object protocolManager,
        Method createPacket,
        Method sendServerPacket,
        Class<?> packetContainerClass,
        Constructor<?> dataValueConstructor,
        Class<?> wrappedAttributeClass,
        Method createPositionMoveRotation,
        Object spawnEntityPacketType,
        Object entityMetadataPacketType,
        Object updateAttributesPacketType,
        Object entityTeleportPacketType,
        Object entityDestroyPacketType,
        Object byteSerializer,
        Object booleanSerializer,
        MetadataIndexes metadataIndexes,
        double guardScale) {
      this.protocolManager = protocolManager;
      this.createPacket = createPacket;
      this.sendServerPacket = sendServerPacket;
      this.packetContainerClass = packetContainerClass;
      this.dataValueConstructor = dataValueConstructor;
      this.wrappedAttributeClass = wrappedAttributeClass;
      this.createPositionMoveRotation = createPositionMoveRotation;
      this.spawnEntityPacketType = spawnEntityPacketType;
      this.entityMetadataPacketType = entityMetadataPacketType;
      this.updateAttributesPacketType = updateAttributesPacketType;
      this.entityTeleportPacketType = entityTeleportPacketType;
      this.entityDestroyPacketType = entityDestroyPacketType;
      this.byteSerializer = byteSerializer;
      this.booleanSerializer = booleanSerializer;
      this.metadataIndexes = metadataIndexes;
      this.guardScale = guardScale;
    }

    public boolean spawnArmorStand(
        Player player, int entityId, UUID entityUuid, Location location) {
      try {
        send(player, createSpawnPacket(entityId, entityUuid, location));
        send(player, createMetadataPacket(entityId));
        if (attributesSupported) {
          send(player, createAttributesPacket(entityId));
        }
        return true;
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        recordRuntimeFailure("spawn fake placement guard", e);
        try {
          send(player, createDestroyPacket(entityId));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
        return false;
      }
    }

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
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        recordRuntimeFailure("move fake placement guard", e);
        return false;
      }
    }

    public boolean destroyEntity(Player player, int entityId) {
      try {
        send(player, createDestroyPacket(entityId));
        return true;
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        recordRuntimeFailure("destroy fake placement guard", e);
        return false;
      }
    }

    public boolean attributesSupported() {
      return attributesSupported;
    }

    public boolean teleportSupported() {
      return teleportSupported;
    }

    public String lastFailure() {
      return lastFailure;
    }

    public String capabilitySummary() {
      return "fake entity packets available; scale="
          + attributesSupported
          + ", teleport="
          + teleportSupported
          + ".";
    }

    private void validate() throws ReflectiveOperationException {
      Location origin = new Location(null, 0.0, 0.0, 0.0);
      createSpawnPacket(1, new UUID(0L, 1L), origin);
      createMetadataPacket(1);
      createDestroyPacket(1);
      attributesSupported = canCreateAttributesPacket(1);
      if (!attributesSupported) {
        ExortLog.warn(
            "[ProtocolLib] Placement guard scale attribute packet unavailable; fake guards will"
                + " use small ArmorStand metadata only.");
      }
      teleportSupported = canCreateTeleportPacket(origin);
    }

    private void recordRuntimeFailure(String action, Throwable error) {
      lastFailure = action + ": " + describeError(error);
    }

    private boolean canCreateAttributesPacket(int entityId) {
      try {
        createAttributesPacket(entityId);
        return true;
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        return false;
      }
    }

    private boolean canCreateTeleportPacket(Location origin) {
      try {
        createTeleportPacket(1, origin);
        return true;
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        return false;
      }
    }

    private Object createSpawnPacket(int entityId, UUID entityUuid, Location location)
        throws ReflectiveOperationException {
      Object packet = createPacket(spawnEntityPacketType);
      writeRequired(modifier(packet, "getIntegers"), 0, entityId, SPAWN_ENTITY + ".id");
      writeRequired(modifier(packet, "getUUIDs"), 0, entityUuid, SPAWN_ENTITY + ".uuid");
      writeRequired(
          modifier(packet, "getEntityTypeModifier"),
          0,
          EntityType.ARMOR_STAND,
          SPAWN_ENTITY + ".type");
      Object doubles = modifier(packet, "getDoubles");
      writeRequired(doubles, 0, location.getX(), SPAWN_ENTITY + ".x");
      writeRequired(doubles, 1, location.getY(), SPAWN_ENTITY + ".y");
      writeRequired(doubles, 2, location.getZ(), SPAWN_ENTITY + ".z");
      Object bytes = modifier(packet, "getBytes");
      writeIfPresent(bytes, 0, (byte) 0);
      writeIfPresent(bytes, 1, (byte) 0);
      writeIfPresent(bytes, 2, (byte) 0);
      Object integers = modifier(packet, "getIntegers");
      writeIfPresent(integers, 1, 0);
      return packet;
    }

    private Object createMetadataPacket(int entityId) throws ReflectiveOperationException {
      Object packet = createPacket(entityMetadataPacketType);
      writeRequired(modifier(packet, "getIntegers"), 0, entityId, ENTITY_METADATA + ".id");
      List<Object> values = new ArrayList<>();
      values.add(
          dataValue(
              metadataIndexes.entityFlags(), byteSerializer, Byte.valueOf(ENTITY_FLAG_INVISIBLE)));
      values.add(dataValue(metadataIndexes.noGravity(), booleanSerializer, Boolean.TRUE));
      values.add(
          dataValue(
              metadataIndexes.armorStandFlags(),
              byteSerializer,
              Byte.valueOf(ARMOR_STAND_FLAGS_SMALL_NO_BASEPLATE)));
      writeRequired(
          modifier(packet, "getDataValueCollectionModifier"),
          0,
          values,
          ENTITY_METADATA + ".values");
      return packet;
    }

    private Object createAttributesPacket(int entityId) throws ReflectiveOperationException {
      Object packet = createPacket(updateAttributesPacketType);
      writeRequired(modifier(packet, "getIntegers"), 0, entityId, UPDATE_ATTRIBUTES + ".id");
      writeRequired(
          modifier(packet, "getAttributeCollectionModifier"),
          0,
          List.of(createScaleAttribute(packet)),
          UPDATE_ATTRIBUTES + ".attributes");
      return packet;
    }

    private Object createTeleportPacket(int entityId, Location location)
        throws ReflectiveOperationException {
      Object packet = createPacket(entityTeleportPacketType);
      writeRequired(modifier(packet, "getIntegers"), 0, entityId, ENTITY_TELEPORT + ".id");
      if (!writeTeleportPosition(packet, location)) {
        throw new IllegalStateException(ENTITY_TELEPORT + ".position field is unavailable.");
      }
      Object bytes = modifier(packet, "getBytes");
      writeIfPresent(bytes, 0, (byte) 0);
      writeIfPresent(bytes, 1, (byte) 0);
      Object booleans = modifier(packet, "getBooleans");
      writeIfPresent(booleans, 0, Boolean.FALSE);
      return packet;
    }

    private boolean writeTeleportPosition(Object packet, Location location)
        throws ReflectiveOperationException {
      Object doubles = modifier(packet, "getDoubles");
      if (modifierSize(doubles) >= 3) {
        writeRequired(doubles, 0, location.getX(), ENTITY_TELEPORT + ".x");
        writeRequired(doubles, 1, location.getY(), ENTITY_TELEPORT + ".y");
        writeRequired(doubles, 2, location.getZ(), ENTITY_TELEPORT + ".z");
        return true;
      }
      Object positionMoveRotation =
          createPositionMoveRotation == null
              ? null
              : createPositionMoveRotation.invoke(
                  null,
                  location.toVector(),
                  new Vector(0.0, 0.0, 0.0),
                  Float.valueOf(0.0f),
                  Float.valueOf(0.0f));
      if (positionMoveRotation == null) {
        return false;
      }
      return writeIfPresent(modifier(packet, "getPositionMoveRotation"), 0, positionMoveRotation);
    }

    private Object createDestroyPacket(int entityId) throws ReflectiveOperationException {
      Object packet = createPacket(entityDestroyPacketType);
      Object intLists = modifier(packet, "getIntLists");
      if (writeIfPresent(intLists, 0, List.of(entityId))) {
        return packet;
      }
      Object integerArrays = modifier(packet, "getIntegerArrays");
      if (writeIfPresent(integerArrays, 0, new int[] {entityId})) {
        return packet;
      }
      throw new IllegalStateException(ENTITY_DESTROY + " entity id list field is unavailable.");
    }

    private Object createPacket(Object packetType) throws ReflectiveOperationException {
      Object packet = createPacket.invoke(protocolManager, packetType);
      Object generic = modifier(packet, "getModifier");
      generic.getClass().getMethod("writeDefaults").invoke(generic);
      return packet;
    }

    private Object createScaleAttribute(Object packet) throws ReflectiveOperationException {
      RuntimeException runtimeFailure = null;
      ReflectiveOperationException reflectionFailure = null;
      for (String key : SCALE_ATTRIBUTE_KEYS) {
        try {
          return createScaleAttribute(packet, key);
        } catch (ReflectiveOperationException e) {
          reflectionFailure = e;
        } catch (RuntimeException e) {
          runtimeFailure = e;
        }
      }
      if (reflectionFailure != null) {
        throw reflectionFailure;
      }
      throw runtimeFailure == null
          ? new IllegalStateException("Scale attribute key is unavailable.")
          : runtimeFailure;
    }

    private Object createScaleAttribute(Object packet, String key)
        throws ReflectiveOperationException {
      Object builder = wrappedAttributeClass.getMethod("newBuilder").invoke(null);
      builder.getClass().getMethod("packet", packetContainerClass).invoke(builder, packet);
      builder.getClass().getMethod("attributeKey", String.class).invoke(builder, key);
      builder.getClass().getMethod("baseValue", double.class).invoke(builder, guardScale);
      return builder.getClass().getMethod("build").invoke(builder);
    }

    private Object dataValue(int index, Object serializer, Object value)
        throws ReflectiveOperationException {
      return dataValueConstructor.newInstance(index, serializer, value);
    }

    private Object modifier(Object packet, String methodName) throws ReflectiveOperationException {
      return packetContainerClass.getMethod(methodName).invoke(packet);
    }

    private void send(Player player, Object packet) throws ReflectiveOperationException {
      sendServerPacket.invoke(protocolManager, player, packet, Boolean.FALSE);
    }

    private static void writeRequired(Object modifier, int index, Object value, String field)
        throws ReflectiveOperationException {
      if (!writeIfPresent(modifier, index, value)) {
        throw new IllegalStateException(field + " field is unavailable.");
      }
    }

    private static boolean writeIfPresent(Object modifier, int index, Object value)
        throws ReflectiveOperationException {
      if (modifierSize(modifier) <= index) {
        return false;
      }
      modifier
          .getClass()
          .getMethod("write", int.class, Object.class)
          .invoke(modifier, index, value);
      return true;
    }

    private static int modifierSize(Object modifier) throws ReflectiveOperationException {
      Object size = modifier.getClass().getMethod("size").invoke(modifier);
      return ((Number) size).intValue();
    }
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
