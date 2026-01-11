package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.StorageMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

public class StorageListener implements Listener {
    private final ExortPlugin plugin;
    private final long peekDurationTicks;
    private final Material storageCarrier;

    public StorageListener(ExortPlugin plugin, long peekDurationTicks, Material storageCarrier) {
        this.plugin = plugin;
        this.peekDurationTicks = peekDurationTicks;
        this.storageCarrier = storageCarrier;
    }

    private boolean isOurStorage(Block block) {
        return Carriers.matchesCarrier(block, storageCarrier)
                && StorageMarker.get(plugin, block).isPresent();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!event.getAction().isRightClick()) return;
        if (!isOurStorage(block)) return;
        if (event.getPlayer().isSneaking()) {
            // allow vanilla placement
            return;
        }
        if (!plugin.getRegionProtection().canUse(event.getPlayer(), block)) {
            event.setCancelled(true);
            return;
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        var data = StorageMarker.get(plugin, block);
        if (data.isEmpty()) return;
        String storageId = data.get().storageId();
        Optional<StorageTier> tierOpt = Optional.of(data.get().tier());
        plugin.getBossBarManager().showPeek(storageId, tierOpt.get(), event.getPlayer(), peekDurationTicks);
    }
}
