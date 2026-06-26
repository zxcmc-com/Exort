package com.zxcmc.exort.testsupport;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;

public final class BukkitTestDoubles {
  private static final Logger LOGGER = Logger.getLogger("ExortTest");
  private static final Map<UUID, TestWorld> WORLDS = new ConcurrentHashMap<>();

  private BukkitTestDoubles() {}

  public static Plugin plugin() {
    return proxy(
        Plugin.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getName" -> "Exort";
              case "namespace" -> "exort";
              case "getLogger" -> LOGGER;
              case "toString" -> "plugin(Exort)";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  public static TestWorld world(String name, UUID uid) {
    ensureServer();
    TestWorld world = new TestWorld(name, uid);
    WORLDS.put(uid, world);
    return world;
  }

  private static void ensureServer() {
    if (Bukkit.getServer() != null) {
      return;
    }
    Server server =
        proxy(
            Server.class,
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getWorld" -> getWorld(args == null ? null : args[0]);
                  case "getWorlds" -> WORLDS.values().stream().map(TestWorld::world).toList();
                  case "getName" -> "ExortTestServer";
                  case "getVersion" -> "test";
                  case "getBukkitVersion" -> "test";
                  case "getMinecraftVersion" -> "test";
                  case "getLogger" -> LOGGER;
                  case "isPrimaryThread" -> true;
                  case "toString" -> "server(ExortTest)";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> args != null && args.length == 1 && proxy == args[0];
                  default -> defaultValue(method.getReturnType());
                });
    try {
      Field serverField = Bukkit.class.getDeclaredField("server");
      serverField.setAccessible(true);
      serverField.set(null, server);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to install Bukkit test server", e);
    }
  }

  private static World getWorld(Object key) {
    if (key instanceof UUID uid) {
      TestWorld world = WORLDS.get(uid);
      return world == null ? null : world.world();
    }
    if (key instanceof String name) {
      return WORLDS.values().stream()
          .filter(world -> world.name().equals(name))
          .findFirst()
          .map(TestWorld::world)
          .orElse(null);
    }
    return null;
  }

  public static Object defaultValue(Class<?> returnType) {
    if (returnType == Void.TYPE) return null;
    if (returnType == Boolean.TYPE) return false;
    if (returnType == Byte.TYPE) return (byte) 0;
    if (returnType == Short.TYPE) return (short) 0;
    if (returnType == Integer.TYPE) return 0;
    if (returnType == Long.TYPE) return 0L;
    if (returnType == Float.TYPE) return 0.0f;
    if (returnType == Double.TYPE) return 0.0d;
    if (returnType == Character.TYPE) return '\0';
    return null;
  }

  @SuppressWarnings("unchecked")
  public static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T)
        Proxy.newProxyInstance(
            BukkitTestDoubles.class.getClassLoader(), new Class<?>[] {type}, handler);
  }

  public static final class TestWorld {
    private final String name;
    private final Map<ChunkPos, TestChunk> chunks = new HashMap<>();
    private final Map<BlockPos, TestBlock> blocks = new HashMap<>();
    private final World world;
    private int getBlockAtCalls;

    private TestWorld(String name, UUID uid) {
      this.name = Objects.requireNonNull(name, "name");
      Objects.requireNonNull(uid, "uid");
      this.world =
          proxy(
              World.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getUID" -> uid;
                    case "getName" -> name;
                    case "getLoadedChunks" ->
                        chunks.values().stream()
                            .filter(TestChunk::loaded)
                            .map(TestChunk::chunk)
                            .toArray(Chunk[]::new);
                    case "isChunkLoaded" -> isChunkLoaded((int) args[0], (int) args[1]);
                    case "getChunkAt" -> chunk((int) args[0], (int) args[1]).chunk();
                    case "getBlockAt" -> {
                      getBlockAtCalls++;
                      yield block((int) args[0], (int) args[1], (int) args[2]).block();
                    }
                    case "toString" -> "world(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
    }

    public String name() {
      return name;
    }

    public World world() {
      return world;
    }

    public Block block(int x, int y, int z, Material material) {
      TestBlock block = block(x, y, z);
      block.setType(material);
      return block.block();
    }

    public TestBlock block(int x, int y, int z) {
      return blocks.computeIfAbsent(new BlockPos(x, y, z), pos -> new TestBlock(this, pos));
    }

    public void loadChunk(int chunkX, int chunkZ) {
      chunk(chunkX, chunkZ).setLoaded(true);
    }

    public void unloadChunk(int chunkX, int chunkZ) {
      chunk(chunkX, chunkZ).setLoaded(false);
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
      return chunk(chunkX, chunkZ).loaded();
    }

    public int getBlockAtCalls() {
      return getBlockAtCalls;
    }

    private TestChunk chunk(int chunkX, int chunkZ) {
      return chunks.computeIfAbsent(new ChunkPos(chunkX, chunkZ), pos -> new TestChunk(this, pos));
    }
  }

  public static final class TestBlock {
    private final Block block;
    private Material type = Material.AIR;
    private Object blockData;

    private TestBlock(TestWorld world, BlockPos pos) {
      this.block =
          proxy(
              Block.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getWorld" -> world.world();
                    case "getChunk" -> world.chunk(pos.x() >> 4, pos.z() >> 4).chunk();
                    case "getX" -> pos.x();
                    case "getY" -> pos.y();
                    case "getZ" -> pos.z();
                    case "getType" -> type;
                    case "setType" -> {
                      setType((Material) args[0]);
                      yield null;
                    }
                    case "getBlockData" -> blockData;
                    case "setBlockData" -> {
                      blockData = args[0];
                      yield null;
                    }
                    case "getRelative" -> {
                      BlockFace face = (BlockFace) args[0];
                      yield world
                          .block(
                              pos.x() + face.getModX(),
                              pos.y() + face.getModY(),
                              pos.z() + face.getModZ())
                          .block();
                    }
                    case "toString" -> "block(" + type + " " + pos + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
    }

    public Block block() {
      return block;
    }

    private void setType(Material type) {
      this.type = type == null ? Material.AIR : type;
      this.blockData = this.type == Material.CHORUS_PLANT ? fullChorusData() : null;
    }
  }

  private record BlockPos(int x, int y, int z) {}

  private record ChunkPos(int x, int z) {}

  private static final class TestChunk {
    private final SimplePdc pdc = new SimplePdc();
    private final Chunk chunk;
    private boolean loaded = true;

    private TestChunk(TestWorld world, ChunkPos pos) {
      this.chunk =
          proxy(
              Chunk.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getWorld" -> world.world();
                    case "getX" -> pos.x();
                    case "getZ" -> pos.z();
                    case "isLoaded" -> loaded;
                    case "getPersistentDataContainer" -> pdc.proxy();
                    case "toString" -> "chunk(" + pos + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
    }

    private Chunk chunk() {
      return chunk;
    }

    private boolean loaded() {
      return loaded;
    }

    private void setLoaded(boolean loaded) {
      this.loaded = loaded;
    }
  }

  private static MultipleFacing fullChorusData() {
    Set<BlockFace> faces =
        EnumSet.of(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST);
    return proxy(
        MultipleFacing.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getAllowedFaces" -> faces;
              case "hasFace" -> faces.contains(args[0]);
              case "setFace" -> null;
              case "toString" -> "full-chorus-data";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static final class SimplePdc {
    private final Map<NamespacedKey, Object> values = new HashMap<>();

    private PersistentDataContainer proxy() {
      return BukkitTestDoubles.proxy(PersistentDataContainer.class, this::invoke);
    }

    private Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
      return switch (method.getName()) {
        case "set" -> {
          values.put((NamespacedKey) args[0], args[2]);
          yield null;
        }
        case "get" -> values.get((NamespacedKey) args[0]);
        case "getOrDefault" -> values.getOrDefault((NamespacedKey) args[0], args[2]);
        case "has" -> values.containsKey((NamespacedKey) args[0]);
        case "remove" -> {
          values.remove((NamespacedKey) args[0]);
          yield null;
        }
        case "isEmpty" -> values.isEmpty();
        case "getKeys" -> Set.copyOf(values.keySet());
        case "getAdapterContext" -> adapterContext();
        case "toString" -> "pdc" + values;
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> args != null && args.length == 1 && proxy == args[0];
        default -> defaultValue(method.getReturnType());
      };
    }

    private PersistentDataAdapterContext adapterContext() {
      return BukkitTestDoubles.proxy(
          PersistentDataAdapterContext.class,
          (proxy, method, args) ->
              switch (method.getName()) {
                case "newPersistentDataContainer" -> new SimplePdc().proxy();
                case "toString" -> "pdc-adapter";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> args != null && args.length == 1 && proxy == args[0];
                default -> defaultValue(method.getReturnType());
              });
    }
  }
}
