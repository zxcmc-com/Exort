package com.zxcmc.exort.runtime;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.block.Block;

/** Host callbacks that keep the runtime factory independent from the plugin facade. */
public record RuntimeHooks(
    Runnable reloadDefaultSortMode,
    Runnable setupRegionProtection,
    Runnable revalidateSessions,
    Consumer<String> pickDebugSink,
    Consumer<String> pickDebugFullSink,
    Consumer<Block> monitorPlacedRecorder,
    Predicate<Block> monitorRecentlyPlaced,
    Consumer<Block> transmitterPlacedRecorder,
    Predicate<Block> transmitterRecentlyPlaced,
    Consumer<String> renderStorage) {
  public RuntimeHooks {
    Objects.requireNonNull(reloadDefaultSortMode, "reloadDefaultSortMode");
    Objects.requireNonNull(setupRegionProtection, "setupRegionProtection");
    Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    Objects.requireNonNull(pickDebugSink, "pickDebugSink");
    Objects.requireNonNull(pickDebugFullSink, "pickDebugFullSink");
    Objects.requireNonNull(monitorPlacedRecorder, "monitorPlacedRecorder");
    Objects.requireNonNull(monitorRecentlyPlaced, "monitorRecentlyPlaced");
    Objects.requireNonNull(transmitterPlacedRecorder, "transmitterPlacedRecorder");
    Objects.requireNonNull(transmitterRecentlyPlaced, "transmitterRecentlyPlaced");
    Objects.requireNonNull(renderStorage, "renderStorage");
  }
}
