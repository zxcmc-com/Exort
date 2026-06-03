package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.i18n.Lang;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class LoadTestLocalizationTest {
  @Test
  void playerOwnerUsesPlayerLocaleButConsoleUsesConfiguredLanguage() throws Exception {
    Lang lang = new Lang(null);
    lang.load("en_us");
    LoadTestService service = new LoadTestService(null, null, null, lang);

    Method languageFor =
        LoadTestService.class.getDeclaredMethod("languageFor", CommandSender.class);
    languageFor.setAccessible(true);

    String ownerLanguage = (String) languageFor.invoke(service, player("ru_ru"));
    String consoleLanguage = (String) languageFor.invoke(service, console());

    assertEquals("ru_ru", ownerLanguage);
    assertEquals("en_us", consoleLanguage);
    assertEquals(
        "Тест нагрузки запущен для 25 симулируемых игроков.",
        lang.trLanguage(ownerLanguage, "message.debug_load_started", 25));
    assertEquals(
        "Load test started for 25 simulated players.",
        lang.trConfigured("message.debug_load_started", 25));
  }

  private static Player player(String locale) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          if ("locale".equals(method.getName())) {
            return Locale.forLanguageTag(locale.replace('_', '-'));
          }
          return defaultValue(method.getReturnType());
        };
    return (Player)
        Proxy.newProxyInstance(
            LoadTestLocalizationTest.class.getClassLoader(),
            new Class<?>[] {Player.class},
            handler);
  }

  private static CommandSender console() {
    InvocationHandler handler = (proxy, method, args) -> defaultValue(method.getReturnType());
    return (CommandSender)
        Proxy.newProxyInstance(
            LoadTestLocalizationTest.class.getClassLoader(),
            new Class<?>[] {CommandSender.class},
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
