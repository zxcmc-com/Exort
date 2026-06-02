package com.zxcmc.exort.integration.auth;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class KnownAuthenticationGate implements AuthenticationGate {
  private static final String AUTHME_PLUGIN = "AuthMe";
  private static final String LOGIN_SECURITY_PLUGIN = "LoginSecurity";

  private final JavaPlugin plugin;
  private final Set<String> warned = ConcurrentHashMap.newKeySet();
  private volatile Boolean authMeForceRegistration;

  public KnownAuthenticationGate(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean blocks(Player player) {
    if (player == null || !player.isOnline()) {
      return false;
    }
    return authMeBlocks(player) || loginSecurityBlocks(player);
  }

  private boolean authMeBlocks(Player player) {
    Plugin authMe = enabledPlugin(AUTHME_PLUGIN);
    if (authMe == null) {
      return false;
    }
    try {
      ClassLoader loader = authMe.getClass().getClassLoader();
      Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", false, loader);
      Object api = apiClass.getMethod("getInstance").invoke(null);
      if (api == null) {
        return false;
      }
      if (invokeBoolean(api, "isAuthenticated", Player.class, player)) {
        return false;
      }
      if (invokeBoolean(api, "isUnrestricted", Player.class, player)) {
        return false;
      }
      if (isAuthMeRegistrationForced(authMe)) {
        return true;
      }
      Method getPlayerInfo = apiClass.getMethod("getPlayerInfo", String.class);
      Object result = getPlayerInfo.invoke(api, player.getName());
      return result instanceof Optional<?> optional && optional.isPresent();
    } catch (ReflectiveOperationException | RuntimeException e) {
      warnOnce(AUTHME_PLUGIN, e);
      return false;
    }
  }

  private boolean loginSecurityBlocks(Player player) {
    Plugin loginSecurity = enabledPlugin(LOGIN_SECURITY_PLUGIN);
    if (loginSecurity == null) {
      return false;
    }
    try {
      ClassLoader loader = loginSecurity.getClass().getClassLoader();
      Class<?> pluginClass =
          Class.forName("com.lenis0012.bukkit.loginsecurity.LoginSecurity", false, loader);
      Object sessionManager = pluginClass.getMethod("getSessionManager").invoke(null);
      Object session =
          sessionManager
              .getClass()
              .getMethod("getPlayerSession", Player.class)
              .invoke(sessionManager, player);
      if (session == null) {
        return false;
      }
      return !invokeBoolean(session, "isAuthorized");
    } catch (ReflectiveOperationException | RuntimeException e) {
      warnOnce(LOGIN_SECURITY_PLUGIN, e);
      return false;
    }
  }

  private Plugin enabledPlugin(String name) {
    PluginManager pluginManager = plugin.getServer().getPluginManager();
    Plugin found = pluginManager.getPlugin(name);
    return found != null && found.isEnabled() ? found : null;
  }

  private static boolean invokeBoolean(
      Object target, String methodName, Class<?> argType, Object argument)
      throws ReflectiveOperationException {
    Object value = target.getClass().getMethod(methodName, argType).invoke(target, argument);
    return Boolean.TRUE.equals(value);
  }

  private static boolean invokeBoolean(Object target, String methodName)
      throws ReflectiveOperationException {
    Object value = target.getClass().getMethod(methodName).invoke(target);
    return Boolean.TRUE.equals(value);
  }

  private boolean isAuthMeRegistrationForced(Plugin authMe) {
    Boolean cached = authMeForceRegistration;
    if (cached != null) {
      return cached;
    }
    YamlConfiguration config =
        YamlConfiguration.loadConfiguration(
            authMe.getDataFolder().toPath().resolve("config.yml").toFile());
    boolean forced = config.getBoolean("settings.registration.force", true);
    authMeForceRegistration = forced;
    return forced;
  }

  private void warnOnce(String integration, Exception e) {
    if (warned.add(integration)) {
      plugin
          .getLogger()
          .log(
              Level.WARNING,
              "Failed to read "
                  + integration
                  + " authentication state; using event cancellation only.",
              e);
    }
  }
}
