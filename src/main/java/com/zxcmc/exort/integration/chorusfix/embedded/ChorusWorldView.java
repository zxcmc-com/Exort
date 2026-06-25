package com.zxcmc.exort.integration.chorusfix.embedded;

@FunctionalInterface
public interface ChorusWorldView {
  ChorusMaterial typeAt(int dx, int dy, int dz);
}
