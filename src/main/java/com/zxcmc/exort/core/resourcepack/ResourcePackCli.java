package com.zxcmc.exort.core.resourcepack;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class ResourcePackCli {
  private static final Logger LOGGER = Logger.getLogger(ResourcePackCli.class.getName());

  private ResourcePackCli() {}

  public static void main(String[] args) {
    Path sourceRoot = args.length > 0 ? Path.of(args[0]) : Path.of("src/main/resources");
    File outputDir = args.length > 1 ? new File(args[1]) : new File("build/exort-resource-pack");
    boolean obfuscate = args.length <= 2 || Boolean.parseBoolean(args[2]);

    PackExporter.Result result =
        PackExporter.exportPack(sourceRoot, "pack/", outputDir, obfuscate, LOGGER);
    if (!result.available()) {
      System.err.println("Resource pack export failed.");
      System.exit(2);
    }

    System.out.println("rawPack=" + result.rawFile().getPath());
    System.out.println("pack=" + result.outputFile().getPath());
    System.out.println("sha1=" + result.outputSha1());
    System.out.println("entries=" + result.entryCount());
    System.out.println("obfuscated=" + result.obfuscated());
  }
}
