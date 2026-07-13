package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class PacketPlayerLanguageSnapshotTest {
  @Test
  void publishesImmutableGenerations() {
    PacketPlayerLanguageSnapshot snapshot = new PacketPlayerLanguageSnapshot();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    snapshot.replace(Map.of(first, "en_us"));
    PacketPlayerLanguageSnapshot.State initial = snapshot.current();
    snapshot.update(second, "de_de");
    PacketPlayerLanguageSnapshot.State updated = snapshot.current();
    snapshot.remove(first);

    assertTrue(updated.generation() > initial.generation());
    assertEquals(Map.of(first, "en_us"), initial.languages());
    assertEquals("de_de", snapshot.language(second, "ru_ru"));
    assertEquals("ru_ru", snapshot.language(first, "ru_ru"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> updated.languages().put(UUID.randomUUID(), "ru_ru"));
  }

  @Test
  void readersNeverObserveMutableOrPartiallyPublishedState() throws Exception {
    PacketPlayerLanguageSnapshot snapshot = new PacketPlayerLanguageSnapshot();
    UUID playerId = UUID.randomUUID();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      Future<?> writer =
          executor.submit(
              () -> {
                await(start);
                for (int i = 0; i < 5_000; i++) {
                  snapshot.update(playerId, (i & 1) == 0 ? "en_us" : "de_de");
                  if (i % 17 == 0) {
                    snapshot.remove(playerId);
                  }
                }
              });
      Future<?>[] readers = new Future<?>[3];
      for (int reader = 0; reader < readers.length; reader++) {
        readers[reader] =
            executor.submit(
                () -> {
                  await(start);
                  for (int i = 0; i < 10_000; i++) {
                    PacketPlayerLanguageSnapshot.State state = snapshot.current();
                    state.languages().forEach((id, language) -> assertEquals(playerId, id));
                    assertTrue(state.generation() >= 0L);
                    snapshot.language(playerId, "ru_ru");
                  }
                });
      }

      start.countDown();
      assertDoesNotThrow(() -> writer.get());
      for (Future<?> reader : readers) {
        assertDoesNotThrow(() -> reader.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
