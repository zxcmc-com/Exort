package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.core.carrier.Carriers;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PickListener implements Listener {
    private final ExortPlugin plugin;
    private final CustomItems customItems;
    private final StorageKeys keys;
    private final Material wireMaterial;
    private final Material storageCarrier;
    private final Material terminalCarrier;
    private final Material monitorCarrier;
    private final Material busCarrier;

    public PickListener(ExortPlugin plugin, CustomItems customItems, StorageKeys keys, Material wireMaterial, Material storageCarrier, Material terminalCarrier, Material monitorCarrier, Material busCarrier) {
        this.plugin = plugin;
        this.customItems = customItems;
        this.keys = keys;
        this.wireMaterial = wireMaterial;
        this.storageCarrier = storageCarrier;
        this.terminalCarrier = terminalCarrier;
        this.monitorCarrier = monitorCarrier;
        this.busCarrier = busCarrier;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPick(PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        Block target = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
        if (target == null) return;

        ItemStack desired = null;
        String type = null;
        String expectedTier = null;
        if (isTerminal(target)) {
            TerminalKind kind = TerminalMarker.kind(plugin, target);
            if (kind == TerminalKind.CRAFTING) {
                desired = customItems.craftingTerminalItem();
                type = "crafting_terminal";
            } else {
                desired = customItems.terminalItem();
                type = "terminal";
            }
        } else if (isWire(target)) {
            desired = customItems.wireItem();
            type = "wire";
        } else if (isMonitor(target)) {
            desired = customItems.monitorItem();
            type = "monitor";
        } else if (isBus(target)) {
            var data = BusMarker.get(plugin, target).orElse(null);
            if (data == null) return;
            if (data.type() == com.zxcmc.exort.bus.BusType.EXPORT) {
                desired = customItems.exportBusItem();
                type = "export_bus";
            } else {
                desired = customItems.importBusItem();
                type = "import_bus";
            }
        } else if (isStorage(target)) {
            var tierOpt = readTier(target);
            if (tierOpt.isEmpty()) return;
            StorageTier tier = tierOpt.get();
            desired = customItems.storageItem(tier, null);
            type = "storage";
            expectedTier = tier.key();
        } else if (isStorageCore(target)) {
            desired = customItems.storageCoreItem();
            type = "storage_core";
        } else {
            return;
        }

        int held = player.getInventory().getHeldItemSlot();
        int existingSlot = findExisting(player.getInventory(), type, expectedTier);
        if (existingSlot >= 0) {
            if (existingSlot <= 8) {
                // Already on hotbar: just select that slot
                event.setSourceSlot(existingSlot);
                event.setTargetSlot(existingSlot);
            } else {
                int empty = findEmptyHotbar(player.getInventory(), held);
                if (empty >= 0) {
                    // Move into first empty hotbar slot
                    event.setSourceSlot(existingSlot);
                    event.setTargetSlot(empty);
                } else {
                    // All hotbar slots filled: swap with active slot
                    event.setSourceSlot(existingSlot);
                    event.setTargetSlot(held);
                }
            }
            return;
        }

        // No existing stack. In creative, provide a fresh stack; otherwise block vanilla pick.
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE && desired != null) {
            ItemStack give = desired.clone();
            give.setAmount(1);
            int empty = findEmptyHotbar(player.getInventory(), held);
            int targetSlot = empty >= 0 ? empty : held;
            player.getInventory().setItem(targetSlot, give);
            player.getInventory().setHeldItemSlot(targetSlot);
            player.updateInventory();
        }
    }

    private boolean isTerminal(Block block) {
        return Carriers.matchesCarrier(block, terminalCarrier)
                && TerminalMarker.isTerminal(plugin, block);
    }

    private boolean isWire(Block block) {
        return Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
    }

    private boolean isStorage(Block block) {
        return Carriers.matchesCarrier(block, storageCarrier)
                && StorageMarker.get(plugin, block).isPresent();
    }

    private boolean isStorageCore(Block block) {
        return Carriers.matchesCarrier(block, storageCarrier)
                && StorageCoreMarker.isCore(plugin, block);
    }

    private boolean isMonitor(Block block) {
        return Carriers.matchesCarrier(block, monitorCarrier)
                && MonitorMarker.isMonitor(plugin, block);
    }

    private boolean isBus(Block block) {
        return Carriers.matchesCarrier(block, busCarrier)
                && BusMarker.isBus(plugin, block);
    }

    private java.util.Optional<StorageTier> readTier(Block block) {
        return StorageMarker.get(plugin, block).map(StorageMarker.Data::tier);
    }

    private int findExisting(PlayerInventory inv, String type, String expectedTier) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (matchesType(contents[i], type, expectedTier)) {
                return i;
            }
        }
        return -1;
    }

    private boolean matchesType(ItemStack stack, String type, String expectedTier) {
        if (stack == null || !stack.hasItemMeta()) return false;
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String t = pdc.get(keys.type(), PersistentDataType.STRING);
        if (!type.equalsIgnoreCase(t)) return false;
        if ("storage".equalsIgnoreCase(type) && expectedTier != null) {
            String tier = pdc.get(keys.storageTier(), PersistentDataType.STRING);
            return expectedTier.equalsIgnoreCase(tier);
        }
        return true;
    }

    private int findEmptyHotbar(PlayerInventory inv, int startSlot) {
        // search from current slot to end
        for (int i = startSlot; i <= 8; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) {
                return i;
            }
        }
        // wrap around to the beginning
        for (int i = 0; i < startSlot; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) {
                return i;
            }
        }
        return -1;
    }
}
