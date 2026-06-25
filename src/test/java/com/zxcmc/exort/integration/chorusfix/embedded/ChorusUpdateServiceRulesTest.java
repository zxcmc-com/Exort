package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

final class ChorusUpdateServiceRulesTest {
  @Test
  void postPlacementSkipsExortCarrierWithoutVanillaSupport() {
    FakeWorld world = new FakeWorld();
    Block carrier = world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("down"));
    AtomicBoolean broken = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of()),
            detectorClaimingBlock(carrier),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(carrier, 0, ChorusUpdateService.ProcessingMode.NORMAL);

    assertFalse(broken.get());
    assertEquals(Material.CHORUS_PLANT, carrier.getType());
    assertEquals(0, service.status().brokenTotal());
    assertEquals(0, service.status().correctedTotal());
    assertEquals(1, service.status().skippedTotal());
  }

  @Test
  void postPlacementBreaksOrdinaryFlowerOnExortCarrierOnly() {
    FakeWorld world = new FakeWorld();
    Block flower = world.put(0, 64, 0, Material.CHORUS_FLOWER, null);
    Block carrier = world.put(0, 63, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("down"));
    ChorusUpdateService service =
        service(inactiveConfig(Set.of()), detectorClaimingBlock(carrier), Block::breakNaturally);

    service.processBlock(flower, 0, ChorusUpdateService.ProcessingMode.NORMAL);

    assertEquals(Material.AIR, flower.getType());
    assertEquals(Material.CHORUS_PLANT, carrier.getType());
    assertEquals(1, service.status().brokenTotal());
    assertEquals(0, service.status().correctedTotal());
  }

  @Test
  void postPlacementBreaksOrdinaryFlowerOnIgnoredCarrierOnlyWithoutDetectorCall() {
    FakeWorld world = new FakeWorld();
    Block flower = world.put(0, 64, 0, Material.CHORUS_FLOWER, null);
    Block carrier = world.put(0, 63, 0, Material.CHORUS_PLANT, ChorusFaceMask.ALL);
    AtomicBoolean detectorCalledForIgnoredCarrier = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of(ChorusFaceMask.ALL)),
            new ExortChorusCarrierDetector() {
              @Override
              public boolean isCustom(Block block, ChorusFaceMask mask) {
                if (ChorusFaceMask.ALL.equals(mask)) {
                  detectorCalledForIgnoredCarrier.set(true);
                  return true;
                }
                return false;
              }
            },
            Block::breakNaturally);

    service.processBlock(flower, 0, ChorusUpdateService.ProcessingMode.NORMAL);

    assertEquals(Material.AIR, flower.getType());
    assertEquals(Material.CHORUS_PLANT, carrier.getType());
    assertFalse(detectorCalledForIgnoredCarrier.get());
    assertEquals(1, service.status().brokenTotal());
    assertEquals(0, service.status().correctedTotal());
  }

  @Test
  void postPlacementKeepsFlowerOnVanillaSupport() {
    FakeWorld belowPlantWorld = new FakeWorld();
    Block flowerOnPlant = belowPlantWorld.put(0, 64, 0, Material.CHORUS_FLOWER, null);
    belowPlantWorld.put(0, 63, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("down"));

    FakeWorld endStoneWorld = new FakeWorld();
    Block flowerOnEndStone = endStoneWorld.put(0, 64, 0, Material.CHORUS_FLOWER, null);
    endStoneWorld.put(0, 63, 0, Material.END_STONE, null);

    FakeWorld sideWorld = new FakeWorld();
    Block sideFlower = sideWorld.put(0, 67, -1, Material.CHORUS_FLOWER, null);
    for (int y = 64; y < 72; y++) {
      sideWorld.put(0, y, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("up,down"));
    }
    AtomicBoolean broken = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of()),
            detector(false),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(flowerOnPlant, 0, ChorusUpdateService.ProcessingMode.NORMAL);
    service.processBlock(flowerOnEndStone, 0, ChorusUpdateService.ProcessingMode.NORMAL);
    service.processBlock(sideFlower, 0, ChorusUpdateService.ProcessingMode.NORMAL);

    assertFalse(broken.get());
    assertEquals(Material.CHORUS_FLOWER, flowerOnPlant.getType());
    assertEquals(Material.CHORUS_FLOWER, flowerOnEndStone.getType());
    assertEquals(Material.CHORUS_FLOWER, sideFlower.getType());
    assertEquals(0, service.status().brokenTotal());
  }

  @Test
  void sideFlowerPlacementUsesVanillaMutationRecheckMode() {
    FakeWorld sideWorld = new FakeWorld();
    Block sideFlower = sideWorld.put(0, 64, 0, Material.CHORUS_FLOWER, null);
    sideWorld.put(-1, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("up,down"));

    FakeWorld topWorld = new FakeWorld();
    Block topFlower = topWorld.put(0, 64, 0, Material.CHORUS_FLOWER, null);
    topWorld.put(0, 63, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("down"));

    ChorusUpdateService service = service(activeConfig(Set.of()), detector(false), block -> true);

    assertEquals(
        ChorusUpdateService.ProcessingMode.VANILLA_MUTATION,
        service.recheckModeAfterBlockPlace(sideFlower));
    assertEquals(
        ChorusUpdateService.ProcessingMode.NORMAL, service.recheckModeAfterBlockPlace(topFlower));
  }

  @Test
  void topFlowerPlacementOnSideConnectedStemUsesRepairMode() {
    FakeWorld world = new FakeWorld();
    Block topFlower = world.put(0, 65, 0, Material.CHORUS_FLOWER, null);
    world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("east,down"));
    world.put(1, 64, 0, Material.CHORUS_FLOWER, null);
    ChorusUpdateService service = service(activeConfig(Set.of()), detector(false), block -> true);

    assertEquals(
        ChorusUpdateService.ProcessingMode.FLOWER_PLACEMENT_REPAIR,
        service.recheckModeAfterBlockPlace(topFlower));
  }

  @Test
  void topFlowerPlacementOnExortCarrierStemDoesNotUseRepairMode() {
    FakeWorld world = new FakeWorld();
    Block topFlower = world.put(0, 65, 0, Material.CHORUS_FLOWER, null);
    world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("east,down"));
    ChorusUpdateService service =
        service(
            activeConfig(Set.of()),
            detectorClaimingMask(ChorusFaceMask.parse("east,down")),
            block -> true);

    assertEquals(
        ChorusUpdateService.ProcessingMode.NORMAL, service.recheckModeAfterBlockPlace(topFlower));
  }

  @Test
  void flowerPlacementRepairBreaksTopFlowerAndRemovesStaleUpFace() {
    FakeWorld world = new FakeWorld();
    world.put(0, 63, 0, Material.END_STONE, null);
    Block plant = world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("east,up,down"));
    world.put(1, 64, 0, Material.CHORUS_FLOWER, null);
    Block topFlower = world.put(0, 65, 0, Material.CHORUS_FLOWER, null);
    ChorusUpdateService service =
        service(inactiveConfig(Set.of()), detector(false), Block::breakNaturally);

    service.processBlock(topFlower, 0, ChorusUpdateService.ProcessingMode.FLOWER_PLACEMENT_REPAIR);
    service.processBlock(plant, 0, ChorusUpdateService.ProcessingMode.FLOWER_PLACEMENT_REPAIR);

    assertEquals(Material.AIR, topFlower.getType());
    assertEquals(Material.CHORUS_PLANT, plant.getType());
    assertEquals(ChorusFaceMask.parse("east,down"), maskOf(plant));
    assertEquals(1, service.status().brokenTotal());
    assertEquals(1, service.status().correctedTotal());
  }

  @Test
  void normalModeSkipsImpossibleMasks() {
    FakeWorld world = new FakeWorld();
    Block impossible =
        world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("north,up,down"));
    AtomicBoolean broken = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of()),
            detector(false),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(impossible, 0, ChorusUpdateService.ProcessingMode.NORMAL);

    assertFalse(broken.get());
    assertEquals(0, service.status().brokenTotal());
    assertEquals(1, service.status().skippedTotal());
  }

  @Test
  void vanillaMutationModeBreaksImpossibleMasksEvenWhenSurvivalWouldPass() {
    FakeWorld world = new FakeWorld();
    Block impossible =
        world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("north,up,down"));
    world.put(0, 63, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("up,down"));
    world.put(0, 65, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("up,down"));
    world.put(0, 64, -1, Material.CHORUS_FLOWER, null);
    AtomicBoolean broken = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of()),
            detector(false),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(impossible, 0, ChorusUpdateService.ProcessingMode.VANILLA_MUTATION);

    assertTrue(broken.get());
    assertEquals(1, service.status().brokenTotal());
  }

  @Test
  void vanillaMutationModeStillSkipsIgnoredImpossibleMasks() {
    ChorusFaceMask ignored = ChorusFaceMask.ALL;
    FakeWorld world = new FakeWorld();
    Block impossible = world.put(0, 64, 0, Material.CHORUS_PLANT, ignored);
    AtomicBoolean broken = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of(ignored)),
            detector(false),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(impossible, 0, ChorusUpdateService.ProcessingMode.VANILLA_MUTATION);

    assertFalse(broken.get());
    assertEquals(0, service.status().brokenTotal());
    assertEquals(1, service.status().skippedTotal());
  }

  @Test
  void vanillaMutationModeSkipsIgnoredImpossibleMasksBeforeDetector() {
    ChorusFaceMask ignored = ChorusFaceMask.ALL;
    FakeWorld world = new FakeWorld();
    Block impossible = world.put(0, 64, 0, Material.CHORUS_PLANT, ignored);
    AtomicBoolean broken = new AtomicBoolean();
    AtomicBoolean detectorCalled = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of(ignored)),
            detectorRecordingCall(detectorCalled, true),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(impossible, 0, ChorusUpdateService.ProcessingMode.VANILLA_MUTATION);

    assertFalse(detectorCalled.get());
    assertFalse(broken.get());
    assertEquals(0, service.status().brokenTotal());
    assertEquals(1, service.status().skippedTotal());
  }

  @Test
  void vanillaMutationModeStillSkipsHardExortCarrierImpossibleMasks() {
    ChorusFaceMask mask = ChorusFaceMask.parse("north,up,down");
    FakeWorld world = new FakeWorld();
    Block impossible = world.put(0, 64, 0, Material.CHORUS_PLANT, mask);
    AtomicBoolean broken = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of()),
            detectorClaimingMask(mask),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(impossible, 0, ChorusUpdateService.ProcessingMode.VANILLA_MUTATION);

    assertFalse(broken.get());
    assertEquals(0, service.status().brokenTotal());
    assertEquals(1, service.status().skippedTotal());
  }

  @Test
  void vanillaMutationModeSkipsExortApiClaimedImpossibleCarrier() {
    FakeWorld world = new FakeWorld();
    Block impossible =
        world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.parse("north,up,down"));
    AtomicBoolean broken = new AtomicBoolean();
    ChorusUpdateService service =
        service(
            inactiveConfig(Set.of()),
            detectorClaimingBlock(impossible),
            block -> {
              broken.set(true);
              return true;
            });

    service.processBlock(impossible, 0, ChorusUpdateService.ProcessingMode.VANILLA_MUTATION);

    assertFalse(broken.get());
    assertEquals(0, service.status().brokenTotal());
    assertEquals(1, service.status().skippedTotal());
  }

  @Test
  void queueUpgradesPendingEntriesByProcessingModePriority() {
    FakeWorld world = new FakeWorld();
    Block plant = world.put(0, 64, 0, Material.CHORUS_PLANT, ChorusFaceMask.NONE);
    ChorusUpdateService service = service(inactiveConfig(Set.of()), detector(false), block -> true);
    QueueBudget budget = new QueueBudget(10);

    assertTrue(service.tryEnqueue(plant, 0, budget, ChorusUpdateService.ProcessingMode.NORMAL));
    assertFalse(service.tryEnqueue(plant, 0, budget, ChorusUpdateService.ProcessingMode.NORMAL));
    assertTrue(
        service.tryEnqueue(
            plant, 0, budget, ChorusUpdateService.ProcessingMode.FLOWER_PLACEMENT_REPAIR));
    assertFalse(service.tryEnqueue(plant, 0, budget, ChorusUpdateService.ProcessingMode.NORMAL));
    assertTrue(
        service.tryEnqueue(plant, 0, budget, ChorusUpdateService.ProcessingMode.VANILLA_MUTATION));
    assertFalse(
        service.tryEnqueue(
            plant, 0, budget, ChorusUpdateService.ProcessingMode.FLOWER_PLACEMENT_REPAIR));
    assertEquals(1, service.status().queued());
  }

  private static ChorusUpdateService service(
      EmbeddedChorusfixConfig config,
      ExortChorusCarrierDetector detector,
      ChorusBlockBreaker breaker) {
    return new ChorusUpdateService(
        plugin(), config, detector, EmbeddedChorusfixRuntimeState.known(true), breaker);
  }

  private static EmbeddedChorusfixConfig activeConfig(Set<ChorusFaceMask> ignoredMasks) {
    return config(true, ignoredMasks);
  }

  private static EmbeddedChorusfixConfig inactiveConfig(Set<ChorusFaceMask> ignoredMasks) {
    return config(false, ignoredMasks);
  }

  private static EmbeddedChorusfixConfig config(boolean enabled, Set<ChorusFaceMask> ignoredMasks) {
    return new EmbeddedChorusfixConfig(enabled, false, 64, 64, 64, ignoredMasks, false);
  }

  private static ChorusFaceMask maskOf(Block block) {
    return ChorusFaceMask.fromBlockData(block.getBlockData()).orElseThrow();
  }

  private static ExortChorusCarrierDetector detectorClaimingMask(ChorusFaceMask claimedMask) {
    return new ExortChorusCarrierDetector() {
      @Override
      public boolean isCustom(Block block, ChorusFaceMask mask) {
        return claimedMask.equals(mask);
      }
    };
  }

  private static ExortChorusCarrierDetector detectorClaimingBlock(Block claimedBlock) {
    return new ExortChorusCarrierDetector() {
      @Override
      public boolean isCustom(Block block, ChorusFaceMask mask) {
        return block == claimedBlock;
      }
    };
  }

  private static ExortChorusCarrierDetector detectorRecordingCall(
      AtomicBoolean called, boolean result) {
    return new ExortChorusCarrierDetector() {
      @Override
      public boolean isCustom(Block block, ChorusFaceMask mask) {
        called.set(true);
        return result;
      }
    };
  }

  private static ExortChorusCarrierDetector detector(boolean result) {
    return new ExortChorusCarrierDetector() {
      @Override
      public boolean isCustom(Block block, ChorusFaceMask mask) {
        return result;
      }
    };
  }

  private static Plugin plugin() {
    return proxy(
        Plugin.class,
        (proxy, method, args) -> {
          if (method.getName().equals("getLogger")) {
            return Logger.getLogger("chorusfix-test");
          }
          return defaultValue(method);
        });
  }

  private static final class FakeWorld {
    private final UUID uid = UUID.randomUUID();
    private final Map<String, Block> blocks = new HashMap<>();
    private final World world = proxy(World.class, this::handleWorld);

    Block put(int x, int y, int z, Material material, ChorusFaceMask mask) {
      Block block = block(x, y, z, material, mask);
      blocks.put(key(x, y, z), block);
      return block;
    }

    private Object handleWorld(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "getUID" -> uid;
        case "getMinHeight" -> -64;
        case "getMaxHeight" -> 320;
        case "isChunkLoaded" -> true;
        case "getBlockAt" -> blockAt((int) args[0], (int) args[1], (int) args[2]);
        default -> defaultValue(method);
      };
    }

    private Block blockAt(int x, int y, int z) {
      return blocks.computeIfAbsent(key(x, y, z), ignored -> block(x, y, z, Material.AIR, null));
    }

    private Block block(int x, int y, int z, Material material, ChorusFaceMask mask) {
      return proxy(Block.class, new FakeBlockHandler(x, y, z, material, mask));
    }

    private final class FakeBlockHandler implements InvocationHandler {
      private final int x;
      private final int y;
      private final int z;
      private Material material;
      private BlockData blockData;

      private FakeBlockHandler(int x, int y, int z, Material material, ChorusFaceMask mask) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material;
        this.blockData = mask == null ? null : blockData(mask);
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
          return objectMethod(proxy, method, args);
        }
        return switch (method.getName()) {
          case "getType" -> material;
          case "getBlockData" -> currentBlockData();
          case "setBlockData" -> {
            blockData = (BlockData) args[0];
            yield null;
          }
          case "getWorld" -> world;
          case "getX" -> x;
          case "getY" -> y;
          case "getZ" -> z;
          case "getRelative" -> relative(args);
          case "breakNaturally" -> {
            material = Material.AIR;
            yield true;
          }
          default -> defaultValue(method);
        };
      }

      private BlockData currentBlockData() {
        if (blockData == null && material == Material.CHORUS_PLANT) {
          blockData = blockData(ChorusFaceMask.NONE);
        }
        return blockData;
      }

      private Block relative(Object[] args) {
        if (args.length == 1 && args[0] instanceof BlockFace face) {
          return blockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ());
        }
        return blockAt(x + (int) args[0], y + (int) args[1], z + (int) args[2]);
      }
    }

    private static String key(int x, int y, int z) {
      return x + "," + y + "," + z;
    }
  }

  private static BlockData blockData(ChorusFaceMask mask) {
    return proxy(MultipleFacing.class, new MultipleFacingHandler(mask));
  }

  private static final class MultipleFacingHandler implements InvocationHandler {
    private boolean north;
    private boolean east;
    private boolean south;
    private boolean west;
    private boolean up;
    private boolean down;

    private MultipleFacingHandler(ChorusFaceMask mask) {
      north = mask.north();
      east = mask.east();
      south = mask.south();
      west = mask.west();
      up = mask.up();
      down = mask.down();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      if (method.getDeclaringClass() == Object.class) {
        return objectMethod(proxy, method, args);
      }
      return switch (method.getName()) {
        case "hasFace" -> hasFace((BlockFace) args[0]);
        case "setFace" -> {
          setFace((BlockFace) args[0], (boolean) args[1]);
          yield null;
        }
        case "getFaces" -> faces();
        case "getAllowedFaces" ->
            EnumSet.of(
                BlockFace.NORTH,
                BlockFace.EAST,
                BlockFace.SOUTH,
                BlockFace.WEST,
                BlockFace.UP,
                BlockFace.DOWN);
        case "clone" -> blockData(mask());
        case "getMaterial" -> Material.CHORUS_PLANT;
        case "getAsString" -> mask().asConfigToken();
        default -> defaultValue(method);
      };
    }

    private boolean hasFace(BlockFace face) {
      return switch (face) {
        case NORTH -> north;
        case EAST -> east;
        case SOUTH -> south;
        case WEST -> west;
        case UP -> up;
        case DOWN -> down;
        default -> false;
      };
    }

    private void setFace(BlockFace face, boolean enabled) {
      switch (face) {
        case NORTH -> north = enabled;
        case EAST -> east = enabled;
        case SOUTH -> south = enabled;
        case WEST -> west = enabled;
        case UP -> up = enabled;
        case DOWN -> down = enabled;
        default -> {}
      }
    }

    private Set<BlockFace> faces() {
      EnumSet<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
      if (north) {
        faces.add(BlockFace.NORTH);
      }
      if (east) {
        faces.add(BlockFace.EAST);
      }
      if (south) {
        faces.add(BlockFace.SOUTH);
      }
      if (west) {
        faces.add(BlockFace.WEST);
      }
      if (up) {
        faces.add(BlockFace.UP);
      }
      if (down) {
        faces.add(BlockFace.DOWN);
      }
      return faces;
    }

    private ChorusFaceMask mask() {
      return new ChorusFaceMask(north, east, south, west, up, down);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object objectMethod(Object proxy, Method method, Object[] args) {
    return switch (method.getName()) {
      case "toString" -> "proxy";
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == args[0];
      default -> null;
    };
  }

  private static Object defaultValue(Method method) {
    Class<?> returnType = method.getReturnType();
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == double.class) {
      return 0.0D;
    }
    if (returnType == float.class) {
      return 0.0F;
    }
    if (returnType == void.class) {
      return null;
    }
    if (returnType == String.class) {
      return "";
    }
    return null;
  }
}
