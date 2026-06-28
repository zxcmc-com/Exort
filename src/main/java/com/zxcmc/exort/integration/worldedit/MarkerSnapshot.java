package com.zxcmc.exort.integration.worldedit;

record MarkerSnapshot(
    StorageData storage,
    TerminalData terminal,
    BusData bus,
    MonitorData monitor,
    RelayData relay,
    ChunkLoaderData chunkLoader,
    boolean wire,
    boolean storageCore) {
  MarkerSnapshot(
      StorageData storage,
      TerminalData terminal,
      BusData bus,
      MonitorData monitor,
      RelayData relay,
      boolean wire,
      boolean storageCore) {
    this(storage, terminal, bus, monitor, relay, null, wire, storageCore);
  }
}
