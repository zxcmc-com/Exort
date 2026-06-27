package com.zxcmc.exort.breaking.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.breaking.BreakType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisplayBreakAnimationSenderTest {
  @Test
  void wireBreakStagesCollapseToThreeModelStages() {
    assertEquals(0, DisplayBreakAnimationSender.modelStageFor(BreakType.WIRE, 0));
    assertEquals(0, DisplayBreakAnimationSender.modelStageFor(BreakType.WIRE, 4));
    assertEquals(1, DisplayBreakAnimationSender.modelStageFor(BreakType.WIRE, 5));
    assertEquals(1, DisplayBreakAnimationSender.modelStageFor(BreakType.WIRE, 7));
    assertEquals(2, DisplayBreakAnimationSender.modelStageFor(BreakType.WIRE, 8));
    assertEquals(2, DisplayBreakAnimationSender.modelStageFor(BreakType.WIRE, 9));
  }

  @Test
  void nonWireBreakStagesKeepOriginalModelStage() {
    for (BreakType type :
        List.of(BreakType.STORAGE, BreakType.TERMINAL, BreakType.MONITOR, BreakType.BUS)) {
      assertEquals(0, DisplayBreakAnimationSender.modelStageFor(type, 0), type.name());
      assertEquals(5, DisplayBreakAnimationSender.modelStageFor(type, 5), type.name());
      assertEquals(9, DisplayBreakAnimationSender.modelStageFor(type, 9), type.name());
    }
  }
}
