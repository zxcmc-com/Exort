package com.zxcmc.exort.integration.worldedit;

record MarkerSnapshot(
    StorageData storage,
    TerminalData terminal,
    BusData bus,
    MonitorData monitor,
    RelayData relay,
    boolean wire,
    boolean storageCore) {}
