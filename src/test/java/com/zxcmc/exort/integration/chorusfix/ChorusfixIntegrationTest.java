package com.zxcmc.exort.integration.chorusfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.papermc.paper.plugin.configuration.PluginMeta;
import java.lang.reflect.Proxy;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ChorusfixIntegrationTest {
  @Test
  void enabledLogMessageNamesChorusfixVersion() {
    Plugin plugin = plugin("0.2.0");

    assertEquals(
        "[Chorusfix] Integration enabled: Chorusfix 0.2.0.",
        ChorusfixIntegration.enabledMessage(plugin));
  }

  private static Plugin plugin(String version) {
    PluginMeta meta =
        (PluginMeta)
            Proxy.newProxyInstance(
                PluginMeta.class.getClassLoader(),
                new Class<?>[] {PluginMeta.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getName" -> ChorusfixIntegration.PLUGIN_NAME;
                      case "getVersion" -> version;
                      case "namespace" -> "chorusfix";
                      default -> defaultValue(method.getReturnType());
                    });
    return (Plugin)
        Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getPluginMeta" -> meta;
                  case "getName" -> ChorusfixIntegration.PLUGIN_NAME;
                  case "isEnabled" -> true;
                  case "namespace" -> "chorusfix";
                  default -> defaultValue(method.getReturnType());
                });
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    return null;
  }
}
