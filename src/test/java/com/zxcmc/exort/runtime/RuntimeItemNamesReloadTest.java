package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxcmc.exort.i18n.ItemNameService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeItemNamesReloadTest {
  @Test
  void startsTransitionLazilyAndOnlyOnce() {
    AtomicInteger starts = new AtomicInteger();
    CompletableFuture<ItemNameService.Status> expected = new CompletableFuture<>();
    RuntimeItemNamesReload reload =
        new RuntimeItemNamesReload(
            () -> {
              starts.incrementAndGet();
              return expected;
            });

    CompletableFuture<ItemNameService.Status> first = reload.start();
    CompletableFuture<ItemNameService.Status> second = reload.start();

    assertSame(first, second);
    org.junit.jupiter.api.Assertions.assertEquals(1, starts.get());
    expected.complete(null);
    assertNull(first.join());
  }

  @Test
  void synchronousStarterFailureIsPublishedThroughTheSharedFuture() {
    RuntimeItemNamesReload reload =
        new RuntimeItemNamesReload(
            () -> {
              throw new IllegalStateException("reload failed");
            });

    CompletableFuture<ItemNameService.Status> first = reload.start();

    assertSame(first, reload.start());
    assertThrows(CompletionException.class, first::join);
  }
}
