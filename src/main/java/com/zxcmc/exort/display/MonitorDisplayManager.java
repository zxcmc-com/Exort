package com.zxcmc.exort.display;

import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.ItemKeyUtil;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.DisplayMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.MarkerCoords;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Display manager for monitors (base model + item/text overlays).
 */
public class MonitorDisplayManager extends BaseCarrierDisplayManager {
    public record ScreenConfig(double offsetX, double offsetY, double offsetZ, double scale) {
    }

    private static final String TAG_ITEM = DisplayTags.MONITOR_ITEM_TAG;
    private static final String TAG_TEXT = DisplayTags.MONITOR_TEXT_TAG;
    private static final DecimalFormat COMPACT_DECIMAL = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final NamespacedKey NEXO_FURNITURE = NamespacedKey.fromString("nexo:furniture");
    private static final BlockFace[] FULL_FACES = new BlockFace[]{
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private static final EnumSet<Material> THIN_BLOCK_OVERRIDES = EnumSet.of(
        Material.AMETHYST_CLUSTER,
        Material.SMALL_AMETHYST_BUD,
        Material.MEDIUM_AMETHYST_BUD,
        Material.LARGE_AMETHYST_BUD
    );

    private final StorageKeys keys;
    private final StorageManager storageManager;
    private final int wireLimit;
    private final int wireHardCap;
    private final Material wireMaterial;
    private final Material storageCarrier;
    private final String monitorName;
    private final String enabledModel;
    private final String disabledModel;
    private final ScreenConfig itemConfig;
    private final ScreenConfig blockConfig;
    private final ScreenConfig thinBlockConfig;
    private final ScreenConfig horizontalBlockConfig;
    private final ScreenConfig fullBlockConfig;
    private final ScreenConfig textConfig;
    private final ScreenConfig textEmptyConfig;
    private final int textBackgroundAlpha;
    private final Map<MonitorPos, MonitorState> monitors = new HashMap<>();
    private final Map<String, Long> loadAttempts = new HashMap<>();
    private int taskId = -1;
    private final ThreadLocal<TerminalLinkFinder.StorageSearchResult> currentLink = new ThreadLocal<>();
    private static final long LOAD_COOLDOWN_MS = 1000L;

    public MonitorDisplayManager(Plugin plugin,
                                 StorageKeys keys,
                                 StorageManager storageManager,
                                 Material carrierMaterial,
                                 String enabledModel,
                                 String disabledModel,
                                 Material displayBaseMaterial,
                                 double displayScale,
                                 double offsetX,
                                 double offsetY,
                                 double offsetZ,
                                 String monitorName,
                                 int wireLimit,
                                 int wireHardCap,
                                 Material wireMaterial,
                                 Material storageCarrier,
                                 ScreenConfig itemConfig,
                                 ScreenConfig blockConfig,
                                 ScreenConfig thinBlockConfig,
                                 ScreenConfig horizontalBlockConfig,
                                 ScreenConfig fullBlockConfig,
                                 ScreenConfig textConfig,
                                 ScreenConfig textEmptyConfig,
                                 int textBackgroundAlpha) {
        super(plugin, carrierMaterial, enabledModel, displayBaseMaterial, displayScale, offsetX, offsetY, offsetZ, "monitor");
        this.keys = keys;
        this.storageManager = storageManager;
        this.wireLimit = wireLimit;
        this.wireHardCap = wireHardCap;
        this.wireMaterial = wireMaterial;
        this.storageCarrier = storageCarrier;
        this.monitorName = monitorName == null ? "" : monitorName;
        this.enabledModel = enabledModel == null ? "" : enabledModel;
        this.disabledModel = disabledModel == null ? this.enabledModel : disabledModel;
        this.itemConfig = itemConfig;
        this.blockConfig = blockConfig;
        this.thinBlockConfig = thinBlockConfig == null ? blockConfig : thinBlockConfig;
        this.horizontalBlockConfig = horizontalBlockConfig == null ? blockConfig : horizontalBlockConfig;
        this.fullBlockConfig = fullBlockConfig == null ? blockConfig : fullBlockConfig;
        this.textConfig = textConfig;
        this.textEmptyConfig = textEmptyConfig == null ? textConfig : textEmptyConfig;
        this.textBackgroundAlpha = Math.max(0, Math.min(255, textBackgroundAlpha));
    }

    public void start() {
        if (taskId != -1) return;
        scanLoadedChunks();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        clearAll();
    }

    private void clearAll() {
        for (MonitorPos pos : new HashMap<>(monitors).keySet()) {
            World world = Bukkit.getWorld(pos.world());
            if (world == null) continue;
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            removeContent(block);
            removeDisplay(block);
        }
        monitors.clear();
    }

    public void registerMonitor(Block block) {
        monitors.put(MonitorPos.of(block), new MonitorState(null, null, Long.MIN_VALUE, null, null, false, null));
        refresh(block);
    }

    public void unregisterMonitor(Block block) {
        MonitorPos pos = MonitorPos.of(block);
        monitors.remove(pos);
        removeContent(block);
        removeDisplay(block);
    }

    public void refreshChunk(org.bukkit.Chunk chunk) {
        scanChunkMarkers(chunk);
    }

    public void scanLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                scanChunkMarkers(chunk);
            }
        }
    }

    private void scanChunkMarkers(org.bukkit.Chunk chunk) {
        var keys = chunk.getPersistentDataContainer().getKeys();
        if (keys.isEmpty()) return;
        for (NamespacedKey k : keys) {
            if (!k.getNamespace().equals(plugin.getName().toLowerCase())) continue;
            String raw = k.getKey();
            if (!raw.startsWith("monitor_")) continue;
            if (raw.startsWith("monitor_display_") || raw.startsWith("monitor_item_") || raw.startsWith("monitor_item_blob_")) {
                continue;
            }
            int[] xyz = MarkerCoords.parseXYZ(raw.substring("monitor_".length()));
            if (xyz == null) continue;
            Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
            if (!Carriers.matchesCarrier(block, carrierMaterial)) continue;
            if (!MonitorMarker.isMonitor(plugin, block)) continue;
            monitors.putIfAbsent(MonitorPos.of(block), new MonitorState(null, null, Long.MIN_VALUE, null, null, false, null));
            refresh(block);
        }
    }

    private void tick() {
        for (var entry : new HashMap<>(monitors).entrySet()) {
            MonitorPos pos = entry.getKey();
            World world = Bukkit.getWorld(pos.world());
            if (world == null) {
                monitors.remove(pos);
                continue;
            }
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                removeContent(world.getBlockAt(pos.x(), pos.y(), pos.z()));
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (!isValidBlock(block)) {
                unregisterMonitor(block);
                continue;
            }
            refresh(block);
        }
    }

    @Override
    public void refresh(Block block) {
        if (!isValidBlock(block)) {
            removeDisplay(block);
            removeContent(block);
            monitors.remove(MonitorPos.of(block));
            return;
        }
        TerminalLinkFinder.StorageSearchResult link = resolveLink(block);
        currentLink.set(link);
        try {
            String modelId = link.count() == 1 && link.data() != null ? enabledModel : disabledModel;
            MonitorPos pos = MonitorPos.of(block);
            MonitorState prev = monitors.get(pos);
            if (needsBaseRefresh(block, modelId, prev)) {
                super.refresh(block);
            }
            updateContent(block, link, modelId, prev);
        } finally {
            currentLink.remove();
        }
    }

    @Override
    public void removeDisplay(Block block) {
        super.removeDisplay(block);
        removeContent(block);
    }

    @Override
    protected boolean isValidBlock(Block block) {
        return Carriers.matchesCarrier(block, carrierMaterial) && MonitorMarker.isMonitor(plugin, block);
    }

    @Override
    protected void decorateMeta(ItemMeta meta, Block block) {
        if (!monitorName.isEmpty()) {
            meta.displayName(net.kyori.adventure.text.Component.text(monitorName).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
    }

    @Override
    protected String modelId(Block block) {
        TerminalLinkFinder.StorageSearchResult link = currentLink.get();
        if (link == null) {
            link = resolveLink(block);
        }
        boolean active = link.count() == 1 && link.data() != null;
        return active ? enabledModel : disabledModel;
    }

    @Override
    protected void applyTransform(ItemDisplay display, Block block) {
        Transformation t = display.getTransformation();
        t.getScale().set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
        t.getLeftRotation().set(DisplayRotation.rotationForFacing(facingFor(block)));
        t.getRightRotation().identity();
        if (!monitorName.isEmpty()) {
            display.customName(net.kyori.adventure.text.Component.text(monitorName));
            display.setCustomNameVisible(false);
        } else {
            display.customName(null);
        }
        display.setTransformation(t);
    }

    private TerminalLinkFinder.StorageSearchResult resolveLink(Block block) {
        return TerminalLinkFinder.find(block, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
    }

    private boolean needsBaseRefresh(Block block, String modelId, MonitorState prev) {
        UUID existingId = DisplayMarker.get(plugin, "monitor", block).orElse(null);
        if (existingId == null) {
            return true;
        }
        var ent = Bukkit.getEntity(existingId);
        if (!(ent instanceof ItemDisplay display) || display.isDead()) {
            return true;
        }
        return prev == null || prev.lastModelId() == null || !prev.lastModelId().equals(modelId);
    }

    private void updateContent(Block block, TerminalLinkFinder.StorageSearchResult link, String modelId, MonitorState prev) {
        boolean active = link.count() == 1 && link.data() != null;
        MonitorPos pos = MonitorPos.of(block);
        if (!active) {
            monitors.put(pos, new MonitorState(null, null, Long.MIN_VALUE, modelId, null, false, null));
            removeContent(block);
            return;
        }
        String storageId = link.data().storageId();
        StorageTier tier = link.data().tier();
        ensureCacheLoaded(storageId);
        Optional<String> itemKeyOpt = MonitorMarker.itemKey(plugin, block);
        Optional<byte[]> itemBlobOpt = MonitorMarker.itemBlob(plugin, block);
        if (itemKeyOpt.isEmpty() || itemBlobOpt.isEmpty()) {
            updateEmptyText(block, storageId, tier, modelId, prev);
            return;
        }
        byte[] blob = itemBlobOpt.get();
        ItemStack sample = ItemKeyUtil.deserialize(blob);
        if (sample == null || sample.getType() == Material.AIR) {
            updateEmptyText(block, storageId, tier, modelId, prev);
            return;
        }
        String itemKey = itemKeyOpt.get();

        ScreenConfig activeItemConfig = screenConfigFor(sample);
        boolean horizontal = activeItemConfig == horizontalBlockConfig;
        ItemDisplay.ItemDisplayTransform transform = displayTransformFor(sample);
        boolean itemChanged = prev == null || prev.itemKey() == null || !itemKey.equals(prev.itemKey());
        boolean configChanged = prev == null
                || prev.lastItemConfig() == null
                || prev.lastItemConfig() != activeItemConfig
                || prev.lastHorizontal() != horizontal
                || prev.lastTransform() != transform;
        ItemDisplay itemDisplay = findItemDisplay(block);
        if (itemDisplay == null || itemDisplay.isDead()) {
            itemDisplay = spawnItemDisplay(block, sample, activeItemConfig, horizontal, transform);
            orientDisplay(itemDisplay, block, activeItemConfig.scale(), horizontal);
        } else if (itemChanged || configChanged) {
            applyItemSettings(itemDisplay, activeItemConfig.scale(), transform);
            itemDisplay.setItemStack(ItemKeyUtil.cloneSample(sample));
            itemDisplay.teleport(targetScreenLoc(block, activeItemConfig));
            orientDisplay(itemDisplay, block, activeItemConfig.scale(), horizontal);
        }

        TextDisplay textDisplay = findTextDisplay(block);
        long amount = resolveAmount(storageId, itemKey, block);
        boolean amountChanged = itemChanged || prev == null || prev.lastAmount() != amount;
        if (textDisplay == null || textDisplay.isDead()) {
            String countText = formatCount(amount);
            textDisplay = spawnTextDisplay(block, textConfig, net.kyori.adventure.text.Component.text(countText));
        } else if (amountChanged) {
            String countText = formatCount(amount);
            applyTextSettings(textDisplay, textConfig.scale());
            textDisplay.text(net.kyori.adventure.text.Component.text(countText));
            textDisplay.teleport(targetScreenLoc(block, textConfig));
            orientDisplay(textDisplay, block, textConfig.scale(), false);
        }
        monitors.put(pos, new MonitorState(storageId, itemKey, amount, modelId, activeItemConfig, horizontal, transform));
    }

    private void updateEmptyText(Block block, String storageId, StorageTier tier, String modelId, MonitorState prev) {
        MonitorPos pos = MonitorPos.of(block);
        if (storageId == null || tier == null) {
            monitors.put(pos, new MonitorState(null, null, Long.MIN_VALUE, modelId, null, false, null));
            removeContent(block);
            return;
        }
        removeItemDisplay(block);
        long total = resolveStorageTotal(storageId, block, tier);
        long max = Math.max(1, tier.maxItems());
        double progress = Math.min(1.0, Math.max(0.0, (double) total / (double) max));
        double free = 1.0 - progress;
        int percentValue = (int) Math.round(progress * 100.0);
        Component text = Component.text(percentValue + "%").color(freeColor(free));

        TextDisplay textDisplay = findTextDisplay(block);
        boolean amountChanged = true;
        if (prev != null && prev.itemKey() == null && prev.lastAmount() == total) {
            amountChanged = false;
        }
        if (textDisplay == null || textDisplay.isDead()) {
            textDisplay = spawnTextDisplay(block, textEmptyConfig, text);
        } else if (amountChanged) {
            applyTextSettings(textDisplay, textEmptyConfig.scale());
            textDisplay.text(text);
            textDisplay.teleport(targetScreenLoc(block, textEmptyConfig));
            orientDisplay(textDisplay, block, textEmptyConfig.scale(), false);
        }
        monitors.put(pos, new MonitorState(storageId, null, total, modelId, null, false, null));
    }

    @Override
    protected double cleanupRadius() {
        return 0.25;
    }

    private long resolveAmount(String storageId, String itemKey, Block block) {
        Optional<StorageCache> cacheOpt = storageManager.peekLoadedCache(storageId);
        if (cacheOpt.isPresent()) {
            return cacheOpt.get().peekAmount(itemKey);
        }
        MonitorState prev = monitors.get(MonitorPos.of(block));
        if (prev != null && itemKey != null && itemKey.equals(prev.itemKey())) {
            return prev.lastAmount();
        }
        return 0L;
    }

    private long resolveStorageTotal(String storageId, Block block, StorageTier tier) {
        Optional<StorageCache> cacheOpt = storageManager.peekLoadedCache(storageId);
        if (cacheOpt.isPresent()) {
            return cacheOpt.get().peekEffectiveTotal();
        }
        MonitorState prev = monitors.get(MonitorPos.of(block));
        if (prev != null && prev.itemKey() == null && storageId != null && storageId.equals(prev.storageId())) {
            return prev.lastAmount();
        }
        return 0L;
    }

    private ItemDisplay spawnItemDisplay(Block block, ItemStack sample, ScreenConfig config, boolean horizontal, ItemDisplay.ItemDisplayTransform transform) {
        Location loc = targetScreenLoc(block, config);
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, item -> {
            applyItemSettings(item, config.scale(), transform);
            item.setItemStack(ItemKeyUtil.cloneSample(sample));
            orientDisplay(item, block, config.scale(), horizontal);
        });
        DisplayMarker.set(plugin, "monitor_item", block, display.getUniqueId());
        return display;
    }

    private TextDisplay spawnTextDisplay(Block block, ScreenConfig config, net.kyori.adventure.text.Component text) {
        Location loc = targetScreenLoc(block, config);
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, display1 -> {
            applyTextSettings(display1, config.scale());
            display1.text(text);
            orientDisplay(display1, block, config.scale(), false);
        });
        DisplayMarker.set(plugin, "monitor_text", block, display.getUniqueId());
        return display;
    }

    private void applyItemSettings(ItemDisplay item, double scale, ItemDisplay.ItemDisplayTransform transform) {
        item.setPersistent(true);
        item.setInvulnerable(true);
        item.setSilent(true);
        item.setInvisible(true);
        item.setBillboard(Display.Billboard.FIXED);
        item.setBrightness(new Display.Brightness(15, 15));
        item.setItemDisplayTransform(transform);
        item.setDisplayWidth(0.0f);
        item.setDisplayHeight(0.0f);
        item.setViewRange(1.0f);
        item.setTeleportDuration(0);
        item.addScoreboardTag(DisplayTags.DISPLAY_TAG);
        item.addScoreboardTag(TAG_ITEM);
        item.addScoreboardTag(DisplayTags.MONITOR_BASE_TAG);
        Transformation t = item.getTransformation();
        t.getScale().set(new Vector3f((float) scale, (float) scale, (float) scale));
        t.getLeftRotation().identity();
        t.getRightRotation().identity();
        item.setTransformation(t);
    }

    private ItemDisplay.ItemDisplayTransform displayTransformFor(ItemStack sample) {
        if (sample == null || NEXO_FURNITURE == null) {
            return ItemDisplay.ItemDisplayTransform.FIXED;
        }
        ItemMeta meta = sample.getItemMeta();
        if (meta == null) {
            return ItemDisplay.ItemDisplayTransform.FIXED;
        }
        if (meta.getPersistentDataContainer().getKeys().contains(NEXO_FURNITURE)) {
            return ItemDisplay.ItemDisplayTransform.GROUND;
        }
        return ItemDisplay.ItemDisplayTransform.FIXED;
    }

    private void applyTextSettings(TextDisplay display, double scale) {
        display.setPersistent(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(1.0f);
        display.setTeleportDuration(0);
        display.addScoreboardTag(DisplayTags.DISPLAY_TAG);
        display.addScoreboardTag(TAG_TEXT);
        display.addScoreboardTag(DisplayTags.MONITOR_BASE_TAG);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        int alpha = Math.max(0, Math.min(255, textBackgroundAlpha));
        display.setBackgroundColor(org.bukkit.Color.fromARGB(alpha, 0, 0, 0));
        Transformation t = display.getTransformation();
        t.getScale().set(new Vector3f((float) scale, (float) scale, (float) scale));
        t.getLeftRotation().identity();
        t.getRightRotation().identity();
        display.setTransformation(t);
    }

    private void orientDisplay(Display display, Block block, double scale, boolean horizontal) {
        Transformation t = display.getTransformation();
        Quaternionf rotation = DisplayRotation.rotationForFacing(facingFor(block));
        if (horizontal) {
            rotation = rotation.mul(new Quaternionf().rotateX((float) (Math.PI / 2.0)));
        }
        t.getLeftRotation().set(rotation);
        t.getRightRotation().identity();
        t.getScale().set(new Vector3f((float) scale, (float) scale, (float) scale));
        display.setTransformation(t);
        display.setRotation(0f, 0f);
    }

    private void removeContent(Block block) {
        removeItemDisplay(block);
        removeTextDisplay(block);
    }

    private void removeItemDisplay(Block block) {
        UUID itemId = DisplayMarker.get(plugin, "monitor_item", block).orElse(null);
        if (itemId != null) {
            var ent = Bukkit.getEntity(itemId);
            if (ent instanceof ItemDisplay display && !display.isDead()) {
                display.remove();
            }
            DisplayMarker.clear(plugin, "monitor_item", block);
        }
    }

    private void removeTextDisplay(Block block) {
        UUID textId = DisplayMarker.get(plugin, "monitor_text", block).orElse(null);
        if (textId != null) {
            var ent = Bukkit.getEntity(textId);
            if (ent instanceof TextDisplay display && !display.isDead()) {
                display.remove();
            }
            DisplayMarker.clear(plugin, "monitor_text", block);
        }
    }

    private ItemDisplay findItemDisplay(Block block) {
        UUID id = DisplayMarker.get(plugin, "monitor_item", block).orElse(null);
        if (id != null) {
            var ent = Bukkit.getEntity(id);
            if (ent instanceof ItemDisplay display && !display.isDead()) {
                return display;
            }
            DisplayMarker.clear(plugin, "monitor_item", block);
        }
        return null;
    }

    private TextDisplay findTextDisplay(Block block) {
        UUID id = DisplayMarker.get(plugin, "monitor_text", block).orElse(null);
        if (id != null) {
            var ent = Bukkit.getEntity(id);
            if (ent instanceof TextDisplay display && !display.isDead()) {
                return display;
            }
            DisplayMarker.clear(plugin, "monitor_text", block);
        }
        return null;
    }

    private Location targetScreenLoc(Block block, ScreenConfig config) {
        BlockFace face = facingFor(block);
        Vector3f rotated = rotatedOffset(face, config.offsetX(), config.offsetY(), config.offsetZ());
        return block.getLocation().add(rotated.x(), rotated.y(), rotated.z());
    }

    private BlockFace facingFor(Block block) {
        if (block == null) return BlockFace.SOUTH;
        return MonitorMarker.facing(plugin, block).orElse(BlockFace.SOUTH);
    }

    private Vector3f rotatedOffset(BlockFace face, double ox, double oy, double oz) {
        double localRight = ox - 0.5;
        double localForward = oz - 0.5;
        double fx = 0.0;
        double fz = 1.0;
        switch (face) {
            case NORTH -> {
                fx = 0.0;
                fz = -1.0;
            }
            case EAST -> {
                fx = 1.0;
                fz = 0.0;
            }
            case WEST -> {
                fx = -1.0;
                fz = 0.0;
            }
            default -> {
                fx = 0.0;
                fz = 1.0;
            }
        }
        double rx = -fz;
        double rz = fx;
        double worldX = rx * localRight + fx * localForward;
        double worldZ = rz * localRight + fz * localForward;
        return new Vector3f((float) (worldX + 0.5), (float) oy, (float) (worldZ + 0.5));
    }

    private String formatCount(long amount) {
        if (amount < 10_000) {
            return Long.toString(amount);
        }
        if (amount < 1_000_000) {
            return trimTrailingZero(COMPACT_DECIMAL.format(amount / 1000.0)) + "K";
        }
        if (amount < 1_000_000_000) {
            return trimTrailingZero(COMPACT_DECIMAL.format(amount / 1_000_000.0)) + "M";
        }
        return trimTrailingZero(COMPACT_DECIMAL.format(amount / 1_000_000_000.0)) + "B";
    }

    private NamedTextColor freeColor(double freeRatio) {
        if (freeRatio <= 0.05) {
            return NamedTextColor.RED;
        }
        if (freeRatio <= 0.30) {
            return NamedTextColor.GOLD;
        }
        return NamedTextColor.WHITE;
    }

    private String trimTrailingZero(String value) {
        if (value.endsWith(".0")) {
            return value.substring(0, value.length() - 2);
        }
        return value;
    }

    private ScreenConfig screenConfigFor(ItemStack stack) {
        if (stack == null) return itemConfig;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(keys.type(), PersistentDataType.STRING)) {
            String customType = meta.getPersistentDataContainer().get(keys.type(), PersistentDataType.STRING);
            if ("wireless_terminal".equalsIgnoreCase(customType)) {
                return itemConfig;
            }
            if ("wire".equalsIgnoreCase(customType)) {
                return itemConfig;
            }
            return fullBlockConfig;
        }
        if (!stack.getType().isBlock()) return itemConfig;
        if (isChestMaterial(stack.getType())) return fullBlockConfig;
        if (isHorizontalBlock(stack.getType())) return horizontalBlockConfig;
        if (THIN_BLOCK_OVERRIDES.contains(stack.getType()) || isThinBlockOverride(stack.getType())) return thinBlockConfig;
        try {
            BlockData data = stack.getType().createBlockData();
            if (isFullBlock(data)) {
                return fullBlockConfig;
            }
            if (!stack.getType().isSolid()) {
                return thinBlockConfig;
            }
            return blockConfig;
        } catch (IllegalArgumentException ignored) {
            return blockConfig;
        }
    }

    private boolean isChestMaterial(Material material) {
        if (!material.isBlock()) return false;
        if (material == Material.CHEST) return true;
        return material.name().endsWith("_CHEST");
    }

    private boolean isHorizontalBlock(Material material) {
        if (!material.isBlock()) return false;
        return Tag.TRAPDOORS.isTagged(material) || Tag.PRESSURE_PLATES.isTagged(material) || material.name().endsWith("_CARPET");
    }

    private boolean isThinBlockOverride(Material material) {
        return material == Material.POINTED_DRIPSTONE;
    }

    private boolean isFullBlock(BlockData data) {
        for (BlockFace face : FULL_FACES) {
            if (!data.isFaceSturdy(face, BlockSupport.FULL)) {
                return false;
            }
        }
        return true;
    }

    private void ensureCacheLoaded(String storageId) {
        if (storageId == null || storageId.isBlank()) return;
        if (storageManager.peekLoadedCache(storageId).isPresent()) return;
        if (storageManager.isLoading(storageId)) return;
        long now = System.currentTimeMillis();
        long lastAttempt = loadAttempts.getOrDefault(storageId, 0L);
        if (now - lastAttempt < LOAD_COOLDOWN_MS) return;
        loadAttempts.put(storageId, now);
        storageManager.getOrLoad(storageId).whenComplete((cache, err) -> {
            if (err != null) return;
            Bukkit.getScheduler().runTask(plugin, () -> refreshStorageMonitors(storageId));
        });
    }

    private void refreshStorageMonitors(String storageId) {
        if (storageId == null) return;
        for (var entry : new HashMap<>(monitors).entrySet()) {
            MonitorState state = entry.getValue();
            if (state == null || !storageId.equals(state.storageId())) continue;
            MonitorPos pos = entry.getKey();
            World world = Bukkit.getWorld(pos.world());
            if (world == null) continue;
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) continue;
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (!isValidBlock(block)) continue;
            refresh(block);
        }
    }

    private record MonitorPos(UUID world, int x, int y, int z) {
        static MonitorPos of(Block block) {
            return new MonitorPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private record MonitorState(String storageId,
                                String itemKey,
                                long lastAmount,
                                String lastModelId,
                                ScreenConfig lastItemConfig,
                                boolean lastHorizontal,
                                ItemDisplay.ItemDisplayTransform lastTransform) {
    }
}
