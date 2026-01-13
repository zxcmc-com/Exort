package com.zxcmc.exort.gui;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

public final class CraftingState {
  private final ItemStack[] grid = new ItemStack[9];
  private ConfirmTarget confirmTarget;
  private int confirmRemaining;
  private long confirmLastAt;
  private String bufferKey;
  private int bufferAmount;
  private ItemStack bufferSample;

  public synchronized ItemStack getSlot(int index) {
    if (index < 0 || index >= grid.length) return null;
    return grid[index] == null ? null : grid[index].clone();
  }

  public synchronized ItemStack[] snapshot() {
    ItemStack[] copy = new ItemStack[grid.length];
    for (int i = 0; i < grid.length; i++) {
      copy[i] = grid[i] == null ? null : grid[i].clone();
    }
    return copy;
  }

  public synchronized void setSlot(int index, ItemStack stack) {
    if (index < 0 || index >= grid.length) return;
    grid[index] = stack == null ? null : stack.clone();
    if (grid[index] != null) {
      grid[index].setAmount(1);
    }
    clearBuffer();
  }

  public synchronized void clear() {
    Arrays.fill(grid, null);
    resetConfirm();
    clearBuffer();
  }

  public synchronized void resetConfirm() {
    confirmTarget = null;
    confirmRemaining = 0;
    confirmLastAt = 0L;
  }

  public synchronized void startConfirm(ConfirmTarget target, int remaining) {
    confirmTarget = target;
    confirmRemaining = Math.max(0, remaining);
    confirmLastAt = System.currentTimeMillis();
  }

  public synchronized boolean isConfirming(ConfirmTarget target, long timeoutMs) {
    if (confirmTarget != target || confirmRemaining <= 0) return false;
    if (timeoutMs > 0 && System.currentTimeMillis() - confirmLastAt > timeoutMs) {
      resetConfirm();
      return false;
    }
    return true;
  }

  public synchronized int confirmRemaining(ConfirmTarget target, long timeoutMs) {
    return isConfirming(target, timeoutMs) ? confirmRemaining : 0;
  }

  public synchronized int decrementConfirm(ConfirmTarget target, long timeoutMs) {
    if (!isConfirming(target, timeoutMs)) return 0;
    confirmRemaining = Math.max(0, confirmRemaining - 1);
    confirmLastAt = System.currentTimeMillis();
    if (confirmRemaining == 0) {
      confirmTarget = null;
    }
    return confirmRemaining;
  }

  public synchronized void clearBuffer() {
    bufferKey = null;
    bufferAmount = 0;
    bufferSample = null;
  }

  public synchronized void clearBufferIfMismatch(String key) {
    if (bufferKey != null && !bufferKey.equals(key)) {
      clearBuffer();
    }
  }

  public synchronized Buffer snapshotBuffer() {
    if (bufferKey == null || bufferAmount <= 0 || bufferSample == null) return null;
    return new Buffer(bufferKey, bufferSample.clone(), bufferAmount);
  }

  public synchronized int bufferAmount(String key) {
    if (bufferKey == null || !bufferKey.equals(key)) return 0;
    return bufferAmount;
  }

  public synchronized void setBuffer(String key, ItemStack sample, int amount) {
    if (key == null || amount <= 0 || sample == null || sample.getType().isAir()) {
      clearBuffer();
      return;
    }
    bufferKey = key;
    bufferAmount = amount;
    bufferSample = sample.clone();
    bufferSample.setAmount(1);
  }

  public synchronized void takeFromBuffer(String key, int amount) {
    if (bufferKey == null || !bufferKey.equals(key) || amount <= 0) return;
    bufferAmount = Math.max(0, bufferAmount - amount);
    if (bufferAmount == 0) {
      bufferKey = null;
      bufferSample = null;
    }
  }

  public enum ConfirmTarget {
    STORAGE,
    PLAYER
  }

  public record Buffer(String key, ItemStack sample, int amount) {}
}
