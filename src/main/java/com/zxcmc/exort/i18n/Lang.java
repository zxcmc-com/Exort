package com.zxcmc.exort.i18n;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Lang {
  private final JavaPlugin plugin;
  private final File dataFolder;
  private final Path resourceRoot;
  private final Map<String, Map<String, String>> bundledDefaults = new ConcurrentHashMap<>();

  private volatile Set<String> bundledLanguages =
      Set.of(LocalizationFiles.DEFAULT_LANGUAGE, LocalizationFiles.RUSSIAN_LANGUAGE);
  private volatile Map<String, Map<String, String>> languages = Map.of();
  private volatile Map<String, String> defaultsEn = Map.of();
  private volatile Map<String, String> active = Map.of();
  private volatile String activeLanguage = LocalizationFiles.DEFAULT_LANGUAGE;

  public Lang(JavaPlugin plugin) {
    this(plugin, plugin == null ? null : plugin.getDataFolder(), Path.of("src/main/resources"));
  }

  Lang(JavaPlugin plugin, File dataFolder, Path resourceRoot) {
    this.plugin = plugin;
    this.dataFolder = dataFolder;
    this.resourceRoot = resourceRoot;
    loadBundledDefaults();
  }

  private void loadBundledDefaults() {
    Map<String, String> english = loadBundledDefaultData(LocalizationFiles.DEFAULT_LANGUAGE);
    if (english.isEmpty()) {
      english = emergencyEnglishFallback();
    }
    defaultsEn = immutable(english);
    bundledDefaults.put(LocalizationFiles.DEFAULT_LANGUAGE, defaultsEn);

    Map<String, String> russian = loadBundledDefaultData(LocalizationFiles.RUSSIAN_LANGUAGE);
    Map<String, String> initialRussian = merged(defaultsEn, russian);
    bundledDefaults.put(LocalizationFiles.RUSSIAN_LANGUAGE, initialRussian);

    Map<String, Map<String, String>> initial = new LinkedHashMap<>();
    initial.put(LocalizationFiles.DEFAULT_LANGUAGE, defaultsEn);
    initial.put(LocalizationFiles.RUSSIAN_LANGUAGE, initialRussian);
    languages = immutableNested(initial);
    active = defaultsEn;
    activeLanguage = LocalizationFiles.DEFAULT_LANGUAGE;
  }

  public void load(String language) {
    File langDir = langDir();
    if (langDir != null && !langDir.exists()) {
      langDir.mkdirs();
    }

    Set<String> bundled = bundledLanguageCodes();
    Set<String> local = localLanguageFiles(langDir);

    Set<String> requestedLanguages = new TreeSet<>(bundled);
    for (String code : local) {
      requestedLanguages.add(code);
    }

    String requested = normalizeLanguage(language);
    Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
    for (String code : requestedLanguages) {
      loaded.put(code, loadLanguage(langDir, code, local.contains(code)));
    }

    Map<String, String> selected = loaded.get(requested);
    if (selected == null || selected.isEmpty()) {
      requested = LocalizationFiles.DEFAULT_LANGUAGE;
      selected = loaded.getOrDefault(LocalizationFiles.DEFAULT_LANGUAGE, defaultsEn);
    }
    languages = immutableNested(loaded);
    activeLanguage = requested;
    active = selected;
  }

  private Map<String, String> loadLanguage(File langDir, String code, boolean allowLocalOverride) {
    Map<String, String> loaded = new LinkedHashMap<>(defaultsEn);
    loaded.putAll(bundledDefaultData(code));
    if (allowLocalOverride && langDir != null) {
      loaded.putAll(loadLocalLanguage(langDir, code));
    }
    return immutable(loaded);
  }

  private Map<String, String> loadLocalLanguage(File langDir, String code) {
    File target = new File(langDir, code + LocalizationFiles.LANG_EXT);
    if (!target.isFile()) {
      return Map.of();
    }
    try {
      return LocalizationFiles.readFlatLanguage(target.toPath());
    } catch (IOException e) {
      if (plugin != null) {
        plugin
            .getLogger()
            .warning("Failed to read language file " + target.getName() + ": " + e.getMessage());
      }
      return Map.of();
    }
  }

  private Set<String> localLanguageFiles(File langDir) {
    Set<String> result = new TreeSet<>();
    if (langDir == null) {
      return result;
    }
    File[] files = langDir.listFiles((dir, name) -> name.endsWith(LocalizationFiles.LANG_EXT));
    if (files == null) {
      return result;
    }
    for (File file : files) {
      String name = file.getName();
      result.add(
          normalizeLanguage(
              name.substring(0, name.length() - LocalizationFiles.LANG_EXT.length())));
    }
    return result;
  }

  private Set<String> bundledLanguageCodes() {
    Set<String> result = new TreeSet<>();
    try (InputStream in = openBundledResource(LocalizationFiles.LANG_INDEX)) {
      if (in != null) {
        result.addAll(LocalizationFiles.readLanguageIndex(in));
      }
    } catch (IOException e) {
      if (plugin != null) {
        plugin.getLogger().warning("Failed to read bundled language index: " + e.getMessage());
      }
    }
    result.add(LocalizationFiles.DEFAULT_LANGUAGE);
    result.add(LocalizationFiles.RUSSIAN_LANGUAGE);
    bundledLanguages = Set.copyOf(result);
    return bundledLanguages;
  }

  private Map<String, String> bundledDefaultData(String code) {
    String normalized = normalizeLanguage(code);
    if (LocalizationFiles.DEFAULT_LANGUAGE.equals(normalized)) {
      return defaultsEn;
    }
    return bundledDefaults.computeIfAbsent(normalized, this::loadBundledDefaultData);
  }

  private Map<String, String> loadBundledDefaultData(String code) {
    try (InputStream in =
        openBundledResource(LocalizationFiles.LANG_DIR + code + LocalizationFiles.LANG_EXT)) {
      if (in == null) {
        return Map.of();
      }
      return immutable(LocalizationFiles.readFlatLanguage(in));
    } catch (IOException e) {
      if (plugin != null) {
        plugin
            .getLogger()
            .warning(
                "Failed to read bundled language file "
                    + code
                    + LocalizationFiles.LANG_EXT
                    + ": "
                    + e.getMessage());
      }
      return Map.of();
    }
  }

  private InputStream openBundledResource(String name) throws IOException {
    if (plugin != null) {
      return plugin.getResource(name);
    }
    if (resourceRoot != null) {
      Path path = resourceRoot.resolve(name);
      if (Files.isRegularFile(path)) {
        return Files.newInputStream(path);
      }
    }
    return Lang.class.getClassLoader().getResourceAsStream(name);
  }

  private File langDir() {
    return dataFolder == null ? null : new File(dataFolder, "lang");
  }

  public String tr(String key, Object... params) {
    return trLanguage(activeLanguage, key, params);
  }

  public String trConfigured(String key, Object... params) {
    return trLanguage(configuredLanguage(), key, params);
  }

  public String tr(CommandSender sender, String key, Object... params) {
    if (sender instanceof Player player) {
      return tr(player, key, params);
    }
    return tr(key, params);
  }

  public String tr(Player player, String key, Object... params) {
    return trLanguage(pluginTextLanguage(player), key, params);
  }

  public String trLanguage(String language, String key, Object... params) {
    String resolvedLanguage = pluginTextLanguage(language);
    Map<String, String> selected = languages.getOrDefault(resolvedLanguage, active);
    String base =
        selected.getOrDefault(key, active.getOrDefault(key, defaultsEn.getOrDefault(key, key)));
    if (params.length == 0) return base;
    return formatParams(base, params);
  }

  private String formatParams(String base, Object... params) {
    String result = base;
    for (int i = 0; i < params.length; i++) {
      result = result.replace("{" + i + "}", String.valueOf(params[i]));
    }
    return result;
  }

  public Component itemComponent(boolean clientTranslations, String key, Object... params) {
    Component component = clientComponent(clientTranslations, key, params);
    return component.decoration(TextDecoration.ITALIC, false);
  }

  public Component clientComponent(boolean clientTranslations, String key, Object... params) {
    String fallback = tr(key, params);
    if (!clientTranslations || !LocalizationFiles.isClientResourcePackKey(key)) {
      return Component.text(fallback);
    }
    return Component.translatable(clientKey(key), fallback, componentArgs(params));
  }

  private Component[] componentArgs(Object... params) {
    Component[] args = new Component[params.length];
    for (int i = 0; i < params.length; i++) {
      args[i] = Component.text(String.valueOf(params[i]));
    }
    return args;
  }

  public String clientKey(String key) {
    return LocalizationFiles.CLIENT_KEY_PREFIX + key;
  }

  public String configuredLanguage() {
    return activeLanguage;
  }

  public boolean hasLanguage(String language) {
    return languages.containsKey(normalizeLanguage(language));
  }

  public Set<String> availableLanguages() {
    return Set.copyOf(languages.keySet());
  }

  public String pluginTextLanguage(Player player) {
    if (player == null) {
      return activeLanguage;
    }
    return pluginTextLanguage(player.locale().toString());
  }

  public String pluginTextLanguage(String requestedLanguage) {
    String normalized = normalizeLanguage(requestedLanguage);
    if (languages.containsKey(normalized)) {
      return normalized;
    }
    if (languages.containsKey(activeLanguage)) {
      return activeLanguage;
    }
    return LocalizationFiles.DEFAULT_LANGUAGE;
  }

  public String normalizeLanguage(String input) {
    return LocalizationFiles.normalizeLanguage(input);
  }

  public void reload(String language) {
    try {
      load(language);
    } catch (Exception e) {
      if (plugin != null) {
        plugin
            .getLogger()
            .severe(
                "Failed to load language '"
                    + language
                    + "', falling back to en_us: "
                    + e.getMessage());
      }
      load(LocalizationFiles.DEFAULT_LANGUAGE);
    }
  }

  private Map<String, String> emergencyEnglishFallback() {
    Map<String, String> fallback = new LinkedHashMap<>();
    fallback.put("message.no_permission", "No permission.");
    fallback.put("message.operation_failed", "Operation failed. See console.");
    fallback.put("message.command_click", "Click to insert {0}");
    fallback.put(
        "message.relay_waiting",
        "First network relay selected. Right-click the second network relay.");
    fallback.put("message.relay_same", "A network relay cannot link to itself.");
    fallback.put("message.relay_cross_world", "Network relays cannot link across worlds.");
    fallback.put("message.relay_already_linked", "One of these network relays is already linked.");
    fallback.put(
        "message.relay_out_of_range",
        "Network relays are too far apart. Limit: {0} chunks by Manhattan distance.");
    fallback.put("message.relay_linked", "Network relays linked.");
    fallback.put("message.relay_unlinked", "Network relay link removed.");
    fallback.put("message.help_header", "Exort Storage Network commands:");
    fallback.put("message.version", "Exort Storage Network v{0} by phantomfighterxx");
    fallback.put("item.storage_core", "Storage Core");
    fallback.put("item.terminal", "Storage Terminal");
    fallback.put("item.crafting_terminal", "Crafting Terminal");
    fallback.put("item.wire", "Storage Wire");
    fallback.put("item.relay", "Network Relay");
    fallback.put("item.monitor", "Storage Monitor");
    fallback.put("item.import_bus", "Import Bus");
    fallback.put("item.export_bus", "Export Bus");
    fallback.put("item.wireless_terminal", "Wireless Terminal");
    fallback.put("relay.status", "Network relay peer {0} | {1}");
    fallback.put("relay.storage_multiple", "Storage: multiple connected");
    fallback.put("relay.storage_tail", "Storage: {0}");
    fallback.put("relay.storage_none", "Storage: none");
    fallback.put("lore.storage.capacity", "{0} / {1} ({2})");
    fallback.put("lore.storage.id_tail", "{0}");
    fallback.put("lore.wireless_terminal.battery", "Battery: {0}%");
    fallback.put("lore.wireless_terminal.owner", "Owner: {0}");
    fallback.put("lore.wireless_terminal.not_linked", "Not linked");
    fallback.put("lore.wireless_terminal.storage_tail", "{0}");
    return fallback;
  }

  private Map<String, String> merged(Map<String, String> base, Map<String, String> overlay) {
    Map<String, String> result = new LinkedHashMap<>(base);
    result.putAll(overlay);
    return immutable(result);
  }

  private Map<String, String> immutable(Map<String, String> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  private Map<String, Map<String, String>> immutableNested(
      Map<String, Map<String, String>> source) {
    Map<String, Map<String, String>> result = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
      result.put(entry.getKey(), immutable(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }
}
