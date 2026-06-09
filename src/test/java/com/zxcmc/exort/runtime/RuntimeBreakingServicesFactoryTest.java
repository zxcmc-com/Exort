package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class RuntimeBreakingServicesFactoryTest {
  @Test
  void packetBreakingBackendRequiresPacketEnhancements() {
    assertTrue(
        RuntimeBreakingServicesFactory.shouldRegisterCustomBreakingPackets(packetEnhancements()));
    assertFalse(RuntimeBreakingServicesFactory.shouldRegisterCustomBreakingPackets(null));
  }

  private static PacketEnhancements packetEnhancements() {
    return (PacketEnhancements)
        Proxy.newProxyInstance(
            RuntimeBreakingServicesFactoryTest.class.getClassLoader(),
            new Class<?>[] {PacketEnhancements.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == Boolean.TYPE) return false;
    if (returnType == Void.TYPE) return null;
    return null;
  }
}
