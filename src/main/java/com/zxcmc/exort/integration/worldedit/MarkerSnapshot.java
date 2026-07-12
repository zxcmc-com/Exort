package com.zxcmc.exort.integration.worldedit;

record MarkerSnapshot(
    StorageData storage,
    TerminalData terminal,
    BusData bus,
    MonitorData monitor,
    RelayData relay,
    boolean transmitter,
    TransmitterData transmitterData,
    ChunkLoaderData chunkLoader,
    boolean wire,
    boolean storageCore) {
  MarkerSnapshot {
    transmitter = transmitter || transmitterData != null;
    if (!transmitter) {
      transmitterData = null;
    }
  }

  MarkerSnapshot(
      StorageData storage,
      TerminalData terminal,
      BusData bus,
      MonitorData monitor,
      RelayData relay,
      boolean transmitter,
      ChunkLoaderData chunkLoader,
      boolean wire,
      boolean storageCore) {
    this(storage, terminal, bus, monitor, relay, transmitter, null, chunkLoader, wire, storageCore);
  }

  MarkerSnapshot(
      StorageData storage,
      TerminalData terminal,
      BusData bus,
      MonitorData monitor,
      RelayData relay,
      ChunkLoaderData chunkLoader,
      boolean wire,
      boolean storageCore) {
    this(storage, terminal, bus, monitor, relay, false, null, chunkLoader, wire, storageCore);
  }

  MarkerSnapshot(
      StorageData storage,
      TerminalData terminal,
      BusData bus,
      MonitorData monitor,
      RelayData relay,
      boolean wire,
      boolean storageCore) {
    this(storage, terminal, bus, monitor, relay, false, null, null, wire, storageCore);
  }

  MarkerSnapshot withoutTransmitterStoredItems() {
    TransmitterData sanitized =
        transmitterData == null ? null : transmitterData.withoutStoredItems();
    return new MarkerSnapshot(
        storage,
        terminal,
        bus,
        monitor,
        relay,
        transmitter,
        sanitized,
        chunkLoader,
        wire,
        storageCore);
  }
}
