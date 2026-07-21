package com.zxcmc.exort.infra.db;

import com.zxcmc.exort.debug.PerfStats;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the single SQLite connection and its ordered bounded executor. */
final class SqliteDatabase implements AutoCloseable {
  private static final long CLOSE_TIMEOUT_SECONDS = 15L;
  private static final long FORCED_CLOSE_TIMEOUT_SECONDS = 2L;
  private static final int QUEUE_CAPACITY = 8_192;

  private final Logger logger;
  private final AtomicBoolean closing = new AtomicBoolean();
  private final AtomicBoolean connectionClosed = new AtomicBoolean();
  private final AtomicInteger queuedOperations = new AtomicInteger();
  private final Set<CompletableFuture<?>> pendingFutures = ConcurrentHashMap.newKeySet();
  private final ThreadPoolExecutor executor;
  private final long closeTimeoutMillis;
  private final long forcedCloseTimeoutMillis;
  private volatile Connection connection;

  SqliteDatabase(Logger logger) {
    this(
        logger,
        TimeUnit.SECONDS.toMillis(CLOSE_TIMEOUT_SECONDS),
        TimeUnit.SECONDS.toMillis(FORCED_CLOSE_TIMEOUT_SECONDS));
  }

  SqliteDatabase(Logger logger, long closeTimeoutMillis, long forcedCloseTimeoutMillis) {
    this.logger = logger;
    if (closeTimeoutMillis < 0L || forcedCloseTimeoutMillis < 0L) {
      throw new IllegalArgumentException("database close timeouts must be non-negative");
    }
    this.closeTimeoutMillis = closeTimeoutMillis;
    this.forcedCloseTimeoutMillis = forcedCloseTimeoutMillis;
    this.executor =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            runnable -> {
              Thread thread = new Thread(runnable, "exort-db");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()) {
          @Override
          protected void terminated() {
            try {
              closeOwnedConnection();
            } finally {
              super.terminated();
            }
          }
        };
  }

  void init(File file, SchemaInitializer initializer) throws SQLException {
    if (closing.get()) {
      throw new IllegalStateException("Database is already closing");
    }
    File parent = file.getParentFile();
    if (parent != null) {
      try {
        Files.createDirectories(parent.toPath());
      } catch (IOException error) {
        throw new SQLException("Failed to create database directory " + parent, error);
      }
    }
    Connection opened = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    connection = opened;
    try {
      applyPragmas(opened);
      initializer.initialize();
    } catch (SQLException | RuntimeException error) {
      try {
        opened.close();
      } catch (SQLException closeFailure) {
        error.addSuppressed(closeFailure);
      } finally {
        connection = null;
      }
      throw error;
    }
  }

  Connection connection() {
    Connection current = connection;
    if (current == null) {
      throw new IllegalStateException("Database connection is not initialized");
    }
    return current;
  }

  void installConnectionForTesting(Connection connection) {
    if (this.connection != null || closing.get()) {
      throw new IllegalStateException("Database connection is already initialized or closing");
    }
    this.connection = java.util.Objects.requireNonNull(connection, "connection");
  }

  boolean isClosing() {
    return closing.get();
  }

  int queuedOperations() {
    return queuedOperations.get();
  }

  CompletableFuture<Void> run(String action, Runnable task) {
    return supply(
        action,
        () -> {
          task.run();
          return null;
        });
  }

  <T> CompletableFuture<T> supply(String action, Supplier<T> task) {
    if (closing.get()) {
      return rejected(action);
    }
    CompletableFuture<T> future = new CompletableFuture<>();
    pendingFutures.add(future);
    incrementQueueDepth();
    try {
      executor.execute(
          () -> {
            T result = null;
            Throwable failure = null;
            try {
              result = PerfStats.measure(PerfStats.Area.STORAGE_DB, task);
            } catch (RuntimeException thrown) {
              failure = unwrapCompletion(thrown);
              if (!(failure instanceof SQLException)) {
                log(Level.SEVERE, "Async database task failed: " + action, failure);
              }
            } catch (Error fatal) {
              future.completeExceptionally(fatal);
              throw fatal;
            } finally {
              decrementQueueDepth();
              pendingFutures.remove(future);
            }
            if (failure == null) {
              future.complete(result);
            } else {
              future.completeExceptionally(failure);
            }
          });
    } catch (RejectedExecutionException error) {
      decrementQueueDepth();
      pendingFutures.remove(future);
      log(Level.WARNING, "Rejected async database task: " + action, error);
      future.completeExceptionally(error);
    }
    return future;
  }

  boolean execute(String action, Runnable task) {
    if (closing.get()) return false;
    incrementQueueDepth();
    try {
      executor.execute(
          () -> {
            try {
              PerfStats.measure(
                  PerfStats.Area.STORAGE_DB,
                  () -> {
                    task.run();
                    return null;
                  });
            } catch (RuntimeException error) {
              log(Level.WARNING, "Async database task failed: " + action, error);
            } finally {
              decrementQueueDepth();
            }
          });
      return true;
    } catch (RejectedExecutionException error) {
      decrementQueueDepth();
      log(Level.WARNING, "Rejected async database task: " + action, error);
      return false;
    }
  }

  private <T> CompletableFuture<T> rejected(String action) {
    RejectedExecutionException error =
        new RejectedExecutionException("Database is closing; rejected async task: " + action);
    log(Level.WARNING, "Rejected async database task: " + action, error);
    return CompletableFuture.failedFuture(error);
  }

  private static void applyPragmas(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA journal_mode=WAL");
      statement.execute("PRAGMA synchronous=NORMAL");
      statement.execute("PRAGMA foreign_keys=ON");
      statement.execute("PRAGMA busy_timeout=5000");
    }
  }

  private void incrementQueueDepth() {
    PerfStats.setGauge("storage-db.queueDepth", queuedOperations.incrementAndGet());
  }

  private void decrementQueueDepth() {
    PerfStats.setGauge(
        "storage-db.queueDepth",
        queuedOperations.updateAndGet(current -> Math.max(0, current - 1)));
  }

  private static Throwable unwrapCompletion(Throwable thrown) {
    Throwable current = thrown;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private void log(Level level, String message, Throwable thrown) {
    if (logger != null) {
      logger.log(level, message, thrown);
    }
  }

  @Override
  public void close() {
    if (!closing.compareAndSet(false, true)) {
      return;
    }
    executor.shutdown();
    boolean terminated = false;
    try {
      terminated = executor.awaitTermination(closeTimeoutMillis, TimeUnit.MILLISECONDS);
      if (!terminated) {
        if (logger != null) {
          logger.warning(
              "Database executor did not stop within "
                  + closeTimeoutMillis
                  + "ms; forcing shutdown");
        }
        executor.shutdownNow();
        terminated = executor.awaitTermination(forcedCloseTimeoutMillis, TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log(Level.WARNING, "Interrupted while waiting for database shutdown", error);
      executor.shutdownNow();
    }
    CancellationException closed =
        new CancellationException("Database closed before queued operation completed");
    pendingFutures.forEach(future -> future.completeExceptionally(closed));
    pendingFutures.clear();
    queuedOperations.set(0);
    PerfStats.setGauge("storage-db.queueDepth", 0);
    if (!terminated) {
      log(
          Level.SEVERE,
          "Database worker ignored shutdown; leaving its connection open until the daemon exits to"
              + " avoid a concurrent JDBC close",
          null);
    }
  }

  private void closeOwnedConnection() {
    if (!connectionClosed.compareAndSet(false, true)) {
      return;
    }
    Connection owned = connection;
    if (owned != null) {
      try {
        owned.close();
      } catch (SQLException error) {
        log(Level.SEVERE, "Failed to close database", error);
      } finally {
        connection = null;
      }
    }
  }

  @FunctionalInterface
  interface SchemaInitializer {
    void initialize() throws SQLException;
  }
}
