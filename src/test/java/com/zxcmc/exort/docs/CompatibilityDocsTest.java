package com.zxcmc.exort.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CompatibilityDocsTest {
  @Test
  void worldEditMarkerExtentClassNameUsesCurrentPackage() throws Exception {
    for (String file : new String[] {"docs/compatibility.ru.md", "docs/compatibility.en.md"}) {
      String text = Files.readString(Path.of(file));

      assertFalse(
          text.contains("com.zxcmc.exort.core.worldedit"),
          file + " still references stale WorldEdit package");
    }
  }
}
