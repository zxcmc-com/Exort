package com.zxcmc.exort.bus;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public final class InventorySideRules {
    private static final int[] FURNACE_INPUT = new int[]{0};
    private static final int[] FURNACE_FUEL = new int[]{1};
    private static final int[] FURNACE_OUTPUT = new int[]{2};
    private static final int[] BREWING_BOTTLES = new int[]{0, 1, 2};
    private static final int[] BREWING_INGREDIENT = new int[]{3};
    private static final int[] BREWING_FUEL = new int[]{4};

    private InventorySideRules() {
    }

    public static int[] insertSlots(Inventory inventory, BlockState state, BlockFace side) {
        if (inventory == null) return new int[0];
        InventoryType type = inventory.getType();
        if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER) {
            if (side == BlockFace.UP) return FURNACE_INPUT;
            if (side == BlockFace.DOWN) return new int[0];
            return FURNACE_FUEL;
        }
        if (type == InventoryType.BREWING) {
            if (side == BlockFace.UP) return concat(BREWING_INGREDIENT, BREWING_FUEL);
            if (side == BlockFace.DOWN) return new int[0];
            return concat(BREWING_BOTTLES, BREWING_FUEL);
        }
        return allSlots(inventory);
    }

    public static int[] extractSlots(Inventory inventory, BlockState state, BlockFace side) {
        if (inventory == null) return new int[0];
        InventoryType type = inventory.getType();
        if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER) {
            return side == BlockFace.DOWN ? concat(FURNACE_OUTPUT, FURNACE_FUEL) : new int[0];
        }
        if (type == InventoryType.BREWING) {
            return side == BlockFace.DOWN ? BREWING_BOTTLES : new int[0];
        }
        return allSlots(inventory);
    }

    public static boolean canInsert(Inventory inventory, BlockState state, BlockFace side, ItemStack stack, int slot) {
        if (stack == null || stack.getType().isAir()) return false;
        InventoryType type = inventory.getType();
        if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER) {
            if (!(inventory instanceof FurnaceInventory furnace)) {
                return slot == 0 || slot == 1;
            }
            if (slot == 0) {
                return furnace.canSmelt(stack);
            }
            if (slot == 1) {
                return furnace.isFuel(stack);
            }
            return false;
        }
        if (type == InventoryType.BREWING) {
            if (slot == 4) {
                return stack.getType() == Material.BLAZE_POWDER;
            }
            if (slot == 3) {
                return stack.getType() != Material.BLAZE_POWDER;
            }
            return slot >= 0 && slot <= 2;
        }
        return true;
    }

    public static boolean isSideSensitive(Inventory inventory) {
        if (inventory == null) return false;
        InventoryType type = inventory.getType();
        return type == InventoryType.FURNACE
                || type == InventoryType.BLAST_FURNACE
                || type == InventoryType.SMOKER
                || type == InventoryType.BREWING;
    }

    public static boolean canExtract(Inventory inventory, BlockState state, BlockFace side, ItemStack stack, int slot) {
        if (stack == null || stack.getType().isAir()) return false;
        InventoryType type = inventory.getType();
        if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER) {
            if (side != BlockFace.DOWN) return false;
            if (slot == 2) return true;
            if (slot == 1) {
                Material mat = stack.getType();
                return mat == Material.BUCKET;
            }
            return false;
        }
        if (type == InventoryType.BREWING) {
            return side == BlockFace.DOWN && slot >= 0 && slot <= 2;
        }
        return true;
    }

    public static int insert(Inventory inventory, BlockState state, BlockFace side, ItemStack sample, String key, int maxAmount) {
        if (inventory == null || sample == null || sample.getType().isAir()) return 0;
        int remaining = Math.max(0, maxAmount);
        if (remaining == 0) return 0;
        int[] slots = insertSlots(inventory, state, side);
        if (slots.length == 0) return 0;
        int moved = 0;
        int maxStack = sample.getMaxStackSize();
        for (int slot : slots) {
            if (remaining <= 0) break;
            if (!canInsert(inventory, state, side, sample, slot)) continue;
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                int put = Math.min(maxStack, remaining);
                ItemStack clone = sample.clone();
                clone.setAmount(put);
                inventory.setItem(slot, clone);
                remaining -= put;
                moved += put;
                continue;
            }
            String existingKey = com.zxcmc.exort.core.items.ItemKeyUtil.keyFor(existing);
            if (!existingKey.equals(key)) continue;
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) continue;
            int put = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + put);
            inventory.setItem(slot, existing);
            remaining -= put;
            moved += put;
        }
        return moved;
    }

    private static int[] allSlots(Inventory inventory) {
        int size = inventory.getSize();
        int[] slots = new int[size];
        for (int i = 0; i < size; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
