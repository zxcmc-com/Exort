package com.zxcmc.exort.integration.worldedit;

import java.util.Arrays;

record TransmitterData(String mode, byte[] terminalBlob) {
  TransmitterData {
    mode = mode == null || mode.isBlank() ? null : mode;
    terminalBlob =
        terminalBlob == null || terminalBlob.length == 0
            ? null
            : Arrays.copyOf(terminalBlob, terminalBlob.length);
  }

  @Override
  public byte[] terminalBlob() {
    return terminalBlob == null ? null : Arrays.copyOf(terminalBlob, terminalBlob.length);
  }

  TransmitterData withoutTerminalBlob() {
    return terminalBlob == null ? this : new TransmitterData(mode, null);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof TransmitterData other
        && java.util.Objects.equals(mode, other.mode)
        && Arrays.equals(terminalBlob, other.terminalBlob);
  }

  @Override
  public int hashCode() {
    return 31 * java.util.Objects.hashCode(mode) + Arrays.hashCode(terminalBlob);
  }
}
