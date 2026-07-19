package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.BlockFace;

final class WorldEditExtentCapabilities {
  private static final Map<Class<?>, TranslateAccessor> TRANSLATE_ACCESSORS =
      new ConcurrentHashMap<>();
  private static final Set<Class<?>> TRANSLATE_SKIP = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> POSITION_METHODS = new ConcurrentHashMap<>();
  private static final Set<Class<?>> POSITION_SKIP = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, TransformAccessor> TRANSFORM_ACCESSORS =
      new ConcurrentHashMap<>();
  private static final Set<Class<?>> TRANSFORM_SKIP = ConcurrentHashMap.newKeySet();
  private static final BlockFace[] ROTATABLE_FACES = {
    BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
  };

  static BlockVector3 tryPositionTransform(Extent extent, BlockVector3 position) {
    Method method = positionMethod(extent);
    if (method == null) return position;
    try {
      Object result = method.invoke(extent, position);
      return result instanceof BlockVector3 vec ? vec : position;
    } catch (Exception ignored) {
      return position;
    }
  }

  private static Method positionMethod(Object extent) {
    Class<?> type = extent.getClass();
    Method cached = POSITION_METHODS.get(type);
    if (cached != null) return cached;
    if (POSITION_SKIP.contains(type)) return null;
    try {
      Method method;
      try {
        method = type.getDeclaredMethod("getPos", BlockVector3.class);
      } catch (NoSuchMethodException ignored) {
        method = type.getMethod("getPos", BlockVector3.class);
      }
      method.setAccessible(true);
      POSITION_METHODS.put(type, method);
      return method;
    } catch (Exception ignored) {
      POSITION_SKIP.add(type);
      return null;
    }
  }

  static TranslateAccessor translateAccessor(Object extent) {
    Class<?> type = extent.getClass();
    TranslateAccessor cached = TRANSLATE_ACCESSORS.get(type);
    if (cached != null) return cached;
    if (TRANSLATE_SKIP.contains(type)) return null;
    try {
      Field dx = type.getDeclaredField("dx");
      Field dy = type.getDeclaredField("dy");
      Field dz = type.getDeclaredField("dz");
      dx.setAccessible(true);
      dy.setAccessible(true);
      dz.setAccessible(true);
      TranslateAccessor accessor = new TranslateAccessor(dx, dy, dz);
      TRANSLATE_ACCESSORS.put(type, accessor);
      return accessor;
    } catch (Exception ignored) {
      TRANSLATE_SKIP.add(type);
      return null;
    }
  }

  record TranslateAccessor(Field dx, Field dy, Field dz) {
    int dx(Object instance) {
      return read(dx, instance);
    }

    int dy(Object instance) {
      return read(dy, instance);
    }

    int dz(Object instance) {
      return read(dz, instance);
    }

    private static int read(Field field, Object instance) {
      try {
        return ((Number) field.get(instance)).intValue();
      } catch (Exception ignored) {
        return 0;
      }
    }
  }

  private record TransformAccessor(Method method, Field field) {
    Transform get(Object instance) {
      if (method != null) {
        try {
          Object value = method.invoke(instance);
          if (value instanceof Transform transform) {
            return transform;
          }
        } catch (Exception ignored) {
          // ignored
        }
      }
      if (field != null) {
        try {
          Object value = field.get(instance);
          if (value instanceof Transform transform) {
            return transform;
          }
        } catch (Exception ignored) {
          // ignored
        }
      }
      return null;
    }
  }

  private static final Map<Class<?>, Method> SETBLOCKS_STATE = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Method> SETBLOCKS_PATTERN = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Method> SETBLOCKS_SET = new ConcurrentHashMap<>();
  private static final Set<Class<?>> SETBLOCKS_STATE_SKIP = ConcurrentHashMap.newKeySet();
  private static final Set<Class<?>> SETBLOCKS_PATTERN_SKIP = ConcurrentHashMap.newKeySet();
  private static final Set<Class<?>> SETBLOCKS_SET_SKIP = ConcurrentHashMap.newKeySet();

  static Method findSetBlocks(Class<?> extentClass, Class<?> paramType) {
    Map<Class<?>, Method> cache = paramType == Pattern.class ? SETBLOCKS_PATTERN : SETBLOCKS_STATE;
    Set<Class<?>> skip = paramType == Pattern.class ? SETBLOCKS_PATTERN_SKIP : SETBLOCKS_STATE_SKIP;
    Method cached = cache.get(extentClass);
    if (cached != null) return cached;
    if (skip.contains(extentClass)) return null;
    try {
      Method method = extentClass.getMethod("setBlocks", Region.class, paramType);
      cache.put(extentClass, method);
      return method;
    } catch (Exception ignored) {
      skip.add(extentClass);
      return null;
    }
  }

  static Method findSetBlocksSetMethod(Class<?> extentClass) {
    Method cached = SETBLOCKS_SET.get(extentClass);
    if (cached != null) return cached;
    if (SETBLOCKS_SET_SKIP.contains(extentClass)) return null;
    try {
      Method method =
          extentClass.getMethod(
              "setBlocks", Set.class, com.sk89q.worldedit.function.pattern.Pattern.class);
      SETBLOCKS_SET.put(extentClass, method);
      return method;
    } catch (Exception ignored) {
      SETBLOCKS_SET_SKIP.add(extentClass);
      return null;
    }
  }

  static Transform resolveTransform(Extent extent) {
    Transform combined = null;
    Extent current = extent;
    while (current != null) {
      Transform transform = null;
      if (current instanceof BlockTransformExtent blockTransformExtent) {
        transform = blockTransformExtent.getTransform();
      } else {
        TransformAccessor accessor = transformAccessor(current);
        if (accessor != null) {
          transform = accessor.get(current);
        }
      }
      if (transform != null && !transform.isIdentity()) {
        combined = combined == null ? transform : combined.combine(transform);
      }
      if (current instanceof AbstractDelegateExtent delegateExtent) {
        current = delegateExtent.getExtent();
      } else {
        break;
      }
    }
    return combined;
  }

  static FacingTransform resolveClipboardFacing(Actor actor) {
    if (actor == null) return null;
    try {
      LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
      if (session == null) return null;
      ClipboardHolder holder;
      try {
        holder = session.getClipboard();
      } catch (com.sk89q.worldedit.EmptyClipboardException ignored) {
        return null;
      }
      if (holder == null) return null;
      Transform transform = holder.getTransform();
      if (transform == null || transform.isIdentity()) return null;
      return face -> WorldEditBridge.rotateFacing(face, transform);
    } catch (Exception ignored) {
      return null;
    }
  }

  static FacingTransform resolveFacingTransform(Extent extent, Transform transform) {
    if (transform != null && !transform.isIdentity()) {
      return face -> WorldEditBridge.rotateFacing(face, transform);
    }
    return resolveFacingTransformFromPositions(extent);
  }

  private static FacingTransform resolveFacingTransformFromPositions(Extent extent) {
    if (extent == null) return null;
    BlockVector3 origin = BlockVector3.at(0, 0, 0);
    BlockVector3 originPos = resolvePositionForFacing(extent, origin);
    EnumMap<BlockFace, BlockFace> mapping = new EnumMap<>(BlockFace.class);
    boolean changed = false;
    for (BlockFace face : ROTATABLE_FACES) {
      BlockVector3 offset = offsetForFace(face);
      BlockVector3 transformed = resolvePositionForFacing(extent, origin.add(offset));
      BlockVector3 delta = transformed.subtract(originPos);
      BlockFace mapped = faceFromDelta(delta);
      if (mapped == null) {
        return null;
      }
      mapping.put(face, mapped);
      if (mapped != face) {
        changed = true;
      }
    }
    if (!changed) return null;
    return face -> mapping.getOrDefault(face, face);
  }

  private static BlockVector3 resolvePositionForFacing(Extent extent, BlockVector3 position) {
    BlockVector3 resolved = position;
    Extent current = extent;
    while (current != null) {
      BlockVector3 transformed = tryPositionTransform(current, resolved);
      if (transformed != null) {
        resolved = transformed;
      }
      TranslateAccessor accessor = translateAccessor(current);
      if (accessor != null) {
        int dx = accessor.dx(current);
        int dy = accessor.dy(current);
        int dz = accessor.dz(current);
        if (dx != 0 || dy != 0 || dz != 0) {
          resolved = resolved.add(dx, dy, dz);
        }
      }
      if (current instanceof AbstractDelegateExtent delegateExtent) {
        current = delegateExtent.getExtent();
      } else {
        break;
      }
    }
    return resolved;
  }

  private static BlockVector3 offsetForFace(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockVector3.at(0, 0, -1);
      case SOUTH -> BlockVector3.at(0, 0, 1);
      case EAST -> BlockVector3.at(1, 0, 0);
      case WEST -> BlockVector3.at(-1, 0, 0);
      case UP -> BlockVector3.at(0, 1, 0);
      case DOWN -> BlockVector3.at(0, -1, 0);
      default -> BlockVector3.at(0, 0, 0);
    };
  }

  private static BlockFace faceFromDelta(BlockVector3 delta) {
    int x = delta.x();
    int y = delta.y();
    int z = delta.z();
    int ax = Math.abs(x);
    int ay = Math.abs(y);
    int az = Math.abs(z);
    if (ax == 0 && ay == 0 && az == 0) {
      return null;
    }
    if (ax >= ay && ax >= az) {
      return x >= 0 ? BlockFace.EAST : BlockFace.WEST;
    }
    if (ay >= ax && ay >= az) {
      return y >= 0 ? BlockFace.UP : BlockFace.DOWN;
    }
    return z >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
  }

  private static TransformAccessor transformAccessor(Object extent) {
    Class<?> type = extent.getClass();
    TransformAccessor cached = TRANSFORM_ACCESSORS.get(type);
    if (cached != null) return cached;
    if (TRANSFORM_SKIP.contains(type)) return null;
    try {
      Method method;
      try {
        method = type.getDeclaredMethod("getTransform");
      } catch (NoSuchMethodException ignored) {
        method = type.getMethod("getTransform");
      }
      if (Transform.class.isAssignableFrom(method.getReturnType())) {
        method.setAccessible(true);
        TransformAccessor accessor = new TransformAccessor(method, null);
        TRANSFORM_ACCESSORS.put(type, accessor);
        return accessor;
      }
    } catch (Exception ignored) {
      // fallback to field lookup
    }
    try {
      Field field = type.getDeclaredField("transform");
      if (Transform.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        TransformAccessor accessor = new TransformAccessor(null, field);
        TRANSFORM_ACCESSORS.put(type, accessor);
        return accessor;
      }
    } catch (Exception ignored) {
      // ignored
    }
    TRANSFORM_SKIP.add(type);
    return null;
  }
}
