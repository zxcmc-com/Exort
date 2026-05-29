package com.zxcmc.exort.display;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;

final class ClientCullingTranslationProbe implements Listener {
  private static final int MAX_SIGN_LINES = 4;
  private static final int SIGN_BACK_OFFSET_BLOCKS = 5;
  private static final int SIGN_DOWN_OFFSET_BLOCKS = 5;
  private static final String MARKER_PREFIX = "[EXC";

  private final JavaPlugin plugin;
  private final DisplayCullingConfig.TranslationProbeConfig config;
  private final BiFunction<Player, String, DisplayCullingService.ClientCullingProbeStatus>
      cacheStatusProvider;
  private final BiConsumer<Player, DisplayCullingService.ClientCullingProbeStatus> resultConsumer;
  private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, ProbeSession> sessions = new ConcurrentHashMap<>();
  private volatile boolean running;

  ClientCullingTranslationProbe(
      JavaPlugin plugin,
      DisplayCullingConfig.TranslationProbeConfig config,
      BiFunction<Player, String, DisplayCullingService.ClientCullingProbeStatus>
          cacheStatusProvider,
      BiConsumer<Player, DisplayCullingService.ClientCullingProbeStatus> resultConsumer) {
    this.plugin = plugin;
    this.config = config;
    this.cacheStatusProvider = cacheStatusProvider;
    this.resultConsumer = resultConsumer;
  }

  void start() {
    if (running || !config.enabled()) {
      return;
    }
    running = true;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  void stop() {
    running = false;
    HandlerList.unregisterAll(this);
    for (ProbeSession session : List.copyOf(sessions.values())) {
      Player player = Bukkit.getPlayer(session.playerId());
      if (player != null && player.isOnline()) {
        restoreClientBlock(player, session.signLocation());
      }
    }
    sessions.clear();
    queuedPlayers.clear();
  }

  void schedule(Player player) {
    if (!running || player == null || !player.isOnline() || !config.enabled()) {
      return;
    }
    UUID playerId = player.getUniqueId();
    if (sessions.containsKey(playerId) || !queuedPlayers.add(playerId)) {
      return;
    }
    publish(
        player,
        DisplayCullingService.ClientCullingProbeStatus.pending(
            safeBrand(player), "scheduled translation probe"));
    scheduleAttempt(playerId, 1, config.joinDelayTicks());
  }

  void cancel(UUID playerId) {
    if (playerId == null) {
      return;
    }
    queuedPlayers.remove(playerId);
    ProbeSession session = sessions.remove(playerId);
    if (session == null) {
      return;
    }
    Player player = Bukkit.getPlayer(playerId);
    if (player != null && player.isOnline()) {
      restoreClientBlock(player, session.signLocation());
    }
  }

  private void scheduleAttempt(UUID playerId, int attempt, int delayTicks) {
    Bukkit.getScheduler()
        .runTaskLater(plugin, () -> runAttempt(playerId, attempt), Math.max(1L, delayTicks));
  }

  private void runAttempt(UUID playerId, int attempt) {
    if (!running || !queuedPlayers.contains(playerId)) {
      return;
    }
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      queuedPlayers.remove(playerId);
      return;
    }

    String brand = safeBrand(player);
    if (config.requireModdedBrand() && isUnknownBrand(brand)) {
      retryOrFinish(
          player,
          attempt,
          DisplayCullingService.ClientCullingProbeStatus.pending(
              brand, "waiting for client brand"));
      return;
    }
    if (config.requireModdedBrand() && isVanillaBrand(brand)) {
      queuedPlayers.remove(playerId);
      publish(
          player,
          DisplayCullingService.ClientCullingProbeStatus.skipped(brand, "vanilla client brand"));
      return;
    }
    DisplayCullingService.ClientCullingProbeStatus cachedStatus = cachedStatus(player, brand);
    if (cachedStatus != null) {
      queuedPlayers.remove(playerId);
      publish(player, cachedStatus);
      return;
    }
    if (!canProbeWithOpenInventory(player)) {
      retryOrFinish(
          player,
          attempt,
          DisplayCullingService.ClientCullingProbeStatus.pending(
              brand, "waiting for idle player inventory " + openInventorySummary(player)));
      return;
    }

    queuedPlayers.remove(playerId);
    openProbe(player, brand, matchesBrand(brand));
  }

  private void retryOrFinish(
      Player player, int attempt, DisplayCullingService.ClientCullingProbeStatus pendingStatus) {
    publish(player, pendingStatus);
    if (attempt >= config.maxAttempts()) {
      queuedPlayers.remove(player.getUniqueId());
      publish(
          player,
          DisplayCullingService.ClientCullingProbeStatus.skipped(
              pendingStatus.brand(), pendingStatus.detail()));
      return;
    }
    scheduleAttempt(player.getUniqueId(), attempt + 1, config.retryDelayTicks());
  }

  private boolean matchesBrand(String brand) {
    if (isUnknownBrand(brand) || isVanillaBrand(brand)) {
      return false;
    }
    String lower = brand.toLowerCase(java.util.Locale.ROOT);
    for (String token : config.brandTokens()) {
      if (lower.contains(token)) {
        return true;
      }
    }
    return false;
  }

  private DisplayCullingService.ClientCullingProbeStatus cachedStatus(Player player, String brand) {
    return cacheStatusProvider == null ? null : cacheStatusProvider.apply(player, brand);
  }

  private void openProbe(Player player, String brand, boolean knownModdedBrand) {
    List<String> keys = config.translationKeys().stream().limit(MAX_SIGN_LINES).toList();
    if (keys.isEmpty()) {
      publish(
          player,
          DisplayCullingService.ClientCullingProbeStatus.error(
              brand, null, null, "no translation keys configured"));
      return;
    }

    BlockFace facing = behindFace(player);
    Location signLocation = signLocation(player, facing);
    BlockData signBlockData = Material.OAK_WALL_SIGN.createBlockData();
    if (signBlockData instanceof WallSign wallSign) {
      wallSign.setFacing(facing);
    }
    player.sendBlockChange(signLocation, signBlockData);
    if (!(signBlockData.createBlockState() instanceof Sign virtualSign)) {
      restoreClientBlock(player, signLocation);
      publish(
          player,
          DisplayCullingService.ClientCullingProbeStatus.error(
              brand, null, null, "could not create virtual sign state"));
      return;
    }

    Side side = Side.BACK;
    List<String> markers = new ArrayList<>(keys.size());
    for (int i = 0; i < MAX_SIGN_LINES; i++) {
      if (i >= keys.size()) {
        virtualSign.getSide(side).line(i, Component.empty());
        continue;
      }
      String marker = marker(i);
      markers.add(marker);
      virtualSign
          .getSide(side)
          .line(i, Component.text(marker + " ").append(Component.translatable(keys.get(i))));
    }
    player.sendBlockUpdate(signLocation, virtualSign);

    ProbeSession session =
        new ProbeSession(
            player.getUniqueId(), brand, signLocation, side, List.copyOf(keys), markers);
    sessions.put(player.getUniqueId(), session);
    String brandDetail = knownModdedBrand ? "" : " unlisted-brand";
    publish(
        player,
        DisplayCullingService.ClientCullingProbeStatus.pending(
            brand, "virtual sign opened" + brandDetail + " " + openInventorySummary(player)));

    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> openVirtualSign(player.getUniqueId(), session),
            Math.max(1L, config.openDelayTicks()));
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> timeout(player.getUniqueId(), session),
            Math.max(config.openDelayTicks() + 1L, config.timeoutTicks()));
  }

  private void openVirtualSign(UUID playerId, ProbeSession expected) {
    if (!running || sessions.get(playerId) != expected) {
      return;
    }
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      sessions.remove(playerId, expected);
      return;
    }
    Location location = expected.signLocation();
    player.openVirtualSign(
        Position.block(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
        expected.side());
    player.closeInventory();
  }

  private void timeout(UUID playerId, ProbeSession expected) {
    if (!running || !sessions.remove(playerId, expected)) {
      return;
    }
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      return;
    }
    restoreClientBlock(player, expected.signLocation());
    publish(
        player,
        DisplayCullingService.ClientCullingProbeStatus.error(
            expected.brand(), null, null, "virtual sign response timed out"));
  }

  @EventHandler
  public void onUncheckedSignChange(UncheckedSignChangeEvent event) {
    if (event == null || event.getPlayer() == null) {
      return;
    }
    Player player = event.getPlayer();
    ProbeSession session = sessions.get(player.getUniqueId());
    if (session == null || !sameBlock(event, session.signLocation())) {
      return;
    }
    sessions.remove(player.getUniqueId(), session);
    event.setCancelled(true);
    restoreClientBlock(player, session.signLocation());

    List<String> lines = plainLines(event);
    ProbeMatch match = findMatch(session, lines);
    if (match != null) {
      publish(
          player,
          DisplayCullingService.ClientCullingProbeStatus.match(
              session.brand(), match.key(), match.response()));
      return;
    }
    publish(
        player,
        DisplayCullingService.ClientCullingProbeStatus.noMatch(
            session.brand(), lineSummary(lines), "no EntityCulling translation key changed"));
  }

  private ProbeMatch findMatch(ProbeSession session, List<String> lines) {
    for (int i = 0; i < session.keys().size() && i < lines.size(); i++) {
      String key = session.keys().get(i);
      String line = lines.get(i) == null ? "" : lines.get(i).trim();
      String marker = session.markers().get(i);
      if (line.startsWith(marker) && !line.contains(key)) {
        return new ProbeMatch(key, line);
      }
    }
    return null;
  }

  private static List<String> plainLines(UncheckedSignChangeEvent event) {
    PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
    List<String> out = new ArrayList<>();
    for (Component line : event.lines()) {
      out.add(serializer.serialize(line));
    }
    return out;
  }

  private static boolean sameBlock(UncheckedSignChangeEvent event, Location location) {
    return event.getEditedBlockPosition().blockX() == location.getBlockX()
        && event.getEditedBlockPosition().blockY() == location.getBlockY()
        && event.getEditedBlockPosition().blockZ() == location.getBlockZ();
  }

  private static String marker(int index) {
    return MARKER_PREFIX + index + "]";
  }

  private static String lineSummary(List<String> lines) {
    if (lines == null || lines.isEmpty()) {
      return "";
    }
    List<String> out = new ArrayList<>();
    for (int i = 0; i < lines.size() && i < MAX_SIGN_LINES; i++) {
      String line = lines.get(i);
      if (line != null && !line.isBlank()) {
        out.add(line);
      }
    }
    return String.join(" | ", out);
  }

  private static String safeBrand(Player player) {
    String brand = player.getClientBrandName();
    return brand == null || brand.isBlank() ? "unknown" : brand.trim();
  }

  private static boolean isUnknownBrand(String brand) {
    return brand == null || brand.isBlank() || "unknown".equalsIgnoreCase(brand);
  }

  private static boolean isVanillaBrand(String brand) {
    return "vanilla".equalsIgnoreCase(brand);
  }

  private static BlockFace behindFace(Player player) {
    return switch (player.getFacing()) {
      case NORTH -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.NORTH;
      case EAST -> BlockFace.WEST;
      case WEST -> BlockFace.EAST;
      default -> BlockFace.SOUTH;
    };
  }

  private static Location signLocation(Player player, BlockFace facing) {
    Location location =
        player.getLocation().getBlock().getRelative(facing, SIGN_BACK_OFFSET_BLOCKS).getLocation();
    int minY = player.getWorld().getMinHeight();
    location.setY(Math.max(minY, location.getBlockY() - SIGN_DOWN_OFFSET_BLOCKS));
    return location;
  }

  private static boolean canProbeWithOpenInventory(Player player) {
    InventoryType type = player.getOpenInventory().getType();
    return type == InventoryType.CRAFTING
        || type == InventoryType.PLAYER
        || type == InventoryType.CREATIVE;
  }

  private static String openInventorySummary(Player player) {
    InventoryView view = player.getOpenInventory();
    return "view="
        + inventoryTypeName(view.getType())
        + " top="
        + inventoryTypeName(view.getTopInventory().getType())
        + " bottom="
        + inventoryTypeName(view.getBottomInventory().getType());
  }

  private static String inventoryTypeName(InventoryType type) {
    return type == null ? "unknown" : type.name().toLowerCase(java.util.Locale.ROOT);
  }

  private void restoreClientBlock(Player player, Location location) {
    if (player == null || !player.isOnline() || location == null) {
      return;
    }
    player.sendBlockChange(location, location.getBlock().getBlockData());
  }

  private void publish(Player player, DisplayCullingService.ClientCullingProbeStatus status) {
    resultConsumer.accept(player, status);
  }

  private record ProbeSession(
      UUID playerId,
      String brand,
      Location signLocation,
      Side side,
      List<String> keys,
      List<String> markers) {}

  private record ProbeMatch(String key, String response) {}
}
