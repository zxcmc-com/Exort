package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class PacketDisplayLocalizerTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void returnsOriginalListWhenEntityHasNoLocalizedName() {
    List<Value> values = List.of(new Value(2, Optional.empty()));

    List<Value> localized =
        PacketDisplayLocalizer.localizeValues(
            null, 7, values, new ValueAdapter(), (player, entityId) -> null);

    assertSame(values, localized);
  }

  @Test
  void rewritesCustomNameCopyOnWrite() {
    Value customName = new Value(2, Optional.empty());
    Value other = new Value(9, "unchanged");
    List<Value> values = List.of(customName, other);

    List<Value> localized =
        PacketDisplayLocalizer.localizeValues(
            null, 42, values, new ValueAdapter(), (player, entityId) -> "Локальное имя");

    assertNotSame(values, localized);
    assertEquals(Optional.empty(), values.get(0).value());
    assertSame(other, localized.get(1));
    Optional<?> rewritten = (Optional<?>) localized.get(0).value();
    assertEquals("Локальное имя", PLAIN.serialize((Component) rewritten.orElseThrow()));
  }

  @Test
  void rewritesItemStackDisplayNameWithoutMutatingOriginal() {
    TestItemStack item = new TestItemStack("Original");
    List<Value> values = List.of(new Value(23, item));

    List<Value> localized =
        PacketDisplayLocalizer.localizeValues(
            null, 42, values, new ValueAdapter(), (player, entityId) -> "Draht");

    assertNotSame(values, localized);
    assertSame(item, values.get(0).value());
    ItemStack localizedStack = (ItemStack) localized.get(0).value();
    assertNotSame(item, localizedStack);
    assertEquals("Original", plain(item));
    assertEquals("Draht", plain(localizedStack));
  }

  @Test
  void delegatesItemStackPacketValueConversionToAdapter() {
    TestItemStack item = new TestItemStack("Original");
    List<Value> values = List.of(new Value(23, item));

    List<Value> localized =
        PacketDisplayLocalizer.localizeValues(
            null, 42, values, new ConvertingValueAdapter(), (player, entityId) -> "Draht");

    assertNotSame(values, localized);
    assertSame(item, values.get(0).value());
    assertEquals("Original", plain(item));
    RawStack rawStack = (RawStack) localized.get(0).value();
    assertEquals("Draht", rawStack.displayName());
  }

  @Test
  void skipsItemStackRewriteWhenAdapterCannotConvertPacketValue() {
    TestItemStack item = new TestItemStack("Original");
    List<Value> values = List.of(new Value(23, item));

    List<Value> localized =
        PacketDisplayLocalizer.localizeValues(
            null, 42, values, new UnavailableItemValueAdapter(), (player, entityId) -> "Draht");

    assertSame(values, localized);
    assertEquals("Original", plain(item));
  }

  private static String plain(ItemStack item) {
    Component displayName = item.getItemMeta().displayName();
    return displayName == null ? "" : PLAIN.serialize(displayName);
  }

  private record Value(int index, Object value) {}

  private record RawStack(String displayName) {}

  private static class ValueAdapter implements PacketDisplayLocalizer.MetadataValueAdapter<Value> {
    @Override
    public int index(Value value) {
      return value.index();
    }

    @Override
    public Object value(Value value) {
      return value.value();
    }

    @Override
    public Value withValue(Value value, Object replacement) {
      return new Value(value.index(), replacement);
    }

    @Override
    public Object customNameValue(Object previousValue, String localizedName) {
      return Optional.of(Component.text(localizedName));
    }
  }

  private static final class ConvertingValueAdapter extends ValueAdapter {
    @Override
    public Object itemStackValue(Object previousValue, ItemStack localizedStack) {
      return new RawStack(plain(localizedStack));
    }
  }

  private static final class UnavailableItemValueAdapter extends ValueAdapter {
    @Override
    public Object itemStackValue(Object previousValue, ItemStack localizedStack) {
      return null;
    }
  }

  private static final class TestItemStack extends ItemStack {
    private MetaState meta;

    private TestItemStack(String displayName) {
      this.meta = new MetaState(Component.text(displayName));
    }

    @Override
    public Material getType() {
      return Material.PAPER;
    }

    @Override
    public boolean hasItemMeta() {
      return meta != null;
    }

    @Override
    public ItemMeta getItemMeta() {
      return meta == null ? null : meta.copy().proxy();
    }

    @Override
    public boolean setItemMeta(ItemMeta itemMeta) {
      meta = MetaState.from(itemMeta).copy();
      return true;
    }

    @Override
    public ItemStack clone() {
      TestItemStack clone = new TestItemStack("");
      clone.meta = meta == null ? null : meta.copy();
      return clone;
    }
  }

  private static final class MetaState implements InvocationHandler {
    private Component displayName;

    private MetaState(Component displayName) {
      this.displayName = displayName;
    }

    private ItemMeta proxy() {
      return (ItemMeta)
          Proxy.newProxyInstance(
              PacketDisplayLocalizerTest.class.getClassLoader(),
              new Class<?>[] {ItemMeta.class},
              this);
    }

    private MetaState copy() {
      return new MetaState(displayName);
    }

    private static MetaState from(ItemMeta meta) {
      return (MetaState) Proxy.getInvocationHandler(meta);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "displayName" -> {
          if (args == null || args.length == 0) {
            yield displayName;
          }
          displayName = (Component) args[0];
          yield null;
        }
        case "hasDisplayName" -> displayName != null;
        case "clone" -> copy().proxy();
        case "serialize" -> Map.of();
        default -> defaultValue(method.getReturnType());
      };
    }
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0.0d;
    }
    if (type == float.class) {
      return 0.0f;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == char.class) {
      return (char) 0;
    }
    return null;
  }
}
