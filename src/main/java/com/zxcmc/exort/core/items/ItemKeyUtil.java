package com.zxcmc.exort.core.items;

import org.bukkit.inventory.ItemStack;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ItemKeyUtil {
    private ItemKeyUtil() {
    }

    public record SampleData(ItemStack sample, byte[] bytes, String key) {
    }

    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    });

    public static ItemStack sampleItem(ItemStack stack) {
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        return clone;
    }

    public static ItemStack cloneSample(ItemStack stack) {
        ItemStack clone = stack.clone();
        if (clone.getAmount() != 1) {
            clone.setAmount(1);
        }
        return clone;
    }

    public static SampleData sampleData(ItemStack stack) {
        // Compute sample, bytes and key in one pass to reduce allocations during hot paths.
        ItemStack sample = sampleItem(stack);
        byte[] bytes = sample.serializeAsBytes();
        String key = sha256Hex(bytes);
        return new SampleData(sample, bytes, key);
    }

    public static String keyFor(ItemStack stack) {
        ItemStack clone = sampleItem(stack);
        byte[] bytes = clone.serializeAsBytes();
        return sha256Hex(bytes);
    }

    public static ItemStack deserialize(byte[] bytes) {
        return ItemStack.deserializeBytes(bytes);
    }

    public static String sha256Hex(byte[] data) {
        MessageDigest digest = SHA256.get();
        digest.reset();
        byte[] hash = digest.digest(data);
        char[] out = new char[hash.length * 2];
        for (int i = 0; i < hash.length; i++) {
            int v = hash[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();
}
