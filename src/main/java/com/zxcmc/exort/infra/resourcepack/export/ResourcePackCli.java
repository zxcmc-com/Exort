package com.zxcmc.exort.infra.resourcepack.export;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class ResourcePackCli {
  private static final Logger LOGGER = Logger.getLogger(ResourcePackCli.class.getName());

  private ResourcePackCli() {}

  public static void main(String[] args) {
    Path sourceRoot = args.length > 0 ? Path.of(args[0]) : Path.of("src/main/resources");
    File outputDir = args.length > 1 ? new File(args[1]) : new File("build/exort-resource-pack");

    PackExporter.Result result = PackExporter.exportPack(sourceRoot, "pack/", outputDir, LOGGER);
    if (!result.available()) {
      System.err.println("Resource pack export failed.");
      System.exit(2);
    }

    System.out.println("rawPack=" + result.rawFile().getPath());
    System.out.println("pack=" + result.outputFile().getPath());
    System.out.println("sha1=" + result.outputSha1());
    System.out.println("entries=" + result.entryCount());
  }
}
