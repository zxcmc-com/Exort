package com.zxcmc.exort.block;

import com.zxcmc.exort.api.model.ExortBlockDescriptor;
import com.zxcmc.exort.api.model.ExortContentType;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.keys.PdcValueSanitizer;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.storage.StorageTierCatalogSource;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class ExortBlockClassifier {
  private final Plugin plugin;
  private final CarrierMaterials materials;
  private final Supplier<StorageTierCatalog> storageTiers;

  public ExortBlockClassifier(Plugin plugin, CarrierMaterials materials) {
    this(
        plugin,
        materials,
        () ->
            plugin instanceof StorageTierCatalogSource source
                ? source.storageTierCatalog()
                : StorageTierCatalog.empty());
  }

  ExortBlockClassifier(
      Plugin plugin, CarrierMaterials materials, Supplier<StorageTierCatalog> storageTiers) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.materials = Objects.requireNonNull(materials, "materials");
    this.storageTiers = Objects.requireNonNull(storageTiers, "storageTiers");
  }

  public boolean isExortBlock(Block block) {
    if (isInteractiveBlock(block)) {
      return true;
    }
    if (isWire(block)) {
      return true;
    }
    return block != null
        && Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageCoreMarker.isCore(plugin, block);
  }

  public boolean isInteractiveBlock(Block block) {
    if (block == null) return false;
    if (Carriers.matchesCarrier(block, materials.terminalCarrier())
        && TerminalMarker.isTerminal(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.monitorCarrier())
        && MonitorMarker.isMonitor(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.busCarrier()) && BusMarker.isBus(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.relayCarrier())
        && RelayMarker.isRelay(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.transmitterCarrier())
        && TransmitterMarker.isTransmitter(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.chunkLoaderCarrier())
        && ChunkLoaderMarker.isChunkLoader(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageMarker.isMarkedStorage(plugin, block)) {
      return true;
    }
    return false;
  }

  public Optional<ExortBlockDescriptor> inspect(Block block) {
    if (block == null) return Optional.empty();
    boolean chorusCarrier = block.getType() == Material.CHORUS_PLANT;
    if (Carriers.matchesCarrier(block, materials.terminalCarrier())
        && TerminalMarker.isTerminal(plugin, block)) {
      return TerminalMarker.validKind(plugin, block)
          .map(
              kind ->
                  descriptor(
                      kind == TerminalKind.CRAFTING
                          ? ExortContentType.CRAFTING_TERMINAL
                          : ExortContentType.TERMINAL,
                      chorusCarrier));
    }
    if (isWire(block)) {
      return Optional.of(descriptor(ExortContentType.WIRE, chorusCarrier));
    }
    if (Carriers.matchesCarrier(block, materials.monitorCarrier())
        && MonitorMarker.isMonitor(plugin, block)) {
      return Optional.of(descriptor(ExortContentType.MONITOR, chorusCarrier));
    }
    if (Carriers.matchesCarrier(block, materials.busCarrier()) && BusMarker.isBus(plugin, block)) {
      return BusMarker.get(plugin, block)
          .map(BusMarker.Data::type)
          .map(
              type ->
                  descriptor(
                      type == BusType.EXPORT
                          ? ExortContentType.EXPORT_BUS
                          : ExortContentType.IMPORT_BUS,
                      chorusCarrier));
    }
    if (Carriers.matchesCarrier(block, materials.relayCarrier())
        && RelayMarker.isRelay(plugin, block)) {
      return Optional.of(descriptor(ExortContentType.RELAY, chorusCarrier));
    }
    if (Carriers.matchesCarrier(block, materials.transmitterCarrier())
        && TransmitterMarker.isTransmitter(plugin, block)) {
      return Optional.of(descriptor(ExortContentType.TRANSMITTER, chorusCarrier));
    }
    if (Carriers.matchesCarrier(block, materials.chunkLoaderCarrier())
        && ChunkLoaderMarker.isChunkLoader(plugin, block)) {
      return ChunkLoaderMarker.get(plugin, block)
          .map(ChunkLoaderMarker.Data::type)
          .map(ExortBlockClassifier::chunkLoaderContentType)
          .map(type -> descriptor(type, chorusCarrier));
    }
    if (Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageMarker.isMarkedStorage(plugin, block)) {
      StorageTierCatalog catalog = storageTiers.get();
      return StorageMarker.getForInspection(plugin, block, catalog)
          .filter(data -> PdcValueSanitizer.uuidString(data.storageId()) != null)
          .filter(data -> catalog.find(data.tier().key()).isPresent())
          .map(ignored -> descriptor(ExortContentType.STORAGE, chorusCarrier));
    }
    if (Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageCoreMarker.isCore(plugin, block)) {
      return Optional.of(descriptor(ExortContentType.STORAGE_CORE, chorusCarrier));
    }
    return Optional.empty();
  }

  public boolean isExortChorusCarrier(Block block) {
    return block != null && block.getType() == Material.CHORUS_PLANT && isExortBlock(block);
  }

  private boolean isWire(Block block) {
    return block != null
        && Carriers.matchesCarrier(block, materials.wire())
        && WireMarker.isWire(plugin, block);
  }

  private static ExortBlockDescriptor descriptor(ExortContentType type, boolean chorusCarrier) {
    return new ExortBlockDescriptor(type, chorusCarrier);
  }

  private static ExortContentType chunkLoaderContentType(ChunkLoaderType type) {
    return switch (type) {
      case CHUNK_LOADER -> ExortContentType.CHUNK_LOADER;
      case PERSONAL_CHUNK_LOADER -> ExortContentType.PERSONAL_CHUNK_LOADER;
      case DORMANT_CHUNK_LOADER -> ExortContentType.DORMANT_CHUNK_LOADER;
    };
  }
}
