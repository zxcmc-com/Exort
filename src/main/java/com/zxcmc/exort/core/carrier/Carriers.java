package com.zxcmc.exort.core.carrier;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;

public final class Carriers {
    public static final Material CARRIER_BARRIER = Material.BARRIER;
    public static final Material CHORUS_MATERIAL = Material.CHORUS_PLANT;

    private Carriers() {
    }

    public static boolean isFullChorus(Block block) {
        if (block == null || block.getType() != CHORUS_MATERIAL) return false;
        if (!(block.getBlockData() instanceof MultipleFacing facing)) return false;
        for (var face : facing.getAllowedFaces()) {
            if (!facing.hasFace(face)) return false;
        }
        return true;
    }

    public static void applyChorusFaces(Block block) {
        if (block == null || block.getType() != CHORUS_MATERIAL) return;
        var data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged) {
            waterlogged.setWaterlogged(false);
            block.setBlockData(waterlogged, false);
            data = block.getBlockData();
        }
        if (data instanceof MultipleFacing facing) {
            for (var face : facing.getAllowedFaces()) {
                facing.setFace(face, true);
            }
            block.setBlockData(facing, false);
        }
    }

    public static boolean isBarrier(Block block) {
        return block != null && block.getType() == CARRIER_BARRIER;
    }

    public static boolean isChorusCarrier(Block block) {
        return isFullChorus(block);
    }

    public static boolean matchesCarrier(Block block, Material carrier) {
        if (block == null || carrier == null) return false;
        if (carrier == CARRIER_BARRIER) {
            return isBarrier(block);
        }
        if (carrier == CHORUS_MATERIAL) {
            return isChorusCarrier(block);
        }
        return block.getType() == carrier;
    }

    public static void applyCarrier(Block block, Material carrier) {
        if (block == null || carrier == null) return;
        if (block.getType() != carrier) {
            block.setType(carrier, false);
        }
        if (carrier == CHORUS_MATERIAL) {
            applyChorusFaces(block);
        }
    }
}
