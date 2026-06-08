package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.carrier.Carriers;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ClientBreakSpeedSuppressorTest {
  @Test
  void suppressesOnlyChorusWireBreaking() {
    assertTrue(ClientBreakSpeedSuppressor.shouldSuppress(Carriers.CHORUS_MATERIAL, BreakType.WIRE));
    assertFalse(
        ClientBreakSpeedSuppressor.shouldSuppress(Carriers.CARRIER_BARRIER, BreakType.WIRE));
    assertFalse(
        ClientBreakSpeedSuppressor.shouldSuppress(Carriers.CHORUS_MATERIAL, BreakType.STORAGE));
    assertFalse(ClientBreakSpeedSuppressor.shouldSuppress(Material.STONE, BreakType.WIRE));
  }

  @Test
  void applyAddsTransientZeroingModifierAndClearRemovesIt() {
    FakeAttributeInstance attribute = new FakeAttributeInstance();
    Player player = player(UUID.randomUUID());
    ClientBreakSpeedSuppressor suppressor =
        new ClientBreakSpeedSuppressor(plugin(), ignored -> attribute.proxy());

    suppressor.apply(player);

    AttributeModifier modifier = attribute.onlyModifier();
    assertNotNull(modifier);
    assertEquals(-1.0, modifier.getAmount(), 0.0);
    assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1, modifier.getOperation());
    assertTrue(suppressor.isSuppressed(player.getUniqueId()));

    suppressor.clear(player);

    assertNull(attribute.onlyModifier());
    assertFalse(suppressor.isSuppressed(player.getUniqueId()));
  }

  @Test
  void applyIsIdempotentForAlreadySuppressedPlayer() {
    FakeAttributeInstance attribute = new FakeAttributeInstance();
    Player player = player(UUID.randomUUID());
    ClientBreakSpeedSuppressor suppressor =
        new ClientBreakSpeedSuppressor(plugin(), ignored -> attribute.proxy());

    suppressor.apply(player);
    suppressor.apply(player);

    assertEquals(1, attribute.modifierCount());
  }

  private static Plugin plugin() {
    return proxy(
        Plugin.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getName" -> "Exort";
              case "namespace" -> "exort";
              case "toString" -> "plugin(Exort)";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static Player player(UUID playerId) {
    return proxy(
        Player.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getUniqueId" -> playerId;
              case "toString" -> "player(" + playerId + ")";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static Object defaultValue(Class<?> returnType) {
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

  private static ClassLoader classLoader() {
    return ClientBreakSpeedSuppressorTest.class.getClassLoader();
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(classLoader(), new Class<?>[] {type}, handler);
  }

  private static final class FakeAttributeInstance {
    private final Map<Key, AttributeModifier> modifiers = new HashMap<>();

    private AttributeInstance proxy() {
      return ClientBreakSpeedSuppressorTest.proxy(AttributeInstance.class, this::invoke);
    }

    private Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
      return switch (method.getName()) {
        case "getModifier" -> modifiers.get((Key) args[0]);
        case "addTransientModifier", "addModifier" -> {
          AttributeModifier modifier = (AttributeModifier) args[0];
          modifiers.put(modifier.getKey(), modifier);
          yield null;
        }
        case "removeModifier" -> {
          if (args[0] instanceof Key key) {
            modifiers.remove(key);
          } else if (args[0] instanceof AttributeModifier modifier) {
            modifiers.remove(modifier.getKey());
          }
          yield null;
        }
        case "getModifiers" -> modifiers.values();
        case "getBaseValue", "getValue", "getDefaultValue" -> 1.0;
        case "toString" -> "attribute(" + modifiers + ")";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> args != null && args.length == 1 && proxy == args[0];
        default -> defaultValue(method.getReturnType());
      };
    }

    private AttributeModifier onlyModifier() {
      return modifiers.values().stream().findFirst().orElse(null);
    }

    private int modifierCount() {
      return modifiers.size();
    }
  }
}
