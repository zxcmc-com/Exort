package com.zxcmc.exort.bus.loop;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Finds storage-to-storage bus edges that can circulate at least one item indefinitely. */
public final class BusStorageCycleGuard {
  private static final int MAX_EXPLICIT_FILTER_KEYS = 4096;

  private BusStorageCycleGuard() {}

  public static Set<BusPos> cyclicBuses(Collection<Edge> rawEdges) {
    if (rawEdges == null || rawEdges.isEmpty()) {
      return Set.of();
    }
    List<Edge> edges =
        rawEdges.stream()
            .filter(Objects::nonNull)
            .filter(Edge::enabled)
            .filter(edge -> !edge.fromStorageId().equals(edge.toStorageId()))
            .toList();
    if (edges.size() < 2) {
      return Set.of();
    }

    LinkedHashSet<String> explicitKeys = new LinkedHashSet<>();
    boolean candidateOverflow = false;
    for (Edge edge : edges) {
      for (String key : edge.filterKeys()) {
        if (key == null || key.isBlank()) {
          continue;
        }
        if (explicitKeys.size() >= MAX_EXPLICIT_FILTER_KEYS && !explicitKeys.contains(key)) {
          candidateOverflow = true;
          break;
        }
        explicitKeys.add(key);
      }
      if (candidateOverflow) {
        break;
      }
    }
    if (candidateOverflow) {
      return busesInCycles(edges);
    }

    Set<BusPos> cyclic = new HashSet<>();
    cyclic.addAll(busesInCycles(filterForCandidate(edges, Candidate.otherItems())));
    for (String key : explicitKeys) {
      cyclic.addAll(busesInCycles(filterForCandidate(edges, Candidate.item(key))));
    }
    return Set.copyOf(cyclic);
  }

  private static List<Edge> filterForCandidate(List<Edge> edges, Candidate candidate) {
    return edges.stream().filter(edge -> edge.accepts(candidate)).toList();
  }

  private static Set<BusPos> busesInCycles(List<Edge> edges) {
    if (edges.size() < 2) {
      return Set.of();
    }
    Map<String, List<String>> adjacency = new HashMap<>();
    for (Edge edge : edges) {
      adjacency
          .computeIfAbsent(edge.fromStorageId(), ignored -> new ArrayList<>())
          .add(edge.toStorageId());
      adjacency.computeIfAbsent(edge.toStorageId(), ignored -> new ArrayList<>());
    }
    Components components = Components.find(adjacency);
    Set<BusPos> result = new HashSet<>();
    for (Edge edge : edges) {
      int fromComponent = components.componentOf(edge.fromStorageId());
      int toComponent = components.componentOf(edge.toStorageId());
      if (fromComponent < 0 || fromComponent != toComponent) {
        continue;
      }
      if (components.sizeOf(fromComponent) > 1 || edge.fromStorageId().equals(edge.toStorageId())) {
        result.add(edge.bus());
      }
    }
    return result;
  }

  public record Edge(
      BusPos bus, String fromStorageId, String toStorageId, BusMode mode, Set<String> filterKeys) {
    public Edge {
      Objects.requireNonNull(bus, "bus");
      fromStorageId = requireStorageId(fromStorageId, "fromStorageId");
      toStorageId = requireStorageId(toStorageId, "toStorageId");
      mode = mode == null ? BusMode.DISABLED : mode;
      filterKeys = filterKeys == null ? Set.of() : Set.copyOf(filterKeys);
    }

    private static String requireStorageId(String storageId, String field) {
      if (storageId == null || storageId.isBlank()) {
        throw new IllegalArgumentException(field + " must not be blank");
      }
      return storageId;
    }

    private boolean enabled() {
      return switch (mode) {
        case ALL, BLACKLIST -> true;
        case WHITELIST -> !filterKeys.isEmpty();
        case DISABLED -> false;
      };
    }

    private boolean accepts(Candidate candidate) {
      return switch (mode) {
        case ALL -> true;
        case DISABLED -> false;
        case WHITELIST -> !candidate.other() && filterKeys.contains(candidate.itemKey());
        case BLACKLIST -> candidate.other() || !filterKeys.contains(candidate.itemKey());
      };
    }
  }

  private record Candidate(String itemKey, boolean other) {
    private static Candidate item(String itemKey) {
      return new Candidate(Objects.requireNonNull(itemKey, "itemKey"), false);
    }

    private static Candidate otherItems() {
      return new Candidate(null, true);
    }
  }

  private static final class Components {
    private final Map<String, Integer> componentByNode;
    private final List<Integer> componentSizes;

    private Components(Map<String, Integer> componentByNode, List<Integer> componentSizes) {
      this.componentByNode = Map.copyOf(componentByNode);
      this.componentSizes = List.copyOf(componentSizes);
    }

    private static Components find(Map<String, List<String>> adjacency) {
      Tarjan tarjan = new Tarjan(adjacency);
      for (String node : adjacency.keySet()) {
        if (!tarjan.indexByNode.containsKey(node)) {
          tarjan.visit(node);
        }
      }
      return new Components(tarjan.componentByNode, tarjan.componentSizes);
    }

    private int componentOf(String node) {
      return componentByNode.getOrDefault(node, -1);
    }

    private int sizeOf(int component) {
      return component < 0 || component >= componentSizes.size()
          ? 0
          : componentSizes.get(component);
    }
  }

  private static final class Tarjan {
    private final Map<String, List<String>> adjacency;
    private final Map<String, Integer> indexByNode = new HashMap<>();
    private final Map<String, Integer> lowLinkByNode = new HashMap<>();
    private final ArrayDeque<String> stack = new ArrayDeque<>();
    private final Set<String> onStack = new HashSet<>();
    private final Map<String, Integer> componentByNode = new HashMap<>();
    private final List<Integer> componentSizes = new ArrayList<>();
    private int nextIndex;

    private Tarjan(Map<String, List<String>> adjacency) {
      Map<String, List<String>> copy = new HashMap<>();
      adjacency.forEach(
          (node, targets) -> copy.put(node, targets == null ? List.of() : List.copyOf(targets)));
      this.adjacency = Collections.unmodifiableMap(copy);
    }

    private void visit(String node) {
      int index = nextIndex++;
      indexByNode.put(node, index);
      lowLinkByNode.put(node, index);
      stack.push(node);
      onStack.add(node);

      for (String target : adjacency.getOrDefault(node, List.of())) {
        if (!indexByNode.containsKey(target)) {
          visit(target);
          lowLinkByNode.put(node, Math.min(lowLinkByNode.get(node), lowLinkByNode.get(target)));
        } else if (onStack.contains(target)) {
          lowLinkByNode.put(node, Math.min(lowLinkByNode.get(node), indexByNode.get(target)));
        }
      }

      if (!lowLinkByNode.get(node).equals(indexByNode.get(node))) {
        return;
      }
      int component = componentSizes.size();
      int size = 0;
      while (!stack.isEmpty()) {
        String member = stack.pop();
        onStack.remove(member);
        componentByNode.put(member, component);
        size++;
        if (member.equals(node)) {
          break;
        }
      }
      componentSizes.add(size);
    }
  }
}
