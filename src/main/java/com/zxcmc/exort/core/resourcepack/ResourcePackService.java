package com.zxcmc.exort.core.resourcepack;

import com.zxcmc.exort.core.ExortPlugin;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ResourcePackService implements Listener {
  private static final UUID PACK_ID =
      UUID.nameUUIDFromBytes("zxcmc:exort:resource-pack".getBytes(StandardCharsets.UTF_8));

  // Filled in a future release when the immutable official HTTPS pack is published.
  private static final String OFFICIAL_PACK_URL = "";
  private static final String OFFICIAL_PACK_SHA1 = "";

  private final ExortPlugin plugin;
  private final SelfHostPackServer selfHost;
  private final AtomicLong generation = new AtomicLong();
  private final ConcurrentMap<UUID, String> configurationAttempts = new ConcurrentHashMap<>();
  private final AtomicReference<State> state =
      new AtomicReference<>(
          State.disabled("Not started", ResourcePackHosting.AUTO, ResourcePackDelivery.AUTO));

  public ResourcePackService(ExortPlugin plugin) {
    this.plugin = plugin;
    this.selfHost = new SelfHostPackServer(plugin);
  }

  public synchronized void reload() {
    long currentGeneration = generation.incrementAndGet();
    configurationAttempts.clear();
    selfHost.stop();
    NexoPackBridge.removeIfPresent(plugin);

    ResourcePackHosting configured =
        ResourcePackHosting.fromConfig(
            plugin.getConfig().getString("resourcePack.hosting", "AUTO"));
    ResourcePackDelivery configuredDelivery =
        ResourcePackDelivery.fromConfig(
            plugin.getConfig().getString("resourcePack.delivery", "AUTO"));
    DeliverySettings deliverySettings = readDeliverySettings(configuredDelivery);

    if (!plugin.isResourceMode()) {
      state.set(State.disabled("Effective mode is VANILLA", configured, configuredDelivery));
      return;
    }
    if (configured == ResourcePackHosting.DISABLED) {
      state.set(State.disabled("resourcePack.hosting is DISABLED", configured, configuredDelivery));
      return;
    }

    ResourcePackHosting effective = resolveHosting(configured);
    if (effective == ResourcePackHosting.EXORT) {
      readyOfficialPack(configured, deliverySettings);
      return;
    }

    boolean obfuscate = plugin.getConfig().getBoolean("resourcePack.obfuscation", true);
    PackExporter.Result pack = PackExporter.exportPack(plugin, obfuscate);
    if (!pack.available()) {
      state.set(
          State.error(
              configured,
              effective,
              configuredDelivery,
              ResourcePackDelivery.MANUAL,
              "Pack export failed",
              pack,
              null,
              null,
              null));
      return;
    }

    if (effective == ResourcePackHosting.NEXO) {
      if (!NexoPackBridge.copyIfPresent(plugin, pack.rawFile())) {
        state.set(
            State.error(
                configured,
                effective,
                configuredDelivery,
                ResourcePackDelivery.MANUAL,
                "Nexo is not available",
                pack,
                null,
                null,
                null));
        plugin.getLogger().warning("Resource-pack hosting is NEXO, but Nexo is not enabled.");
        return;
      }
      setReady(
          configured,
          effective,
          deliverySettings,
          pack,
          null,
          pack.rawSha1(),
          "Resource-pack delivery is managed by Nexo; use Nexo's delivery commands/settings.");
      return;
    }

    if (effective == ResourcePackHosting.SELFHOST) {
      try {
        String url =
            selfHost.start(
                pack.outputFile(),
                plugin.getConfig().getString("resourcePack.selfHost.bindHost", "0.0.0.0"),
                plugin.getConfig().getInt("resourcePack.selfHost.port", 0),
                plugin.getConfig().getString("resourcePack.selfHost.publicUrl", ""));
        setReady(configured, effective, deliverySettings, pack, url, pack.outputSha1(), null);
      } catch (IOException e) {
        state.set(
            State.error(
                configured,
                effective,
                configuredDelivery,
                ResourcePackDelivery.MANUAL,
                e.getMessage(),
                pack,
                null,
                null,
                null));
        plugin.getLogger().warning("Failed to start resource-pack self-hosting: " + e.getMessage());
      }
      return;
    }

    if (effective == ResourcePackHosting.LOBFILE) {
      String apiKey = readLobFileApiKey();
      if (apiKey == null || apiKey.isBlank()) {
        state.set(
            State.error(
                configured,
                effective,
                configuredDelivery,
                ResourcePackDelivery.MANUAL,
                "LobFile API key is missing",
                pack,
                null,
                null,
                null));
        plugin
            .getLogger()
            .warning("Resource-pack hosting is LOBFILE, but resourcePack.lobfile.apiKey is empty.");
        return;
      }
      state.set(State.uploading(configured, effective, configuredDelivery, pack));
      Bukkit.getScheduler()
          .runTaskAsynchronously(
              plugin,
              () -> {
                try {
                  String url = LobFileUploader.upload(pack.outputFile(), pack.outputSha1(), apiKey);
                  if (generation.get() != currentGeneration) {
                    return;
                  }
                  setReady(
                      configured, effective, deliverySettings, pack, url, pack.outputSha1(), null);
                } catch (IOException | RuntimeException e) {
                  if (generation.get() != currentGeneration) {
                    return;
                  }
                  state.set(
                      State.error(
                          configured,
                          effective,
                          configuredDelivery,
                          ResourcePackDelivery.MANUAL,
                          e.getMessage(),
                          pack,
                          null,
                          null,
                          null));
                  plugin
                      .getLogger()
                      .warning("LobFile resource-pack upload failed: " + e.getMessage());
                }
              });
    }
  }

  public synchronized void stop() {
    generation.incrementAndGet();
    configurationAttempts.clear();
    selfHost.stop();
    state.set(State.disabled("Stopped", ResourcePackHosting.AUTO, ResourcePackDelivery.AUTO));
  }

  private ResourcePackHosting resolveHosting(ResourcePackHosting configured) {
    if (configured != ResourcePackHosting.AUTO) {
      return configured;
    }
    if (NexoPackBridge.isNexoEnabled(plugin)) {
      return ResourcePackHosting.NEXO;
    }
    if (officialPackConfigured()) {
      return ResourcePackHosting.EXORT;
    }
    return ResourcePackHosting.SELFHOST;
  }

  private DeliverySettings readDeliverySettings(ResourcePackDelivery configuredDelivery) {
    return new DeliverySettings(
        configuredDelivery,
        serverResourcePackConfigured(),
        plugin.getConfig().getBoolean("resourcePack.required", true),
        prompt(),
        Math.max(1, plugin.getConfig().getInt("resourcePack.configurationTimeoutSeconds", 30)),
        plugin.getConfig().getBoolean("resourcePack.sendOnlineOnReady", false));
  }

  private void readyOfficialPack(ResourcePackHosting configured, DeliverySettings settings) {
    if (!officialPackConfigured()) {
      state.set(
          State.error(
              configured,
              ResourcePackHosting.EXORT,
              settings.configuredDelivery(),
              ResourcePackDelivery.MANUAL,
              "Official Exort resource-pack URL/SHA-1 are not configured yet",
              null,
              null,
              null,
              "Publish immutable HTTPS pack metadata before enabling EXORT hosting."));
      return;
    }
    setReady(
        configured,
        ResourcePackHosting.EXORT,
        settings,
        null,
        OFFICIAL_PACK_URL,
        OFFICIAL_PACK_SHA1,
        "Official Exort pack via external HTTPS hosting");
  }

  private boolean officialPackConfigured() {
    return !OFFICIAL_PACK_URL.isBlank() && isSha1(OFFICIAL_PACK_SHA1);
  }

  private void setReady(
      ResourcePackHosting configured,
      ResourcePackHosting effective,
      DeliverySettings settings,
      PackExporter.Result pack,
      String url,
      String sha1,
      String note) {
    boolean dispatchReady = isDirectHosting(effective) && isDispatchMetadataValid(url, sha1);
    ResourcePackDelivery effectiveDelivery = resolveDelivery(settings, dispatchReady);
    State ready =
        State.ready(
            configured,
            effective,
            settings.configuredDelivery(),
            effectiveDelivery,
            pack,
            url,
            sha1,
            mergeNotes(note, autoDeliveryNote(settings, dispatchReady, effectiveDelivery)),
            dispatchReady,
            settings.required(),
            settings.prompt(),
            settings.configurationTimeoutSeconds(),
            settings.sendOnlineOnReady());
    state.set(ready);
    logReadyState(ready);
    if (ready.dispatchReady() && ready.sendOnlineOnReady()) {
      scheduleSendAll();
    }
  }

  private ResourcePackDelivery resolveDelivery(DeliverySettings settings, boolean dispatchReady) {
    if (!dispatchReady) {
      return ResourcePackDelivery.MANUAL;
    }
    if (settings.configuredDelivery() != ResourcePackDelivery.AUTO) {
      return settings.configuredDelivery();
    }
    if (settings.serverResourcePackConfigured()) {
      return ResourcePackDelivery.JOIN;
    }
    return ResourcePackDelivery.CONFIGURATION;
  }

  private String autoDeliveryNote(
      DeliverySettings settings, boolean dispatchReady, ResourcePackDelivery effectiveDelivery) {
    if (settings.configuredDelivery() == ResourcePackDelivery.AUTO
        && dispatchReady
        && effectiveDelivery == ResourcePackDelivery.JOIN
        && settings.serverResourcePackConfigured()) {
      return "server.properties resource-pack is configured; Exort will send its pack after join";
    }
    return null;
  }

  private boolean serverResourcePackConfigured() {
    String configuredPack = Bukkit.getResourcePack();
    return configuredPack != null && !configuredPack.isBlank();
  }

  private void logReadyState(State ready) {
    if (ready.effective() == ResourcePackHosting.NEXO) {
      plugin.getLogger().info("Resource-pack delivery is managed by Nexo.");
      return;
    }
    if (ready.dispatchReady()) {
      plugin
          .getLogger()
          .info(
              "Resource-pack delivery is ready via "
                  + ready.effective()
                  + " ("
                  + ready.effectiveDelivery()
                  + "): "
                  + ready.url());
    }
  }

  private void scheduleSendAll() {
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              int sent = sendAll();
              if (sent > 0) {
                plugin
                    .getLogger()
                    .info("Sent Exort resource pack to " + sent + " online player(s).");
              }
            },
            20L);
  }

  public boolean send(Player player) {
    State current = state.get();
    if (!current.dispatchReady()) {
      return false;
    }
    try {
      send(player, current, ResourcePackCallback.noOp());
      return true;
    } catch (RuntimeException e) {
      plugin.getLogger().warning("Cannot send Exort resource pack: " + e.getMessage());
      return false;
    }
  }

  private void send(Audience audience, State current, ResourcePackCallback callback) {
    audience.sendResourcePacks(resourcePackRequest(current, callback));
  }

  private ResourcePackRequest resourcePackRequest(State current, ResourcePackCallback callback) {
    ResourcePackInfo info =
        ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(current.url()), current.sha1());
    return ResourcePackRequest.resourcePackRequest()
        .packs(info)
        .replace(false)
        .required(current.required())
        .prompt(current.prompt())
        .callback(callback)
        .build();
  }

  public int sendAll() {
    int sent = 0;
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (send(player)) {
        sent++;
      }
    }
    return sent;
  }

  public List<String> statusLines() {
    State current = state.get();
    List<String> lines = new ArrayList<>();
    lines.add("Resource pack status: " + current.status());
    lines.add("Configured hosting: " + current.configured());
    lines.add("Effective hosting: " + current.effective());
    lines.add("Configured delivery: " + current.configuredDelivery());
    lines.add("Effective delivery: " + current.effectiveDelivery());
    if (current.pack() != null && current.pack().available()) {
      lines.add("Raw pack: " + current.pack().rawFile().getPath());
      lines.add("Pack: " + current.pack().outputFile().getPath());
      lines.add("Obfuscated: " + current.pack().obfuscated());
    }
    if (current.sha1() != null && !current.sha1().isBlank()) {
      lines.add("SHA-1: " + current.sha1());
    }
    if (current.url() != null) {
      lines.add("URL: " + current.url());
    }
    if (current.note() != null && !current.note().isBlank()) {
      lines.add("Note: " + current.note());
    }
    if (current.error() != null && !current.error().isBlank()) {
      lines.add("Error: " + current.error());
    }
    return lines;
  }

  public boolean dispatchReady() {
    return state.get().dispatchReady();
  }

  public String unavailableReason() {
    State current = state.get();
    if (current.dispatchReady()) {
      return "";
    }
    if (current.error() != null && !current.error().isBlank()) {
      return current.error();
    }
    if (current.note() != null && !current.note().isBlank()) {
      return current.note();
    }
    if ("READY".equals(current.status())) {
      return "direct resource-pack sending is unavailable for " + current.effective();
    }
    return current.status();
  }

  @EventHandler
  public void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event) {
    State current = state.get();
    if (!current.dispatchReady()
        || current.effectiveDelivery() != ResourcePackDelivery.CONFIGURATION) {
      return;
    }
    UUID playerId = event.getConnection().getProfile().getId();
    if (playerId == null) {
      return;
    }

    CompletableFuture<ResourcePackStatus> terminalStatus = new CompletableFuture<>();
    ResourcePackCallback callback =
        (packId, status, audience) -> {
          if (PACK_ID.equals(packId) && !status.intermediate()) {
            terminalStatus.complete(status);
          }
        };
    try {
      configurationAttempts.put(playerId, current.sha1());
      send(event.getConnection().getAudience(), current, callback);
    } catch (RuntimeException e) {
      configurationAttempts.remove(playerId, current.sha1());
      plugin
          .getLogger()
          .warning("Cannot send Exort resource pack during configuration: " + e.getMessage());
      if (current.required()) {
        event.getConnection().disconnect(requiredPackFailureMessage());
      }
      return;
    }

    ResourcePackStatus status = waitForTerminalStatus(terminalStatus, current);
    if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
      return;
    }
    if (current.required()) {
      event.getConnection().disconnect(requiredPackFailureMessage());
    }
  }

  private ResourcePackStatus waitForTerminalStatus(
      CompletableFuture<ResourcePackStatus> terminalStatus, State current) {
    try {
      return terminalStatus.get(current.configurationTimeoutSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (TimeoutException e) {
      return null;
    } catch (ExecutionException e) {
      plugin.getLogger().warning("Resource-pack status callback failed: " + e.getMessage());
      return null;
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    State current = state.get();
    if (!current.dispatchReady() || current.effectiveDelivery() != ResourcePackDelivery.JOIN) {
      return;
    }
    UUID playerId = event.getPlayer().getUniqueId();
    if (current.sha1().equals(configurationAttempts.get(playerId))) {
      return;
    }
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              State latest = state.get();
              if (!latest.dispatchReady()
                  || latest.effectiveDelivery() != ResourcePackDelivery.JOIN
                  || latest.sha1().equals(configurationAttempts.get(playerId))) {
                return;
              }
              send(event.getPlayer());
            },
            20L);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    configurationAttempts.remove(event.getPlayer().getUniqueId());
  }

  private Component prompt() {
    String prompt = plugin.getConfig().getString("resourcePack.prompt", "");
    if (prompt == null || prompt.isBlank()) {
      return null;
    }
    try {
      return MiniMessage.miniMessage().deserialize(prompt);
    } catch (RuntimeException ignored) {
      return Component.text(prompt);
    }
  }

  private Component requiredPackFailureMessage() {
    return Component.text("Required Exort resource pack failed to load. Reconnect and accept it.");
  }

  private boolean isDirectHosting(ResourcePackHosting hosting) {
    return hosting == ResourcePackHosting.EXORT
        || hosting == ResourcePackHosting.SELFHOST
        || hosting == ResourcePackHosting.LOBFILE;
  }

  private boolean isDispatchMetadataValid(String url, String sha1) {
    if (url == null || url.isBlank() || !isSha1(sha1)) {
      return false;
    }
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private boolean isSha1(String sha1) {
    return sha1 != null && sha1.matches("(?i)[0-9a-f]{40}");
  }

  private String mergeNotes(String first, String second) {
    if (first == null || first.isBlank()) {
      return second;
    }
    if (second == null || second.isBlank()) {
      return first;
    }
    return first + "; " + second;
  }

  private String readLobFileApiKey() {
    String configured = plugin.getConfig().getString("resourcePack.lobfile.apiKey", "");
    return configured == null ? "" : configured.trim();
  }

  private record DeliverySettings(
      ResourcePackDelivery configuredDelivery,
      boolean serverResourcePackConfigured,
      boolean required,
      Component prompt,
      int configurationTimeoutSeconds,
      boolean sendOnlineOnReady) {}

  private record State(
      String status,
      ResourcePackHosting configured,
      ResourcePackHosting effective,
      ResourcePackDelivery configuredDelivery,
      ResourcePackDelivery effectiveDelivery,
      PackExporter.Result pack,
      String url,
      String sha1,
      String note,
      String error,
      boolean dispatchReady,
      boolean required,
      Component prompt,
      int configurationTimeoutSeconds,
      boolean sendOnlineOnReady) {
    static State disabled(
        String note, ResourcePackHosting configured, ResourcePackDelivery configuredDelivery) {
      return new State(
          "DISABLED",
          configured,
          ResourcePackHosting.DISABLED,
          configuredDelivery,
          ResourcePackDelivery.MANUAL,
          null,
          null,
          null,
          note,
          null,
          false,
          false,
          null,
          30,
          false);
    }

    static State uploading(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        ResourcePackDelivery configuredDelivery,
        PackExporter.Result pack) {
      return new State(
          "UPLOADING",
          configured,
          effective,
          configuredDelivery,
          ResourcePackDelivery.MANUAL,
          pack,
          null,
          pack.outputSha1(),
          null,
          null,
          false,
          false,
          null,
          30,
          false);
    }

    static State ready(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        ResourcePackDelivery configuredDelivery,
        ResourcePackDelivery effectiveDelivery,
        PackExporter.Result pack,
        String url,
        String sha1,
        String note,
        boolean dispatchReady,
        boolean required,
        Component prompt,
        int configurationTimeoutSeconds,
        boolean sendOnlineOnReady) {
      return new State(
          "READY",
          configured,
          effective,
          configuredDelivery,
          effectiveDelivery,
          pack,
          url,
          sha1,
          note,
          null,
          dispatchReady,
          required,
          prompt,
          configurationTimeoutSeconds,
          sendOnlineOnReady);
    }

    static State error(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        ResourcePackDelivery configuredDelivery,
        ResourcePackDelivery effectiveDelivery,
        String error,
        PackExporter.Result pack,
        String url,
        String sha1,
        String note) {
      return new State(
          "ERROR",
          configured,
          effective,
          configuredDelivery,
          effectiveDelivery,
          pack,
          url,
          sha1,
          note,
          error,
          false,
          false,
          null,
          30,
          false);
    }
  }
}
