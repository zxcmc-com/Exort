package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.concurrent.CompletableFuture;

public class TerminalListener implements Listener {
    private final ExortPlugin plugin;
    private final StorageManager storageManager;
    private final SessionManager sessionManager;
    private final StorageKeys keys;
    private final Lang lang;
    private final int wireLimit;
    private final int wireHardCap;
    private final Material wireMaterial;
    private final Material storageCarrier;
    private final Material terminalCarrier;

    public TerminalListener(ExortPlugin plugin, StorageManager storageManager, SessionManager sessionManager, StorageKeys keys, Lang lang, int wireLimit, int wireHardCap, Material wireMaterial, Material storageCarrier, Material terminalCarrier) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.sessionManager = sessionManager;
        this.keys = keys;
        this.lang = lang;
        this.wireLimit = wireLimit;
        this.wireHardCap = wireHardCap;
        this.wireMaterial = wireMaterial;
        this.storageCarrier = storageCarrier;
        this.terminalCarrier = terminalCarrier;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isOurTerminal(block)) return;
        if (event.getPlayer().isSneaking()) {
            // Allow vanilla block placement with shift-right-click
            return;
        }
        if (!plugin.getRegionProtection().canUse(event.getPlayer(), block)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getTerminalDisplayManager() != null) {
            plugin.getTerminalDisplayManager().refresh(block);
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);

        var storageData = TerminalLinkFinder.find(block, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
        if (storageData.count() == 0) {
            plugin.getBossBarManager().showError(event.getPlayer(), lang.tr("message.no_storage_adjacent"), plugin.getStoragePeekTicks());
            event.setCancelled(true);
            return;
        }
        if (storageData.count() > 1) {
            plugin.getBossBarManager().showError(event.getPlayer(), lang.tr("message.multiple_storages_adjacent"), plugin.getStoragePeekTicks());
            event.setCancelled(true);
            return;
        }
        if (storageData.data() == null) {
            plugin.getBossBarManager().showError(event.getPlayer(), lang.tr("message.storage_missing_id"), plugin.getStoragePeekTicks());
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);
        plugin.getDatabase().setStorageTier(storageData.data().storageId(), storageData.data().tier().key());
        CompletableFuture<StorageCache> future = storageManager.getOrLoad(storageData.data().storageId());
        future.thenAccept(cache -> Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.Location storageLoc = storageData.data().block() != null ? storageData.data().block().getLocation() : null;
            TerminalKind kind = TerminalMarker.kind(plugin, block);
            if (kind == TerminalKind.CRAFTING) {
                sessionManager.openCraftingSession(player, cache, storageData.data().tier(), block, storageLoc);
            } else {
                sessionManager.openSession(player, cache, storageData.data().tier(), block, storageLoc);
            }
        }));
    }

    private boolean isOurTerminal(Block block) {
        return Carriers.matchesCarrier(block, terminalCarrier)
                && TerminalMarker.isTerminal(plugin, block);
    }
}
