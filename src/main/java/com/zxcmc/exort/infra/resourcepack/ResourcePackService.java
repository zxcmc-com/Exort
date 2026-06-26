package com.zxcmc.exort.infra.resourcepack;

import com.zxcmc.exort.infra.logging.ExortLog;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourcePackService implements Listener {
  @FunctionalInterface
  public interface Translator {
    String tr(CommandSender sender, String key, Object... params);

    default String tr(String key, Object... params) {
      return tr(null, key, params);
    }
  }

  private static final String PROVIDER_NOTE_PREFIX = "provider:";
  private static final UUID PACK_ID =
      UUID.nameUUIDFromBytes("zxcmc:exort:resource-pack".getBytes(StandardCharsets.UTF_8));

  private static final String OFFICIAL_PACK_METADATA_URL =
      "https://exort.zxcmc.com/resource-pack/exort/latest.json";
  private static final Duration OFFICIAL_PACK_METADATA_TIMEOUT = Duration.ofSeconds(10);
  private static final long NO_GENERATION = -1L;

  private final JavaPlugin plugin;
  private final BooleanSupplier resourceMode;
  private final Translator translator;
  private final Function<String, Component> richTextParser;
  private final NexoResourcePackIntegration nexoIntegration;
  private final SelfHostPackServer selfHost;
  private final AtomicLong generation = new AtomicLong();
  private final ConcurrentMap<UUID, String> configurationAttempts = new ConcurrentHashMap<>();
  private final Object stateMonitor = new Object();
  private final AtomicReference<State> state;
  private final AtomicLong onlineSendRequestGeneration = new AtomicLong(NO_GENERATION);
  private final AtomicLong onlineSendScheduledGeneration = new AtomicLong(NO_GENERATION);

  public ResourcePackService(
      JavaPlugin plugin,
      BooleanSupplier resourceMode,
      Translator translator,
      Function<String, Component> richTextParser) {
    this(plugin, resourceMode, translator, richTextParser, new NexoResourcePackIntegration());
  }

  public ResourcePackService(
      JavaPlugin plugin,
      BooleanSupplier resourceMode,
      Translator translator,
      Function<String, Component> richTextParser,
      NexoResourcePackIntegration nexoIntegration) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.resourceMode = Objects.requireNonNull(resourceMode, "resourceMode");
    this.translator = Objects.requireNonNull(translator, "translator");
    this.richTextParser = Objects.requireNonNull(richTextParser, "richTextParser");
    this.nexoIntegration = Objects.requireNonNull(nexoIntegration, "nexoIntegration");
    this.selfHost = new SelfHostPackServer();
    this.state = new AtomicReference<>(initialState());
  }

  private State initialState() {
    ResourcePackHosting configured =
        ResourcePackHosting.fromConfig(
            plugin.getConfig().getString("resourcePack.hosting", "AUTO"));
    ResourcePackDelivery configuredDelivery =
        ResourcePackDelivery.fromConfig(
            plugin.getConfig().getString("resourcePack.delivery", "AUTO"));
    if (configured == ResourcePackHosting.DISABLED) {
      return State.disabled("resourcePack.hosting is DISABLED", configured, configuredDelivery);
    }
    if (!resourceMode.getAsBoolean()) {
      return State.disabled("Effective mode is VANILLA", configured, configuredDelivery);
    }
    DeliverySettings settings = readDeliverySettings(configuredDelivery);
    ResourcePackHosting effective = resolveHosting(configured);
    if (configurationGateEnabled(
        effective, settings.configuredDelivery(), settings.serverResourcePackConfigured())) {
      return State.starting(
          configured,
          effective,
          settings,
          "Waiting for Exort resource-pack delivery to initialize");
    }
    return State.disabled("Not started", configured, configuredDelivery);
  }

  private void setState(State next) {
    state.set(next);
    synchronized (stateMonitor) {
      stateMonitor.notifyAll();
    }
  }

  public synchronized void reload() {
    long currentGeneration = generation.incrementAndGet();
    configurationAttempts.clear();
    onlineSendRequestGeneration.set(NO_GENERATION);
    onlineSendScheduledGeneration.set(NO_GENERATION);
    selfHost.stop();
    nexoIntegration.clearApiHandoff();

    ResourcePackHosting configured =
        ResourcePackHosting.fromConfig(
            plugin.getConfig().getString("resourcePack.hosting", "AUTO"));
    ResourcePackDelivery configuredDelivery =
        ResourcePackDelivery.fromConfig(
            plugin.getConfig().getString("resourcePack.delivery", "AUTO"));
    DeliverySettings deliverySettings = readDeliverySettings(configuredDelivery);

    if (configured == ResourcePackHosting.DISABLED) {
      ResourcePackProviderBridge.removeAll(plugin);
      setState(State.disabled("resourcePack.hosting is DISABLED", configured, configuredDelivery));
      return;
    }
    if (!resourceMode.getAsBoolean()) {
      ResourcePackProviderBridge.removeAll(plugin);
      setState(State.disabled("Effective mode is VANILLA", configured, configuredDelivery));
      return;
    }

    ResourcePackHosting effective = resolveHosting(configured);
    if (!isProviderHosting(effective)) {
      ResourcePackProviderBridge.removeOtherProviderHandoffs(plugin, effective);
    }
    if (effective == ResourcePackHosting.EXORT) {
      resolveOfficialPack(configured, deliverySettings, currentGeneration);
      return;
    }

    PackExporter.Result pack = PackExporter.exportPack(plugin);
    if (!pack.available()) {
      setState(
          errorState(
              configured,
              effective,
              deliverySettings,
              ResourcePackDelivery.MANUAL,
              "Pack export failed",
              pack,
              null,
              null,
              null,
              null));
      return;
    }

    if (isProviderHosting(effective)) {
      ResourcePackProviderBridge.removeOtherProviderHandoffs(plugin, effective);
      ResourcePackProviderBridge.HandoffResult handoff = providerHandoff(effective, pack.rawFile());
      if (!handoff.success()) {
        setState(
            errorState(
                configured,
                effective,
                deliverySettings,
                ResourcePackDelivery.MANUAL,
                handoff.error(),
                pack,
                handoff.targetPath(),
                null,
                null,
                null));
        ExortLog.warn(
            "Resource-pack hosting is "
                + effective
                + ", but provider handoff failed: "
                + handoff.error());
        return;
      }
      setReady(
          configured,
          effective,
          deliverySettings,
          pack,
          handoff.targetPath(),
          null,
          pack.rawSha1(),
          providerManagedNote(effective));
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
        setReady(configured, effective, deliverySettings, pack, null, url, pack.outputSha1(), null);
      } catch (IOException e) {
        setState(
            errorState(
                configured,
                effective,
                deliverySettings,
                ResourcePackDelivery.MANUAL,
                e.getMessage(),
                pack,
                null,
                null,
                null,
                null));
        ExortLog.warn("Failed to start resource-pack self-hosting: " + e.getMessage());
      }
      return;
    }

    if (effective == ResourcePackHosting.LOBFILE) {
      String apiKey = readLobFileApiKey();
      if (apiKey == null || apiKey.isBlank()) {
        setState(
            errorState(
                configured,
                effective,
                deliverySettings,
                ResourcePackDelivery.MANUAL,
                "LobFile API key is missing",
                pack,
                null,
                null,
                null,
                null));
        plugin
            .getLogger()
            .warning("Resource-pack hosting is LOBFILE, but resourcePack.lobfile.apiKey is empty.");
        return;
      }
      setState(State.uploading(configured, effective, deliverySettings, pack));
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
                      configured,
                      effective,
                      deliverySettings,
                      pack,
                      null,
                      url,
                      pack.outputSha1(),
                      null);
                } catch (IOException | RuntimeException e) {
                  if (generation.get() != currentGeneration) {
                    return;
                  }
                  setState(
                      errorState(
                          configured,
                          effective,
                          deliverySettings,
                          ResourcePackDelivery.MANUAL,
                          e.getMessage(),
                          pack,
                          null,
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
    onlineSendRequestGeneration.set(NO_GENERATION);
    onlineSendScheduledGeneration.set(NO_GENERATION);
    selfHost.stop();
    setState(State.disabled("Stopped", ResourcePackHosting.AUTO, ResourcePackDelivery.AUTO));
  }

  private ResourcePackHosting resolveHosting(ResourcePackHosting configured) {
    if (configured != ResourcePackHosting.AUTO) {
      return configured;
    }
    return resolveAutoHosting(
        ResourcePackProviderBridge.isProviderInstalled(plugin, ResourcePackHosting.NEXO),
        ResourcePackProviderBridge.isProviderInstalled(plugin, ResourcePackHosting.ITEMSADDER),
        ResourcePackProviderBridge.isProviderInstalled(plugin, ResourcePackHosting.ORAXEN),
        officialPackConfigured());
  }

  private State errorState(
      ResourcePackHosting configured,
      ResourcePackHosting effective,
      DeliverySettings settings,
      ResourcePackDelivery effectiveDelivery,
      String error,
      PackExporter.Result pack,
      String handoffTarget,
      String url,
      String sha1,
      String note) {
    return State.error(
        configured,
        effective,
        settings.configuredDelivery(),
        effectiveDelivery,
        error,
        pack,
        handoffTarget,
        url,
        sha1,
        note,
        configurationGateEnabled(
            effective, settings.configuredDelivery(), settings.serverResourcePackConfigured()),
        settings.required(),
        settings.prompt(),
        settings.configurationTimeoutSeconds(),
        settings.sendOnlineOnReady());
  }

  static ResourcePackHosting resolveAutoHosting(
      boolean nexoInstalled,
      boolean itemsAdderInstalled,
      boolean oraxenInstalled,
      boolean officialConfigured) {
    if (nexoInstalled) {
      return ResourcePackHosting.NEXO;
    }
    if (itemsAdderInstalled) {
      return ResourcePackHosting.ITEMSADDER;
    }
    if (oraxenInstalled) {
      return ResourcePackHosting.ORAXEN;
    }
    if (officialConfigured) {
      return ResourcePackHosting.EXORT;
    }
    return ResourcePackHosting.SELFHOST;
  }

  private boolean isProviderHosting(ResourcePackHosting hosting) {
    return hosting == ResourcePackHosting.NEXO
        || hosting == ResourcePackHosting.ITEMSADDER
        || hosting == ResourcePackHosting.ORAXEN;
  }

  private ResourcePackProviderBridge.HandoffResult providerHandoff(
      ResourcePackHosting hosting, java.io.File rawPack) {
    if (hosting == ResourcePackHosting.NEXO && nexoIntegration.isApiHandoffAvailable()) {
      ResourcePackProviderBridge.HandoffResult apiHandoff =
          ResourcePackProviderBridge.prepareNexoApiHandoff(plugin, rawPack);
      if (apiHandoff.success() && nexoIntegration.useApiHandoff(rawPack)) {
        return apiHandoff;
      }
      nexoIntegration.clearApiHandoff();
      String reason =
          apiHandoff.success()
              ? "listener state is unavailable"
              : String.valueOf(apiHandoff.error());
      ExortLog.warn(
          "[Nexo] API resource-pack handoff unavailable; falling back to external_packs: "
              + reason);
    }
    return ResourcePackProviderBridge.handoff(plugin, hosting, rawPack);
  }

  static boolean configurationGateEnabled(
      ResourcePackHosting effective,
      ResourcePackDelivery configuredDelivery,
      boolean serverResourcePackConfigured) {
    if (!isDirectHosting(effective)) {
      return false;
    }
    if (configuredDelivery == ResourcePackDelivery.CONFIGURATION) {
      return true;
    }
    return configuredDelivery == ResourcePackDelivery.AUTO && !serverResourcePackConfigured;
  }

  private String providerManagedNote(ResourcePackHosting hosting) {
    return PROVIDER_NOTE_PREFIX + providerDisplayName(hosting);
  }

  private String providerDisplayName(ResourcePackHosting hosting) {
    return switch (hosting) {
      case NEXO -> "Nexo";
      case ITEMSADDER -> "ItemsAdder";
      case ORAXEN -> "Oraxen";
      default -> hosting.name();
    };
  }

  private DeliverySettings readDeliverySettings(ResourcePackDelivery configuredDelivery) {
    return new DeliverySettings(
        configuredDelivery,
        serverResourcePackConfigured(),
        true,
        prompt(),
        Math.max(1, plugin.getConfig().getInt("resourcePack.configurationTimeoutSeconds", 30)),
        plugin.getConfig().getBoolean("resourcePack.sendOnlineOnReady", false));
  }

  private void resolveOfficialPack(
      ResourcePackHosting configured, DeliverySettings settings, long currentGeneration) {
    if (!officialPackConfigured()) {
      setState(
          errorState(
              configured,
              ResourcePackHosting.EXORT,
              settings,
              ResourcePackDelivery.MANUAL,
              "Official Exort resource-pack URL/SHA-1 are not configured yet",
              null,
              null,
              null,
              null,
              "Publish immutable HTTPS pack metadata before enabling EXORT hosting."));
      return;
    }
    setState(State.resolving(configured, settings));
    Bukkit.getScheduler()
        .runTaskAsynchronously(
            plugin,
            () -> {
              try {
                OfficialPackMetadata metadata =
                    OfficialPackMetadata.fetch(
                        OFFICIAL_PACK_METADATA_URL, OFFICIAL_PACK_METADATA_TIMEOUT);
                if (generation.get() != currentGeneration) {
                  return;
                }
                setReady(
                    configured,
                    ResourcePackHosting.EXORT,
                    settings,
                    null,
                    null,
                    metadata.url(),
                    metadata.sha1(),
                    officialPackNote(metadata));
              } catch (IOException | RuntimeException e) {
                if (generation.get() != currentGeneration) {
                  return;
                }
                setState(
                    errorState(
                        configured,
                        ResourcePackHosting.EXORT,
                        settings,
                        ResourcePackDelivery.MANUAL,
                        "Official Exort resource-pack metadata is unavailable: " + e.getMessage(),
                        null,
                        null,
                        null,
                        null,
                        "Checked " + OFFICIAL_PACK_METADATA_URL));
                ExortLog.warn(
                    "Official Exort resource-pack metadata is unavailable: " + e.getMessage());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (generation.get() != currentGeneration) {
                  return;
                }
                setState(
                    errorState(
                        configured,
                        ResourcePackHosting.EXORT,
                        settings,
                        ResourcePackDelivery.MANUAL,
                        "Official Exort resource-pack metadata lookup was interrupted",
                        null,
                        null,
                        null,
                        null,
                        "Checked " + OFFICIAL_PACK_METADATA_URL));
              }
            });
  }

  private boolean officialPackConfigured() {
    return isHttpsUrl(OFFICIAL_PACK_METADATA_URL);
  }

  private String officialPackNote(OfficialPackMetadata metadata) {
    if (metadata.version() == null || metadata.version().isBlank()) {
      return "Official Exort pack via external HTTPS hosting";
    }
    return "Official Exort pack " + metadata.version() + " via external HTTPS hosting";
  }

  private void setReady(
      ResourcePackHosting configured,
      ResourcePackHosting effective,
      DeliverySettings settings,
      PackExporter.Result pack,
      String handoffTarget,
      String url,
      String sha1,
      String note) {
    boolean dispatchReady = isDirectHosting(effective) && isDispatchMetadataValid(url, sha1);
    ResourcePackDelivery effectiveDelivery = resolveDelivery(settings, dispatchReady);
    boolean configurationGate =
        configurationGateEnabled(
            effective, settings.configuredDelivery(), settings.serverResourcePackConfigured());
    State ready =
        State.ready(
            configured,
            effective,
            settings.configuredDelivery(),
            effectiveDelivery,
            pack,
            handoffTarget,
            url,
            sha1,
            mergeNotes(note, autoDeliveryNote(settings, dispatchReady, effectiveDelivery)),
            dispatchReady,
            configurationGate,
            settings.required(),
            settings.prompt(),
            settings.configurationTimeoutSeconds(),
            settings.sendOnlineOnReady());
    setState(ready);
    logReadyState(ready);
    boolean requestedOnlineSend = consumeOnlineSendRequest(ready);
    if (ready.dispatchReady() && (ready.sendOnlineOnReady() || requestedOnlineSend)) {
      scheduleSendAllOnce(generation.get());
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
      ExortLog.info("Resource-pack delivery is managed by Nexo: " + ready.handoffTarget());
      return;
    }
    if (ready.effective() == ResourcePackHosting.ITEMSADDER) {
      ExortLog.info("Resource-pack delivery is managed by ItemsAdder: " + ready.handoffTarget());
      return;
    }
    if (ready.effective() == ResourcePackHosting.ORAXEN) {
      ExortLog.info("Resource-pack delivery is managed by Oraxen: " + ready.handoffTarget());
      return;
    }
    if (ready.dispatchReady()) {
      ExortLog.success(
          "Resource-pack delivery is ready via "
              + ready.effective()
              + " ("
              + ready.effectiveDelivery()
              + "): "
              + ready.url());
    }
  }

  public void requestSendOnlineWhenReady() {
    long currentGeneration = generation.get();
    State current = state.get();
    OnlineSendDecision decision = onlineTransitionSendDecision(current);
    if (decision == OnlineSendDecision.SEND_NOW) {
      scheduleSendAllOnce(currentGeneration);
      return;
    }
    if (decision == OnlineSendDecision.WAIT_FOR_READY) {
      onlineSendRequestGeneration.set(currentGeneration);
    }
  }

  private boolean consumeOnlineSendRequest(State ready) {
    long currentGeneration = generation.get();
    if (onlineSendRequestGeneration.get() != currentGeneration) {
      return false;
    }
    onlineSendRequestGeneration.compareAndSet(currentGeneration, NO_GENERATION);
    return onlineTransitionSendDecision(ready) == OnlineSendDecision.SEND_NOW;
  }

  static OnlineSendDecision onlineTransitionSendDecision(State current) {
    if (current == null || current.configuredDelivery() == ResourcePackDelivery.MANUAL) {
      return OnlineSendDecision.SKIP;
    }
    if (!isDirectHosting(current.effective())) {
      return OnlineSendDecision.SKIP;
    }
    if ("READY".equals(current.status())) {
      return current.dispatchReady() ? OnlineSendDecision.SEND_NOW : OnlineSendDecision.SKIP;
    }
    return switch (current.status()) {
      case "STARTING", "RESOLVING", "UPLOADING" -> OnlineSendDecision.WAIT_FOR_READY;
      default -> OnlineSendDecision.SKIP;
    };
  }

  private void scheduleSendAllOnce(long scheduledGeneration) {
    if (!markOnlineSendScheduled(scheduledGeneration)) {
      return;
    }
    scheduleSendAll(scheduledGeneration);
  }

  private boolean markOnlineSendScheduled(long scheduledGeneration) {
    while (true) {
      long existing = onlineSendScheduledGeneration.get();
      if (existing == scheduledGeneration) {
        return false;
      }
      if (onlineSendScheduledGeneration.compareAndSet(existing, scheduledGeneration)) {
        return true;
      }
    }
  }

  private void scheduleSendAll(long scheduledGeneration) {
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (generation.get() != scheduledGeneration) {
                return;
              }
              int sent = sendAll();
              if (sent > 0) {
                ExortLog.info("Sent Exort resource pack to " + sent + " online player(s).");
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
      ExortLog.warn("Cannot send Exort resource pack: " + e.getMessage());
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
    return statusLines(null);
  }

  public List<String> statusLines(CommandSender sender) {
    State current = state.get();
    List<String> lines = new ArrayList<>();
    lines.add(translator.tr(sender, "message.pack_status.status", current.status()));
    lines.add(
        translator.tr(sender, "message.pack_status.configured_hosting", current.configured()));
    lines.add(translator.tr(sender, "message.pack_status.effective_hosting", current.effective()));
    lines.add(
        translator.tr(
            sender, "message.pack_status.configured_delivery", current.configuredDelivery()));
    lines.add(
        translator.tr(
            sender, "message.pack_status.effective_delivery", current.effectiveDelivery()));
    if (current.pack() != null && current.pack().available()) {
      lines.add(
          translator.tr(
              sender, "message.pack_status.raw_pack", current.pack().rawFile().getPath()));
      lines.add(
          translator.tr(sender, "message.pack_status.pack", current.pack().outputFile().getPath()));
    }
    if (current.handoffTarget() != null && !current.handoffTarget().isBlank()) {
      lines.add(translator.tr(sender, "message.pack_status.handoff", current.handoffTarget()));
    }
    if (current.sha1() != null && !current.sha1().isBlank()) {
      lines.add(translator.tr(sender, "message.pack_status.sha1", current.sha1()));
    }
    if (current.url() != null) {
      lines.add(translator.tr(sender, "message.pack_status.url", current.url()));
    }
    String note = displayNote(sender, current);
    if (note != null && !note.isBlank()) {
      lines.add(translator.tr(sender, "message.pack_status.note", note));
    }
    if (current.error() != null && !current.error().isBlank()) {
      lines.add(translator.tr(sender, "message.pack_status.error", current.error()));
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
    String note = displayNote(current);
    if (note != null && !note.isBlank()) {
      return note;
    }
    if ("READY".equals(current.status())) {
      return "direct resource-pack sending is unavailable for " + current.effective();
    }
    return current.status();
  }

  static ConfigurationGateDecision configurationGateDecision(State current) {
    if (current == null || !current.configurationGate()) {
      return ConfigurationGateDecision.IGNORE;
    }
    if (current.dispatchReady()
        && current.effectiveDelivery() == ResourcePackDelivery.CONFIGURATION) {
      return ConfigurationGateDecision.SEND_CONFIGURATION_PACK;
    }
    return switch (current.status()) {
      case "STARTING", "RESOLVING", "UPLOADING" -> ConfigurationGateDecision.WAIT_FOR_READY;
      case "ERROR", "READY" -> ConfigurationGateDecision.DISCONNECT;
      default -> ConfigurationGateDecision.IGNORE;
    };
  }

  @EventHandler
  public void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event) {
    State current = state.get();
    ConfigurationGateDecision decision = configurationGateDecision(current);
    if (decision == ConfigurationGateDecision.IGNORE) {
      return;
    }
    long deadlineNanos = configurationDeadlineNanos(current);
    if (decision == ConfigurationGateDecision.WAIT_FOR_READY) {
      current = waitForConfigurationGate(current, deadlineNanos);
      decision = configurationGateDecision(current);
    }
    if (decision == ConfigurationGateDecision.IGNORE) {
      return;
    }
    if (decision == ConfigurationGateDecision.WAIT_FOR_READY
        || decision == ConfigurationGateDecision.DISCONNECT) {
      if (current.required()) {
        event.getConnection().disconnect(requiredPackFailureMessage());
      }
      return;
    }

    UUID playerId = event.getConnection().getProfile().getId();

    CompletableFuture<ResourcePackStatus> terminalStatus = new CompletableFuture<>();
    ResourcePackCallback callback =
        (packId, status, audience) -> {
          if (PACK_ID.equals(packId) && !status.intermediate()) {
            terminalStatus.complete(status);
          }
        };
    try {
      if (playerId != null) {
        configurationAttempts.put(playerId, current.sha1());
      }
      send(event.getConnection().getAudience(), current, callback);
    } catch (RuntimeException e) {
      if (playerId != null) {
        configurationAttempts.remove(playerId, current.sha1());
      }
      plugin
          .getLogger()
          .warning("Cannot send Exort resource pack during configuration: " + e.getMessage());
      if (current.required()) {
        event.getConnection().disconnect(requiredPackFailureMessage());
      }
      return;
    }

    ResourcePackStatus status = waitForTerminalStatus(terminalStatus, deadlineNanos);
    if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
      return;
    }
    if (playerId != null) {
      configurationAttempts.remove(playerId, current.sha1());
    }
    if (current.required()) {
      event.getConnection().disconnect(requiredPackFailureMessage());
    }
  }

  private long configurationDeadlineNanos(State current) {
    return System.nanoTime()
        + TimeUnit.SECONDS.toNanos(Math.max(1, current.configurationTimeoutSeconds()));
  }

  private State waitForConfigurationGate(State current, long deadlineNanos) {
    State latest = current;
    while (configurationGateDecision(latest) == ConfigurationGateDecision.WAIT_FOR_READY) {
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0) {
        return latest;
      }
      synchronized (stateMonitor) {
        latest = state.get();
        if (configurationGateDecision(latest) != ConfigurationGateDecision.WAIT_FOR_READY) {
          return latest;
        }
        remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
          return latest;
        }
        try {
          long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
          int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
          stateMonitor.wait(millis, nanos);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return latest;
        }
        latest = state.get();
      }
    }
    return latest;
  }

  private ResourcePackStatus waitForTerminalStatus(
      CompletableFuture<ResourcePackStatus> terminalStatus, long deadlineNanos) {
    try {
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0) {
        return null;
      }
      return terminalStatus.get(remainingNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (TimeoutException e) {
      return null;
    } catch (ExecutionException e) {
      ExortLog.warn("Resource-pack status callback failed: " + e.getMessage());
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
    Component parsed = richTextParser.apply(prompt);
    return parsed == null ? Component.empty() : parsed;
  }

  private Component requiredPackFailureMessage() {
    return Component.text(translator.tr("message.resource_pack.required_failure"));
  }

  private String displayNote(State current) {
    return displayNote(null, current);
  }

  private String displayNote(CommandSender sender, State current) {
    String note = current.note();
    if (note == null || !note.startsWith(PROVIDER_NOTE_PREFIX)) {
      return note;
    }
    String provider = note.substring(PROVIDER_NOTE_PREFIX.length());
    return translator.tr(sender, "message.pack_status.provider_note", provider);
  }

  private static boolean isDirectHosting(ResourcePackHosting hosting) {
    return hosting == ResourcePackHosting.EXORT
        || hosting == ResourcePackHosting.SELFHOST
        || hosting == ResourcePackHosting.LOBFILE;
  }

  private boolean isDispatchMetadataValid(String url, String sha1) {
    if (url == null || url.isBlank() || !isSha1(sha1)) {
      return false;
    }
    return isHttpUrl(url);
  }

  private boolean isHttpUrl(String url) {
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private boolean isHttpsUrl(String url) {
    try {
      URI uri = URI.create(url);
      return "https".equalsIgnoreCase(uri.getScheme());
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

  enum ConfigurationGateDecision {
    IGNORE,
    WAIT_FOR_READY,
    SEND_CONFIGURATION_PACK,
    DISCONNECT
  }

  enum OnlineSendDecision {
    SKIP,
    WAIT_FOR_READY,
    SEND_NOW
  }

  record State(
      String status,
      ResourcePackHosting configured,
      ResourcePackHosting effective,
      ResourcePackDelivery configuredDelivery,
      ResourcePackDelivery effectiveDelivery,
      PackExporter.Result pack,
      String handoffTarget,
      String url,
      String sha1,
      String note,
      String error,
      boolean dispatchReady,
      boolean configurationGate,
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
          null,
          note,
          null,
          false,
          false,
          false,
          null,
          30,
          false);
    }

    static State starting(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        DeliverySettings settings,
        String note) {
      return new State(
          "STARTING",
          configured,
          effective,
          settings.configuredDelivery(),
          ResourcePackDelivery.MANUAL,
          null,
          null,
          null,
          null,
          note,
          null,
          false,
          true,
          settings.required(),
          settings.prompt(),
          settings.configurationTimeoutSeconds(),
          settings.sendOnlineOnReady());
    }

    static State uploading(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        DeliverySettings settings,
        PackExporter.Result pack) {
      return new State(
          "UPLOADING",
          configured,
          effective,
          settings.configuredDelivery(),
          ResourcePackDelivery.MANUAL,
          pack,
          null,
          null,
          pack.outputSha1(),
          null,
          null,
          false,
          configurationGateEnabled(
              effective, settings.configuredDelivery(), settings.serverResourcePackConfigured()),
          settings.required(),
          settings.prompt(),
          settings.configurationTimeoutSeconds(),
          settings.sendOnlineOnReady());
    }

    static State resolving(ResourcePackHosting configured, DeliverySettings settings) {
      return new State(
          "RESOLVING",
          configured,
          ResourcePackHosting.EXORT,
          settings.configuredDelivery(),
          ResourcePackDelivery.MANUAL,
          null,
          null,
          null,
          null,
          "Resolving official Exort resource-pack metadata",
          null,
          false,
          configurationGateEnabled(
              ResourcePackHosting.EXORT,
              settings.configuredDelivery(),
              settings.serverResourcePackConfigured()),
          settings.required(),
          settings.prompt(),
          settings.configurationTimeoutSeconds(),
          settings.sendOnlineOnReady());
    }

    static State ready(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        ResourcePackDelivery configuredDelivery,
        ResourcePackDelivery effectiveDelivery,
        PackExporter.Result pack,
        String handoffTarget,
        String url,
        String sha1,
        String note,
        boolean dispatchReady,
        boolean configurationGate,
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
          handoffTarget,
          url,
          sha1,
          note,
          null,
          dispatchReady,
          configurationGate,
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
        String handoffTarget,
        String url,
        String sha1,
        String note,
        boolean configurationGate,
        boolean required,
        Component prompt,
        int configurationTimeoutSeconds,
        boolean sendOnlineOnReady) {
      return new State(
          "ERROR",
          configured,
          effective,
          configuredDelivery,
          effectiveDelivery,
          pack,
          handoffTarget,
          url,
          sha1,
          note,
          error,
          false,
          configurationGate,
          required,
          prompt,
          configurationTimeoutSeconds,
          sendOnlineOnReady);
    }
  }
}
