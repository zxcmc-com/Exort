package com.zxcmc.exort.bus.loop;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import java.util.Set;

public final class BusLoopGuard {
  private BusLoopGuard() {}

  public static boolean filtersIntersect(
      BusMode modeA,
      Set<String> keysA,
      BusType typeA,
      boolean importSideSensitiveA,
      BusMode modeB,
      Set<String> keysB,
      BusType typeB,
      boolean importSideSensitiveB) {
    if (modeA == BusMode.DISABLED || modeB == BusMode.DISABLED) return false;
    // If inventory is side-sensitive, import ALL + export ALL cannot loop by design (slot
    // separation).
    if (modeA == BusMode.ALL
        && typeA == BusType.IMPORT
        && importSideSensitiveA
        && modeB == BusMode.ALL
        && typeB == BusType.EXPORT) {
      return false;
    }
    if (modeB == BusMode.ALL
        && typeB == BusType.IMPORT
        && importSideSensitiveB
        && modeA == BusMode.ALL
        && typeA == BusType.EXPORT) {
      return false;
    }
    BusMode a = modeA;
    BusMode b = modeB;
    if (typeA == BusType.IMPORT && a == BusMode.ALL && importSideSensitiveA) {
      a = BusMode.WHITELIST; // treat as empty whitelist (safe against loops)
    }
    if (typeB == BusType.IMPORT && b == BusMode.ALL && importSideSensitiveB) {
      b = BusMode.WHITELIST;
    }
    if (a == BusMode.ALL || b == BusMode.ALL) return true;
    Set<String> fa = keysA == null ? Set.of() : keysA;
    Set<String> fb = keysB == null ? Set.of() : keysB;
    if (a == BusMode.BLACKLIST && b == BusMode.BLACKLIST) return true; // conservative
    if (a == BusMode.WHITELIST && b == BusMode.WHITELIST) {
      for (String key : fa) {
        if (fb.contains(key)) return true;
      }
      return false;
    }
    if (a == BusMode.WHITELIST && b == BusMode.BLACKLIST) {
      for (String key : fa) {
        if (!fb.contains(key)) return true;
      }
      return false;
    }
    if (a == BusMode.BLACKLIST && b == BusMode.WHITELIST) {
      for (String key : fb) {
        if (!fa.contains(key)) return true;
      }
      return false;
    }
    return false;
  }
}
