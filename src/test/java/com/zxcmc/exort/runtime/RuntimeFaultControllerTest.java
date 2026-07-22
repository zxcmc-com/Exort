package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeFaultControllerTest {
  @AfterEach
  void clearProperties() {
    System.clearProperty(RuntimeFaultController.ENABLED_PROPERTY);
    System.clearProperty(RuntimeFaultController.STAGE_PROPERTY);
  }

  @Test
  void matchingFaultIsOneShotAndRecordsZeroPostCleanupCensus() {
    RuntimeFaultController controller = new RuntimeFaultController();
    RuntimeGenerationScope generation =
        new RuntimeGenerationScope(BukkitTestDoubles.plugin(), controller);
    RuntimeHandle.Scope scope = RuntimeHandle.scope(generation);
    scope.own("candidate service", () -> {});
    System.setProperty(RuntimeFaultController.ENABLED_PROPERTY, "true");
    System.setProperty(
        RuntimeFaultController.STAGE_PROPERTY, RuntimeConstructionStage.SERVICES_COMPLETE.name());

    IllegalStateException injected =
        assertThrows(
            IllegalStateException.class,
            () -> controller.checkpoint(RuntimeConstructionStage.SERVICES_COMPLETE, generation));
    assertEquals("Injected Exort runtime failure at SERVICES_COMPLETE", injected.getMessage());
    assertNull(System.getProperty(RuntimeFaultController.STAGE_PROPERTY));

    scope.close();
    RuntimeFaultController.InjectedFailure failure = controller.diagnostics().lastInjectedFailure();
    assertNotNull(failure);
    assertEquals(RuntimeConstructionStage.SERVICES_COMPLETE, failure.stage());
    assertEquals(1, failure.preCleanup().totalResources());
    assertEquals(0, failure.postCleanup().totalResources());
    assertEquals(true, failure.postCleanup().cleanupFinished());

    controller.checkpoint(RuntimeConstructionStage.SERVICES_COMPLETE, generation);
  }

  @Test
  void disabledControllerIgnoresConfiguredStage() {
    RuntimeFaultController controller = new RuntimeFaultController();
    RuntimeGenerationScope generation =
        new RuntimeGenerationScope(BukkitTestDoubles.plugin(), controller);
    System.setProperty(
        RuntimeFaultController.STAGE_PROPERTY,
        RuntimeConstructionStage.GENERATION_CORE_PREPARATION.name());

    controller.checkpoint(RuntimeConstructionStage.GENERATION_CORE_PREPARATION, generation);

    assertEquals(
        RuntimeConstructionStage.GENERATION_CORE_PREPARATION.name(),
        System.getProperty(RuntimeFaultController.STAGE_PROPERTY));
    RuntimeHandle.scope(generation).close();
  }
}
