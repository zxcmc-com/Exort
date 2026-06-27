package com.zxcmc.exort.display.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zxcmc.exort.display.core.DisplayEntityIndex;
import com.zxcmc.exort.display.core.DisplayTags;
import com.zxcmc.exort.i18n.Lang;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ExortDisplayLocalizationServiceTest {
  @Test
  void localizesIndexedDisplayByPlayerLocale() {
    DisplayEntityIndex index = new DisplayEntityIndex();
    index.register(display(10, Set.of(DisplayTags.DISPLAY_TAG)), "item.storage_core");
    Lang lang = new Lang(null);
    lang.load("en_us");

    ExortDisplayLocalizationService service = new ExortDisplayLocalizationService(index, lang);

    assertEquals("Speicherkern", service.localize(player("de_de"), 10));
  }

  @Test
  void ignoresDisplaysWithoutLocalizationKey() {
    DisplayEntityIndex index = new DisplayEntityIndex();
    index.register(display(11, Set.of(DisplayTags.DISPLAY_TAG)), null);
    Lang lang = new Lang(null);
    lang.load("en_us");

    ExortDisplayLocalizationService service = new ExortDisplayLocalizationService(index, lang);

    assertNull(service.localize(player("ru_ru"), 11));
    assertNull(service.localize(player("ru_ru"), 12));
  }

  private static Display display(int entityId, Set<String> tags) {
    UUID entityUuid = UUID.randomUUID();
    Location location = new Location(world(), 10.0, 64.0, 10.0);
    InvocationHandler handler =
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "isValid" -> true;
            case "getScoreboardTags" -> tags;
            case "getUniqueId" -> entityUuid;
            case "getEntityId" -> entityId;
            case "getLocation" -> location;
            default -> defaultValue(method.getReturnType());
          };
        };
    return (Display)
        Proxy.newProxyInstance(
            ExortDisplayLocalizationServiceTest.class.getClassLoader(),
            new Class<?>[] {Display.class},
            handler);
  }

  private static World world() {
    UUID worldId = UUID.randomUUID();
    InvocationHandler handler =
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "getUID" -> worldId;
            case "getName" -> "world";
            default -> defaultValue(method.getReturnType());
          };
        };
    return (World)
        Proxy.newProxyInstance(
            ExortDisplayLocalizationServiceTest.class.getClassLoader(),
            new Class<?>[] {World.class},
            handler);
  }

  private static Player player(String locale) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "locale" -> Locale.forLanguageTag(locale.replace('_', '-'));
            default -> defaultValue(method.getReturnType());
          };
        };
    return (Player)
        Proxy.newProxyInstance(
            ExortDisplayLocalizationServiceTest.class.getClassLoader(),
            new Class<?>[] {Player.class},
            handler);
  }

  private static Object defaultValue(Class<?> type) {
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
    if (type == void.class) {
      return null;
    }
    return null;
  }
}
