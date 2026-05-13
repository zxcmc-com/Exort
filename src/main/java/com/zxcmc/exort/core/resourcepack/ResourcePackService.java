package com.zxcmc.exort.core.resourcepack;

import com.zxcmc.exort.core.ExortPlugin;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ResourcePackService implements Listener {
  private static final UUID PACK_ID =
      UUID.nameUUIDFromBytes("zxcmc:exort:resource-pack".getBytes(StandardCharsets.UTF_8));

  private final ExortPlugin plugin;
  private final SelfHostPackServer selfHost;
  private final AtomicReference<State> state =
      new AtomicReference<>(State.disabled("Not started", ResourcePackHosting.AUTO));

  public ResourcePackService(ExortPlugin plugin) {
    this.plugin = plugin;
    this.selfHost = new SelfHostPackServer(plugin);
  }

  public synchronized void reload() {
    selfHost.stop();
    NexoPackBridge.removeIfPresent(plugin);
    ResourcePackHosting configured =
        ResourcePackHosting.fromConfig(
            plugin.getConfig().getString("resourcePack.hosting", "AUTO"));
    if (!plugin.isResourceMode()) {
      state.set(State.disabled("Effective mode is VANILLA", configured));
      return;
    }
    if (configured == ResourcePackHosting.DISABLED) {
      state.set(State.disabled("resourcePack.hosting is DISABLED", configured));
      return;
    }
    boolean obfuscate = plugin.getConfig().getBoolean("resourcePack.obfuscation", true);
    PackExporter.Result pack = PackExporter.exportPack(plugin, obfuscate);
    if (!pack.available()) {
      state.set(State.error(configured, configured, "Pack export failed", pack, null, null));
      return;
    }
    ResourcePackHosting effective =
        configured == ResourcePackHosting.AUTO
            ? (NexoPackBridge.isNexoEnabled(plugin)
                ? ResourcePackHosting.NEXO
                : ResourcePackHosting.SELFHOST)
            : configured;
    if (effective == ResourcePackHosting.NEXO) {
      if (!NexoPackBridge.copyIfPresent(plugin, pack.rawFile())) {
        state.set(State.error(configured, effective, "Nexo is not available", pack, null, null));
        plugin.getLogger().warning("Resource-pack hosting is NEXO, but Nexo is not enabled.");
        return;
      }
      setReady(configured, effective, pack, null, "Managed by Nexo");
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
        setReady(configured, effective, pack, url, null);
      } catch (IOException e) {
        state.set(State.error(configured, effective, e.getMessage(), pack, null, null));
        plugin.getLogger().warning("Failed to start resource-pack self-hosting: " + e.getMessage());
      }
      return;
    }
    if (effective == ResourcePackHosting.LOBFILE) {
      String apiKey = readLobFileApiKey();
      if (apiKey == null || apiKey.isBlank()) {
        state.set(
            State.error(configured, effective, "LobFile API key is missing", pack, null, null));
        plugin
            .getLogger()
            .warning(
                "Resource-pack hosting is LOBFILE, but plugins/Exort/secret.yml has no"
                    + " lobfile.apiKey.");
        return;
      }
      state.set(State.uploading(configured, effective, pack));
      Bukkit.getScheduler()
          .runTaskAsynchronously(
              plugin,
              () -> {
                try {
                  String url = LobFileUploader.upload(pack.outputFile(), pack.outputSha1(), apiKey);
                  setReady(configured, effective, pack, url, null);
                } catch (IOException | RuntimeException e) {
                  state.set(State.error(configured, effective, e.getMessage(), pack, null, null));
                  plugin
                      .getLogger()
                      .warning("LobFile resource-pack upload failed: " + e.getMessage());
                }
              });
    }
  }

  public synchronized void stop() {
    selfHost.stop();
    state.set(State.disabled("Stopped", ResourcePackHosting.AUTO));
  }

  private void setReady(
      ResourcePackHosting configured,
      ResourcePackHosting effective,
      PackExporter.Result pack,
      String url,
      String note) {
    State ready = State.ready(configured, effective, pack, url, note);
    state.set(ready);
    logReadyState(ready);
    if (ready.dispatchReady()) {
      scheduleSendAll();
    }
  }

  private void logReadyState(State ready) {
    if (ready.effective() == ResourcePackHosting.NEXO) {
      plugin.getLogger().info("Resource-pack delivery is managed by Nexo.");
      return;
    }
    if (ready.dispatchReady()) {
      plugin
          .getLogger()
          .info("Resource-pack delivery is ready via " + ready.effective() + ": " + ready.url());
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
    ResourcePackInfo info =
        ResourcePackInfo.resourcePackInfo(
            PACK_ID, URI.create(current.url()), current.pack().outputSha1());
    ResourcePackRequest request =
        ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .replace(false)
            .required(plugin.getConfig().getBoolean("resourcePack.required", true))
            .prompt(prompt())
            .build();
    player.sendResourcePacks(request);
    return true;
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
    if (current.pack() != null && current.pack().available()) {
      lines.add("Raw pack: " + current.pack().rawFile().getPath());
      lines.add("Pack: " + current.pack().outputFile().getPath());
      lines.add("SHA-1: " + current.pack().outputSha1());
      lines.add("Obfuscated: " + current.pack().obfuscated());
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
    return current.status();
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    if (!state.get().dispatchReady()) {
      return;
    }
    Bukkit.getScheduler().runTaskLater(plugin, () -> send(event.getPlayer()), 20L);
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

  private String readLobFileApiKey() {
    File secretFile = new File(plugin.getDataFolder(), "secret.yml");
    if (!secretFile.isFile()) {
      return "";
    }
    YamlConfiguration secret = YamlConfiguration.loadConfiguration(secretFile);
    return secret.getString("lobfile.apiKey", secret.getString("lobfile.api_key", ""));
  }

  private record State(
      String status,
      ResourcePackHosting configured,
      ResourcePackHosting effective,
      PackExporter.Result pack,
      String url,
      String note,
      String error,
      boolean dispatchReady) {
    static State disabled(String note, ResourcePackHosting configured) {
      return new State(
          "DISABLED", configured, ResourcePackHosting.DISABLED, null, null, note, null, false);
    }

    static State uploading(
        ResourcePackHosting configured, ResourcePackHosting effective, PackExporter.Result pack) {
      return new State("UPLOADING", configured, effective, pack, null, null, null, false);
    }

    static State ready(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        PackExporter.Result pack,
        String url,
        String note) {
      boolean dispatchReady =
          effective == ResourcePackHosting.SELFHOST || effective == ResourcePackHosting.LOBFILE;
      return new State("READY", configured, effective, pack, url, note, null, dispatchReady);
    }

    static State error(
        ResourcePackHosting configured,
        ResourcePackHosting effective,
        String error,
        PackExporter.Result pack,
        String url,
        String note) {
      return new State("ERROR", configured, effective, pack, url, note, error, false);
    }
  }
}
