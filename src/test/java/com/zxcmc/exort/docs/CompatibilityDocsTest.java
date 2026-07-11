package com.zxcmc.exort.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class CompatibilityDocsTest {
  @Test
  void worldEditMarkerExtentClassNameUsesCurrentPackage() throws Exception {
    List<Path> files =
        List.of(Path.of("docs/compatibility.ru.md"), Path.of("docs/compatibility.en.md"));
    long existingFiles = files.stream().filter(Files::isRegularFile).count();
    assertTrue(
        existingFiles == 0 || existingFiles == files.size(),
        "Compatibility docs must either both exist or both be absent");
    Assumptions.assumeTrue(existingFiles == files.size(), "Compatibility docs are not published");

    for (Path file : files) {
      String text = Files.readString(file);

      assertFalse(
          text.contains("com.zxcmc.exort.core.worldedit"),
          file + " still references stale WorldEdit package");
    }
  }
}
