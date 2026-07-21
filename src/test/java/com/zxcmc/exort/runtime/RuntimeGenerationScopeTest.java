package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeGenerationScopeTest {
  @Test
  void guardedCallbackCannotActivateAfterGenerationCloses() {
    RuntimeGenerationScope scope = new RuntimeGenerationScope(BukkitTestDoubles.plugin());
    AtomicInteger calls = new AtomicInteger();
    Runnable guarded = scope.guard(calls::incrementAndGet);

    guarded.run();
    scope.close();
    guarded.run();

    assertEquals(1, calls.get());
    assertThrows(IllegalStateException.class, () -> scope.runTask(calls::incrementAndGet));
  }
}
