package com.zxcmc.exort.bus;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public record BusPos(UUID world, int x, int y, int z) {
    public static BusPos of(Block block) {
        return new BusPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public Block block() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return w.getBlockAt(x, y, z);
    }
}
