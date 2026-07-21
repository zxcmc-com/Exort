package com.zxcmc.exort.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SqliteDatabaseCloseTest {
  @Test
  void connectionIsClosedOnceAfterOrderlyExecutorTermination() throws Exception {
    CloseProbe probe = new CloseProbe();
    SqliteDatabase database = new SqliteDatabase(null, 1_000L, 1_000L);
    database.installConnectionForTesting(probe.connection());

    database.run("completed operation", () -> {}).join();
    database.close();

    assertTrue(probe.closed.await(1L, TimeUnit.SECONDS));
    assertEquals(1, probe.closeCount.get());
    database.close();
    assertEquals(1, probe.closeCount.get());
    assertThrows(CompletionException.class, () -> database.run("late operation", () -> {}).join());
  }

  @Test
  void stubbornWorkerKeepsOwnershipUntilItActuallyStops() throws Exception {
    CloseProbe probe = new CloseProbe();
    SqliteDatabase database = new SqliteDatabase(null, 25L, 25L);
    database.installConnectionForTesting(probe.connection());
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    var running =
        database.run(
            "stubborn operation",
            () -> {
              started.countDown();
              boolean released = false;
              while (!released) {
                try {
                  released = release.await(1L, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                  // Deliberately model a JDBC driver call that ignores interruption.
                }
              }
            });
    assertTrue(started.await(1L, TimeUnit.SECONDS));

    long startedAt = System.nanoTime();
    database.close();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

    assertTrue(elapsedMillis < 1_000L, "close must remain bounded");
    assertEquals(0, probe.closeCount.get(), "caller must not close a connection in active use");
    assertFalse(running.isDone() && !running.isCompletedExceptionally());

    release.countDown();
    assertTrue(probe.closed.await(1L, TimeUnit.SECONDS));
    assertEquals(1, probe.closeCount.get());
  }

  private static final class CloseProbe {
    private final AtomicInteger closeCount = new AtomicInteger();
    private final CountDownLatch closed = new CountDownLatch(1);

    private Connection connection() {
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                  closeCount.incrementAndGet();
                  closed.countDown();
                  return null;
                }
                if (method.getName().equals("isClosed")) {
                  return closeCount.get() > 0;
                }
                if (method.getName().equals("unwrap")) {
                  throw new RejectedExecutionException("not a wrapper");
                }
                return defaultValue(method.getReturnType());
              });
    }

    private static Object defaultValue(Class<?> type) {
      if (!type.isPrimitive()) return null;
      if (type == boolean.class) return false;
      if (type == char.class) return '\0';
      if (type == byte.class) return (byte) 0;
      if (type == short.class) return (short) 0;
      if (type == int.class) return 0;
      if (type == long.class) return 0L;
      if (type == float.class) return 0.0F;
      if (type == double.class) return 0.0D;
      return null;
    }
  }
}
