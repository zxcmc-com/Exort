package com.zxcmc.exort.chunkloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RotatingChunkLoaderAuditFileWriter implements ChunkLoaderAuditFileWriter {
  private static final long CLOSE_TIMEOUT_SECONDS = 2L;
  private static final int QUEUE_CAPACITY = 2_048;

  private final Path file;
  private final long maxSizeBytes;
  private final int maxFiles;
  private final Logger logger;
  private final ExecutorService executor;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean warned = new AtomicBoolean();

  private RotatingChunkLoaderAuditFileWriter(
      Path file, long maxSizeBytes, int maxFiles, Logger logger) {
    this.file = Objects.requireNonNull(file, "file");
    this.maxSizeBytes = Math.max(1L, maxSizeBytes);
    this.maxFiles = Math.max(1, maxFiles);
    this.logger = logger;
    this.executor =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            task -> {
              Thread thread = new Thread(task, "Exort-ChunkLoaderAuditLog");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  public static ChunkLoaderAuditFileWriter create(
      Path dataFolder, ChunkLoaderAuditFileConfig config, Logger logger) {
    if (config == null || !config.enabled()) {
      return ChunkLoaderAuditFileWriter.noop();
    }
    Path target = resolveSafePath(dataFolder, config.path(), logger);
    return new RotatingChunkLoaderAuditFileWriter(
        target, config.maxSizeBytes(), config.maxFiles(), logger);
  }

  static Path resolveSafePath(Path dataFolder, String rawPath, Logger logger) {
    Path base = dataFolder == null ? Path.of(".") : dataFolder;
    Path absoluteBase = base.toAbsolutePath().normalize();
    Path requested;
    try {
      requested =
          rawPath == null || rawPath.isBlank()
              ? Path.of(ChunkLoaderAuditFileConfig.DEFAULT_PATH)
              : Path.of(rawPath.trim());
    } catch (InvalidPathException invalidPath) {
      warnUnsafePath(logger, rawPath);
      return absoluteBase.resolve(ChunkLoaderAuditFileConfig.DEFAULT_PATH).normalize();
    }
    if (requested.isAbsolute()) {
      warnUnsafePath(logger, rawPath);
      return absoluteBase.resolve(ChunkLoaderAuditFileConfig.DEFAULT_PATH).normalize();
    }
    Path resolved = absoluteBase.resolve(requested).normalize();
    if (!resolved.startsWith(absoluteBase)) {
      warnUnsafePath(logger, rawPath);
      return absoluteBase.resolve(ChunkLoaderAuditFileConfig.DEFAULT_PATH).normalize();
    }
    return resolved;
  }

  private static void warnUnsafePath(Logger logger, String rawPath) {
    if (logger != null) {
      logger.warning(
          "Unsafe chunkLoader.audit.file.path '"
              + rawPath
              + "'; using "
              + ChunkLoaderAuditFileConfig.DEFAULT_PATH
              + ".");
    }
  }

  @Override
  public void write(String line) {
    if (line == null || closed.get()) {
      return;
    }
    try {
      executor.execute(() -> writeNow(line));
    } catch (RejectedExecutionException ignored) {
      warnOnce(null);
    }
  }

  private void writeNow(String line) {
    byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      rotateIfNeeded(bytes.length);
      Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException | RuntimeException e) {
      warnOnce(e);
    }
  }

  private void rotateIfNeeded(long incomingBytes) throws IOException {
    if (!Files.exists(file) || Files.size(file) + incomingBytes <= maxSizeBytes) {
      return;
    }
    Files.deleteIfExists(rotatedPath(maxFiles));
    for (int index = maxFiles - 1; index >= 1; index--) {
      Path source = rotatedPath(index);
      if (Files.exists(source)) {
        Files.move(source, rotatedPath(index + 1), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    Files.move(file, rotatedPath(1), StandardCopyOption.REPLACE_EXISTING);
  }

  private Path rotatedPath(int index) {
    return file.resolveSibling(file.getFileName() + "." + index);
  }

  private void warnOnce(Throwable error) {
    if (logger != null && warned.compareAndSet(false, true)) {
      if (error == null) {
        logger.warning("Failed to queue Chunk Loader audit log write.");
      } else {
        logger.log(Level.WARNING, "Failed to write Chunk Loader audit log.", error);
      }
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
  }
}
