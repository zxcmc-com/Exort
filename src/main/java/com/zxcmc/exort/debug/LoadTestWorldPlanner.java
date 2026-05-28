package com.zxcmc.exort.debug;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LoadTestWorldPlanner {
  static final int SLOT_STRIDE_X = 7;
  static final int SLOT_STRIDE_Z = 7;
  static final int SLOTS_PER_CHUNK = 4;

  private static final List<Placement> TEMPLATE =
      List.of(
          new Placement(new Cell(0, 0, 1), Kind.STORAGE),
          new Placement(new Cell(0, 0, 2), Kind.STORAGE_CORE),
          new Placement(new Cell(1, 0, 1), Kind.WIRE),
          new Placement(new Cell(2, 0, 1), Kind.WIRE),
          new Placement(new Cell(3, 0, 1), Kind.WIRE),
          new Placement(new Cell(4, 0, 1), Kind.WIRE),
          new Placement(new Cell(4, 0, 0), Kind.WIRE),
          new Placement(new Cell(5, 0, 1), Kind.TERMINAL),
          new Placement(new Cell(5, 0, 0), Kind.CRAFTING_TERMINAL),
          new Placement(new Cell(2, 0, 2), Kind.MONITOR),
          new Placement(new Cell(3, 0, 2), Kind.IMPORT_BUS),
          new Placement(new Cell(4, 0, 2), Kind.EXPORT_BUS),
          new Placement(new Cell(3, 0, 3), Kind.CHEST_TARGET),
          new Placement(new Cell(4, 0, 3), Kind.FURNACE_TARGET));

  private LoadTestWorldPlanner() {}

  static Plan buildPlan(Set<Cell> nonBenchmarkOccupied, int availableCells) {
    if (availableCells < TEMPLATE.size()) {
      return Plan.empty();
    }
    for (Placement placement : TEMPLATE) {
      if (nonBenchmarkOccupied.contains(placement.cell())) {
        return Plan.empty();
      }
    }
    Set<Cell> cleanup = new LinkedHashSet<>();
    for (Placement placement : TEMPLATE) {
      cleanup.add(placement.cell());
    }
    return new Plan(TEMPLATE, cleanup);
  }

  static SequencePlan sequencePlan(int moves, int slotStride, Set<Cell> nonBenchmarkOccupied) {
    int count = Math.max(1, moves + 1);
    List<Plan> plans = new ArrayList<>(count);
    Set<Cell> tracked = new LinkedHashSet<>();
    Set<Cell> cleanup = new LinkedHashSet<>();
    for (int i = 0; i < count; i++) {
      Cell offset = new Cell(i * Math.max(1, slotStride), 0, 0);
      Set<Cell> occupied = translate(nonBenchmarkOccupied, offset);
      Plan plan = buildPlan(occupied, TEMPLATE.size()).translated(offset);
      plans.add(plan);
      tracked.addAll(plan.cleanupCells());
      cleanup.addAll(plan.cleanupCells());
    }
    return new SequencePlan(plans, tracked, cleanup);
  }

  static List<Placement> template() {
    return TEMPLATE;
  }

  static Cell slotOffset(int slot) {
    int normalized = Math.floorMod(slot, SLOTS_PER_CHUNK);
    int x = (normalized % 2) * SLOT_STRIDE_X;
    int z = (normalized / 2) * SLOT_STRIDE_Z;
    return new Cell(x, 0, z);
  }

  private static Set<Cell> translate(Set<Cell> cells, Cell offset) {
    if (cells == null || cells.isEmpty()) return Set.of();
    Set<Cell> translated = new LinkedHashSet<>();
    for (Cell cell : cells) {
      translated.add(cell.translate(offset));
    }
    return translated;
  }

  enum Kind {
    STORAGE,
    STORAGE_CORE,
    WIRE,
    TERMINAL,
    CRAFTING_TERMINAL,
    MONITOR,
    IMPORT_BUS,
    EXPORT_BUS,
    CHEST_TARGET,
    FURNACE_TARGET
  }

  record Cell(int dx, int dy, int dz) {
    Cell translate(Cell other) {
      return new Cell(dx + other.dx, dy + other.dy, dz + other.dz);
    }
  }

  record Placement(Cell cell, Kind kind) {
    Placement translated(Cell offset) {
      return new Placement(cell.translate(offset), kind);
    }
  }

  record Plan(List<Placement> placements, Set<Cell> cleanupCells) {
    static Plan empty() {
      return new Plan(List.of(), Set.of());
    }

    Plan translated(Cell offset) {
      if (placements.isEmpty()) return this;
      List<Placement> translatedPlacements = new ArrayList<>(placements.size());
      Set<Cell> translatedCleanup = new LinkedHashSet<>();
      for (Placement placement : placements) {
        Placement translated = placement.translated(offset);
        translatedPlacements.add(translated);
        translatedCleanup.add(translated.cell());
      }
      return new Plan(List.copyOf(translatedPlacements), Set.copyOf(translatedCleanup));
    }
  }

  record SequencePlan(List<Plan> plans, Set<Cell> trackedCells, Set<Cell> cleanupCells) {}
}
