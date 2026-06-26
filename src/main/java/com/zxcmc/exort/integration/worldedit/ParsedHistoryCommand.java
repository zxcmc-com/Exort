package com.zxcmc.exort.integration.worldedit;

record ParsedHistoryCommand(HistoryAction action, int steps) {
  ParsedHistoryCommand {
    java.util.Objects.requireNonNull(action, "action");
    steps = Math.max(1, steps);
  }
}
