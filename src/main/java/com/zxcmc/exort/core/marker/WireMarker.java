package com.zxcmc.exort.core.marker;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class WireMarker {
    private WireMarker() {}

    private static NamespacedKey keyFor(Plugin plugin, Block block) {
        return new NamespacedKey(plugin, "wire_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    public static void setWire(Plugin plugin, Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        pdc.set(keyFor(plugin, block), PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isWire(Plugin plugin, Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        Byte val = pdc.get(keyFor(plugin, block), PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    public static void clearWire(Plugin plugin, Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        pdc.remove(keyFor(plugin, block));
    }
}
