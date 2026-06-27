package com.zxcmc.exort.integration.resourcepack.oraxen;

import com.zxcmc.exort.infra.logging.ExortLog;
import io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent;
import io.th0rgal.oraxen.utils.VirtualFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class OraxenPackGeneratedListener implements Listener {
  private static final String CHORUS_BLOCKSTATE_PARENT = "assets/minecraft/blockstates";
  private static final String CHORUS_BLOCKSTATE_NAME = "chorus_plant.json";

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onOraxenPackGenerated(OraxenPackGeneratedEvent event) {
    mergeChorusBlockstates(event.getOutput());
  }

  static void mergeChorusBlockstates(List<VirtualFile> output) {
    List<IndexedVirtualFile> chorusFiles = chorusBlockstateFiles(output);
    if (chorusFiles.size() <= 1) {
      return;
    }

    List<OraxenChorusBlockstateMerger.MergeInput> inputs = new ArrayList<>(chorusFiles.size());
    for (IndexedVirtualFile indexed : chorusFiles) {
      try {
        inputs.add(
            new OraxenChorusBlockstateMerger.MergeInput(
                indexed.file().getPath(), readAndRestore(indexed.file())));
      } catch (IOException error) {
        ExortLog.warn(
            "[Oraxen] Could not read duplicate chorus_plant.json; keeping provider output"
                + " unchanged: "
                + error.getMessage());
        return;
      }
    }

    OraxenChorusBlockstateMerger.MergeResult result = OraxenChorusBlockstateMerger.merge(inputs);
    if (!result.success()) {
      ExortLog.warn(
          "[Oraxen] Could not merge duplicate chorus_plant.json files; keeping provider output"
              + " unchanged: "
              + result.error());
      return;
    }
    if (!result.changed()) {
      return;
    }

    int firstIndex = chorusFiles.stream().mapToInt(IndexedVirtualFile::index).min().orElse(0);
    List<VirtualFile> duplicateFiles = chorusFiles.stream().map(IndexedVirtualFile::file).toList();
    output.removeIf(duplicateFiles::contains);
    output.add(
        Math.min(firstIndex, output.size()),
        new VirtualFile(
            CHORUS_BLOCKSTATE_PARENT,
            CHORUS_BLOCKSTATE_NAME,
            new ByteArrayInputStream(result.content().getBytes(StandardCharsets.UTF_8))));

    if (result.conflicts() > 0) {
      ExortLog.warn(
          "[Oraxen] Merged Exort chorus_plant.json into Oraxen pack with "
              + result.conflicts()
              + " conflicting state(s); Oraxen variants were kept.");
    }
  }

  private static List<IndexedVirtualFile> chorusBlockstateFiles(List<VirtualFile> output) {
    List<IndexedVirtualFile> files = new ArrayList<>();
    for (int i = 0; i < output.size(); i++) {
      VirtualFile file = output.get(i);
      if (OraxenChorusBlockstateMerger.CHORUS_BLOCKSTATE_PATH.equals(file.getPath())) {
        files.add(new IndexedVirtualFile(i, file));
      }
    }
    files.sort(Comparator.comparingInt(IndexedVirtualFile::index));
    return files;
  }

  private static String readAndRestore(VirtualFile file) throws IOException {
    if (file.getInputStream() == null) {
      throw new IOException(file.getPath() + " has no input stream");
    }
    byte[] bytes;
    try (var input = file.getInputStream()) {
      bytes = input.readAllBytes();
    }
    file.setInputStream(new ByteArrayInputStream(bytes));
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private record IndexedVirtualFile(int index, VirtualFile file) {}
}
