package com.zxcmc.exort.core.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zxcmc.exort.core.ExortPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class ItemNameService {
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCE_ROOT = "https://resources.download.minecraft.net/";
    private static final String INVENTIVE_ROOT = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/";
    private static final String INDEX_FILE = "index.yml";
    private static final String ITEMS_DIR = "items";
    private static final String VERSION_KEY = "__version";
    private static final String ASSET_INDEX_URL_KEY = "assetIndexUrl";
    private static final String LANG_HASHES_KEY = "hashes";
    private static final String LANG_LIST_KEY = "languages";

    private final ExortPlugin plugin;
    private final File langDir;
    private final File itemsDir;
    private final File indexFile;

    private volatile Map<String, String> active = new HashMap<>();
    private volatile Map<String, String> fallback = new HashMap<>();
    private volatile Set<String> availableLanguages = new HashSet<>();
    private volatile Map<String, String> langHashes = new HashMap<>();
    private volatile Map<String, String> dictVersions = new HashMap<>();
    private volatile Map<String, Integer> dictSizes = new HashMap<>();
    private volatile String assetIndexUrl;
    private volatile String activeLanguage = "en_us";
    private volatile String serverVersion = "unknown";
    private volatile boolean indexFetched;

    public ItemNameService(ExortPlugin plugin) {
        this.plugin = plugin;
        this.langDir = new File(plugin.getDataFolder(), "lang");
        this.itemsDir = new File(langDir, ITEMS_DIR);
        this.indexFile = new File(langDir, INDEX_FILE);
    }

    public java.util.concurrent.CompletableFuture<Status> reload(String requestedLanguage) {
        return reloadAsync(requestedLanguage, false, false);
    }

    public java.util.concurrent.CompletableFuture<Status> refresh(String requestedLanguage) {
        return reloadAsync(requestedLanguage, true, true);
    }

    public String getActiveLanguage() {
        return activeLanguage;
    }

    public Status status() {
        return new Status(
                activeLanguage,
                serverVersion,
                indexFile.exists(),
                indexFetched,
                availableLanguages.size(),
                new TreeMap<>(dictVersions),
                new TreeMap<>(dictSizes)
        );
    }

    public boolean isKnownLanguage(String code) {
        if (code == null || code.isBlank()) return false;
        String normalized = normalizeLanguage(code);
        Set<String> local = localLanguages();
        if (local.isEmpty()) return true;
        return local.contains(normalized);
    }

    public Set<String> knownLanguages() {
        Set<String> local = localLanguages();
        if (!local.isEmpty()) {
            return local;
        }
        Set<String> fallback = new TreeSet<>();
        fallback.add(activeLanguage);
        fallback.add("en_us");
        fallback.add("ru_ru");
        return fallback;
    }

    public Set<String> localLanguages() {
        Set<String> result = new TreeSet<>();
        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().substring(0, file.getName().length() - 4);
                String normalized = normalizeLanguage(code);
                if (normalized.equals("en_us") || normalized.equals("ru_ru")
                        || availableLanguages.isEmpty() || availableLanguages.contains(normalized)) {
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    public String resolveName(ItemStack stack) {
        if (stack == null) return "";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasItemName()) {
                return PlainTextComponentSerializer.plainText().serialize(meta.itemName());
            }
            if (meta.hasDisplayName()) {
                return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            }
        }
        String key = stack.getType().getKey().getKey();
        String name = active.get(key);
        if (name != null) return name;
        name = fallback.get(key);
        if (name != null) return name;
        return key;
    }

    public String resolveDisplayName(ItemStack stack) {
        if (stack == null) return "";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            }
            if (meta.hasItemName()) {
                return PlainTextComponentSerializer.plainText().serialize(meta.itemName());
            }
        }
        String key = stack.getType().getKey().getKey();
        String name = active.get(key);
        if (name != null) return name;
        name = fallback.get(key);
        if (name != null) return name;
        return key;
    }

    public String resolveDictionaryName(ItemStack stack) {
        if (stack == null) return "";
        return resolveDictionaryName(stack.getType().getKey().getKey());
    }

    public String resolveDictionaryName(String key) {
        if (key == null) return "";
        String name = active.get(key);
        if (name != null) return name;
        name = fallback.get(key);
        if (name != null) return name;
        return key;
    }

    public String normalizeLanguage(String input) {
        if (input == null) return "en_us";
        return input.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    public String resolveLanguage(String input) {
        String normalized = normalizeLanguage(input);
        if (availableLanguages.isEmpty()) {
            return normalized;
        }
        if (normalized.equals("en_us") || normalized.equals("ru_ru")) {
            return normalized;
        }
        if (!availableLanguages.contains(normalized)) {
            plugin.getLogger().warning("Language '" + normalized + "' is not a valid Minecraft locale. Falling back to en_us.");
            return "en_us";
        }
        return normalized;
    }

    private java.util.concurrent.CompletableFuture<Status> reloadAsync(String requestedLanguage, boolean force, boolean refreshIndex) {
        java.util.concurrent.CompletableFuture<Status> future = new java.util.concurrent.CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                reloadInternal(requestedLanguage, force, refreshIndex);
                Bukkit.getScheduler().runTask(plugin, () -> future.complete(status()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to reload item dictionaries: " + e.getMessage(), e);
                Bukkit.getScheduler().runTask(plugin, () -> future.complete(status()));
            }
        });
        return future;
    }

    private void reloadInternal(String requestedLanguage, boolean force, boolean refreshIndex) {
        this.serverVersion = safeServerVersion();
        ensureDirectories();
        if (refreshIndex) {
            indexFetched = fetchLanguageIndex();
        } else {
            indexFetched = ensureLanguageIndex();
        }
        this.activeLanguage = resolveLanguage(requestedLanguage);
        List<String> updated = ensureDictionaries(force);
        Map<String, String> newActive = loadDictionary(activeLanguage);
        Map<String, String> newFallback = loadDictionary("en_us");
        if (newFallback.isEmpty()) {
            newFallback = buildFallbackMap();
        }
        active = newActive;
        fallback = newFallback;
        logReload(updated);
    }

    private void ensureDirectories() {
        if (!langDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            langDir.mkdirs();
        }
        if (!itemsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            itemsDir.mkdirs();
        }
    }

    private boolean ensureLanguageIndex() {
        if (indexFile.exists()) {
            loadIndex();
            return false;
        }
        try {
            fetchAndStoreIndex();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch Minecraft language index: " + e.getMessage(), e);
        }
        if (!indexFile.exists()) {
            loadIndex(); // will remain empty
        }
        return false;
    }

    private boolean fetchLanguageIndex() {
        try {
            fetchAndStoreIndex();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch Minecraft language index: " + e.getMessage(), e);
            if (indexFile.exists()) {
                loadIndex();
            }
        }
        return false;
    }

    private void loadIndex() {
        Set<String> newLanguages = new HashSet<>();
        Map<String, String> newHashes = new HashMap<>();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(indexFile);
        assetIndexUrl = cfg.getString(ASSET_INDEX_URL_KEY, null);
        List<String> langs = cfg.getStringList(LANG_LIST_KEY);
        newLanguages.addAll(langs);
        if (cfg.contains(LANG_HASHES_KEY)) {
            for (String key : Objects.requireNonNull(cfg.getConfigurationSection(LANG_HASHES_KEY)).getKeys(false)) {
                String hash = cfg.getString(LANG_HASHES_KEY + "." + key);
                if (hash != null && !hash.isEmpty()) {
                    newHashes.put(key, hash);
                }
            }
        }
        availableLanguages = newLanguages;
        langHashes = newHashes;
    }

    private void fetchAndStoreIndex() throws IOException {
        JsonObject manifest = readJson(VERSION_MANIFEST);
        String requestedVersion = serverVersion;
        String versionUrl = null;
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject ver = element.getAsJsonObject();
            if (requestedVersion.equals(ver.get("id").getAsString())) {
                versionUrl = ver.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            JsonObject latest = manifest.getAsJsonObject("latest");
            String latestRelease = latest.get("release").getAsString();
            for (JsonElement element : manifest.getAsJsonArray("versions")) {
                JsonObject ver = element.getAsJsonObject();
                if (latestRelease.equals(ver.get("id").getAsString())) {
                    versionUrl = ver.get("url").getAsString();
                    break;
                }
            }
        }
        if (versionUrl == null) {
            throw new IOException("Version manifest did not contain a usable release url");
        }
        JsonObject versionJson = readJson(versionUrl);
        JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");
        assetIndexUrl = assetIndex.get("url").getAsString();
        JsonObject indexJson = readJson(assetIndexUrl);
        JsonObject objects = indexJson.getAsJsonObject("objects");
        Set<String> languages = new TreeSet<>();
        Map<String, String> hashes = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("minecraft/lang/") || !key.endsWith(".json")) {
                continue;
            }
            String code = key.substring("minecraft/lang/".length(), key.length() - ".json".length());
            languages.add(code);
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            hashes.put(code, hash);
        }
        availableLanguages = new HashSet<>(languages);
        langHashes = new HashMap<>(hashes);

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set(ASSET_INDEX_URL_KEY, assetIndexUrl);
        cfg.set(LANG_LIST_KEY, new ArrayList<>(languages));
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            cfg.set(LANG_HASHES_KEY + "." + entry.getKey(), entry.getValue());
        }
        cfg.save(indexFile);
    }

    private List<String> ensureDictionaries(boolean force) {
        Set<String> requested = new HashSet<>();
        requested.add("en_us");
        requested.add("ru_ru");
        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().substring(0, file.getName().length() - 4);
                String normalized = normalizeLanguage(code);
                if (availableLanguages.isEmpty() || availableLanguages.contains(normalized)) {
                    requested.add(normalized);
                }
            }
        }
        List<String> updated = new ArrayList<>();
        dictVersions = new HashMap<>();
        dictSizes = new HashMap<>();
        for (String code : requested) {
            if (ensureDictionary(code, force)) {
                updated.add(code);
            }
        }
        return updated;
    }

    private boolean ensureDictionary(String code, boolean force) {
        File dictFile = new File(itemsDir, code + ".yml");
        String dictVersion = null;
        if (dictFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dictFile);
            dictVersion = cfg.getString(VERSION_KEY, null);
            if (dictVersion != null) {
                dictVersions.put(code, dictVersion);
            }
            dictSizes.put(code, countEntries(cfg));
            if (!force && dictVersion != null && compareVersions(dictVersion, serverVersion) >= 0) {
                return false;
            }
        }
        Map<String, String> translations = downloadDictionary(code);
        if (translations.isEmpty()) {
            if (!dictFile.exists()) {
                translations = buildFallbackMap();
                plugin.getLogger().warning("Language dictionary '" + code + "' not found in assets; using fallback names.");
            } else {
                return false;
            }
        }
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set(VERSION_KEY, serverVersion);
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            cfg.set(entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(dictFile);
            dictVersions.put(code, serverVersion);
            dictSizes.put(code, translations.size());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save item dictionary " + dictFile.getName(), e);
        }
        return true;
    }

    private Map<String, String> downloadDictionary(String code) {
        Map<String, String> primary = downloadFromMojang(code);
        if (!primary.isEmpty()) {
            return primary;
        }
        Map<String, String> fallback = downloadFromInventive(code);
        if (!fallback.isEmpty()) {
            plugin.getLogger().info("Downloaded lang file for " + code + " from InventivetalentDev assets.");
        }
        return fallback;
    }

    private Map<String, String> downloadFromMojang(String code) {
        if (!langHashes.containsKey(code)) {
            return Collections.emptyMap();
        }
        String hash = langHashes.get(code);
        String url = RESOURCE_ROOT + hash.substring(0, 2) + "/" + hash;
        try {
            JsonObject json = readJson(url);
            return parseLangJson(json);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to download lang file for " + code + ": " + e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Map<String, String> downloadFromInventive(String code) {
        String version = serverVersion == null || serverVersion.isBlank() ? "latest" : serverVersion;
        String url = INVENTIVE_ROOT + version + "/assets/minecraft/lang/" + code + ".json";
        try {
            JsonObject json = readJson(url);
            return parseLangJson(json);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private Map<String, String> parseLangJson(JsonObject json) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("item.minecraft.")) {
                result.put(key.substring("item.minecraft.".length()), entry.getValue().getAsString());
            } else if (key.startsWith("block.minecraft.")) {
                result.put(key.substring("block.minecraft.".length()), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private Map<String, String> loadDictionary(String code) {
        File dictFile = new File(itemsDir, code + ".yml");
        if (!dictFile.exists()) {
            return Collections.emptyMap();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dictFile);
        String version = cfg.getString(VERSION_KEY, null);
        if (version != null) {
            dictVersions.putIfAbsent(code, version);
        }
        dictSizes.putIfAbsent(code, countEntries(cfg));
        Map<String, String> map = new HashMap<>();
        for (String key : cfg.getKeys(false)) {
            if (VERSION_KEY.equals(key)) continue;
            String value = cfg.getString(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    private Map<String, String> buildFallbackMap() {
        Map<String, String> fallback = new HashMap<>();
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                continue;
            }
            String key = material.getKey().getKey();
            String name = titleCase(key.replace('_', ' '));
            fallback.putIfAbsent(key, name);
        }
        return fallback;
    }

    private JsonObject readJson(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Exort");
        try (InputStream in = connection.getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private String safeServerVersion() {
        String version = Bukkit.getMinecraftVersion();
        if (version == null || version.isBlank()) {
            version = "unknown";
        }
        return version;
    }

    private int compareVersions(String a, String b) {
        int[] va = parseVersion(a);
        int[] vb = parseVersion(b);
        int len = Math.max(va.length, vb.length);
        for (int i = 0; i < len; i++) {
            int ai = i < va.length ? va[i] : 0;
            int bi = i < vb.length ? vb[i] : 0;
            int cmp = Integer.compare(ai, bi);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private int[] parseVersion(String version) {
        if (version == null) return new int[]{0};
        List<Integer> parts = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        for (char c : version.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                parts.add(Integer.parseInt(num.toString()));
                num.setLength(0);
            }
        }
        if (num.length() > 0) {
            parts.add(Integer.parseInt(num.toString()));
        }
        if (parts.isEmpty()) {
            parts.add(0);
        }
        int[] arr = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            arr[i] = parts.get(i);
        }
        return arr;
    }

    private String titleCase(String value) {
        String[] parts = value.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }

    private void logReload(List<String> updated) {
        boolean indexAvailable = !availableLanguages.isEmpty() || indexFile.exists();
        String source = indexFetched ? "fetched" : (indexFile.exists() ? "cached" : "missing");
        String message = "Item translations: active=" + activeLanguage
                + ", server=" + serverVersion
                + ", index=" + source
                + " (" + availableLanguages.size() + " languages)";
        if (updated != null && !updated.isEmpty()) {
            message += ", updated=[" + String.join(", ", updated) + "]";
        }
        plugin.getLogger().info(message);
        if (!indexAvailable) {
            plugin.getLogger().warning("Minecraft language index is not available. Using fallback item names.");
        }
    }

    private int countEntries(YamlConfiguration cfg) {
        int count = 0;
        for (String key : cfg.getKeys(false)) {
            if (VERSION_KEY.equals(key)) continue;
            count++;
        }
        return count;
    }

    public record Status(String activeLanguage,
                         String serverVersion,
                         boolean indexCached,
                         boolean indexFetched,
                         int availableLanguages,
                         Map<String, String> dictVersions,
                         Map<String, Integer> dictSizes) {
    }
}
