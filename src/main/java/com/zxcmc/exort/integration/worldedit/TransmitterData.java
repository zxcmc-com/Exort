package com.zxcmc.exort.integration.worldedit;

import java.util.Arrays;

record TransmitterData(String mode, byte[] terminalBlob, byte[] boosterBlob) {
  TransmitterData {
    mode = mode == null || mode.isBlank() ? null : mode;
    terminalBlob =
        terminalBlob == null || terminalBlob.length == 0
            ? null
            : Arrays.copyOf(terminalBlob, terminalBlob.length);
    boosterBlob =
        boosterBlob == null || boosterBlob.length == 0
            ? null
            : Arrays.copyOf(boosterBlob, boosterBlob.length);
  }

  TransmitterData(String mode, byte[] terminalBlob) {
    this(mode, terminalBlob, null);
  }

  @Override
  public byte[] terminalBlob() {
    return terminalBlob == null ? null : Arrays.copyOf(terminalBlob, terminalBlob.length);
  }

  @Override
  public byte[] boosterBlob() {
    return boosterBlob == null ? null : Arrays.copyOf(boosterBlob, boosterBlob.length);
  }

  TransmitterData withoutStoredItems() {
    return terminalBlob == null && boosterBlob == null
        ? this
        : new TransmitterData(mode, null, null);
  }

  TransmitterData mergeMissingFrom(TransmitterData other) {
    if (other == null) {
      return this;
    }
    return new TransmitterData(
        mode == null ? other.mode : mode,
        terminalBlob == null ? other.terminalBlob : terminalBlob,
        boosterBlob == null ? other.boosterBlob : boosterBlob);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof TransmitterData other
        && java.util.Objects.equals(mode, other.mode)
        && Arrays.equals(terminalBlob, other.terminalBlob)
        && Arrays.equals(boosterBlob, other.boosterBlob);
  }

  @Override
  public int hashCode() {
    int result = 31 * java.util.Objects.hashCode(mode) + Arrays.hashCode(terminalBlob);
    return 31 * result + Arrays.hashCode(boosterBlob);
  }
}
