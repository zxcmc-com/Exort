package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.carrier.Carriers;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class EmbeddedChorusfixControllerTest {
  @Test
  void statusMatrixHonorsConfigExternalPaperCarrierAndProviders() {
    EmbeddedChorusfixConfig enabled = config(true);
    EmbeddedChorusfixConfig disabled = config(false);

    assertEquals(
        EmbeddedChorusfixStatus.EXTERNAL,
        EmbeddedChorusfixController.evaluate(
            disabled, true, true, Carriers.CHORUS_MATERIAL, List.of("Nexo")));
    assertEquals(
        EmbeddedChorusfixStatus.EXTERNAL,
        EmbeddedChorusfixController.evaluate(
            enabled, true, true, Carriers.CHORUS_MATERIAL, List.of("Nexo")));
    assertEquals(
        EmbeddedChorusfixStatus.DISABLED,
        EmbeddedChorusfixController.evaluate(
            disabled, false, true, Carriers.CHORUS_MATERIAL, List.of("Nexo")));
    assertEquals(
        EmbeddedChorusfixStatus.INACTIVE,
        EmbeddedChorusfixController.evaluate(
            enabled, false, false, Carriers.CHORUS_MATERIAL, List.of()));
    assertEquals(
        EmbeddedChorusfixStatus.INACTIVE,
        EmbeddedChorusfixController.evaluate(
            enabled, false, false, Carriers.CHORUS_MATERIAL, List.of("Nexo")));
    assertEquals(
        EmbeddedChorusfixStatus.INACTIVE,
        EmbeddedChorusfixController.evaluate(enabled, false, true, Material.BARRIER, List.of()));
    assertEquals(
        EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER,
        EmbeddedChorusfixController.evaluate(
            enabled, false, true, Carriers.CHORUS_MATERIAL, List.of("ItemsAdder")));
    assertEquals(
        EmbeddedChorusfixStatus.EMBEDDED,
        EmbeddedChorusfixController.evaluate(
            enabled, false, true, Carriers.CHORUS_MATERIAL, List.of()));
  }

  @Test
  void knownProviderListMatchesRiskyChorusBlockProviders() {
    assertTrue(EmbeddedChorusfixController.KNOWN_CHORUS_PROVIDER_PLUGINS.contains("Nexo"));
    assertTrue(EmbeddedChorusfixController.KNOWN_CHORUS_PROVIDER_PLUGINS.contains("ItemsAdder"));
    assertTrue(EmbeddedChorusfixController.KNOWN_CHORUS_PROVIDER_PLUGINS.contains("Oraxen"));
  }

  @Test
  void blockedByProviderWarningPointsToManualChorusfixDownload() {
    String warning = EmbeddedChorusfixController.blockedByProviderWarning(List.of("Nexo"));

    assertTrue(warning.contains("Nexo is enabled without the external Chorusfix plugin"));
    assertTrue(warning.contains(EmbeddedChorusfixController.CHORUSFIX_DOWNLOAD_URL));
    assertTrue(warning.contains("https://github.com/zxcmc-com/Chorusfix/releases/latest"));
  }

  @Test
  void blockedProviderWarningStateEmitsOnceForCurrentProviders() {
    var warnings = new EmbeddedChorusfixController.BlockedProviderWarningState();

    assertTrue(warnings.shouldWarn(EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, List.of("Nexo")));
    assertFalse(warnings.shouldWarn(EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, List.of("Nexo")));
    assertTrue(
        warnings.shouldWarn(
            EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, List.of("Nexo", "Oraxen")));
    assertFalse(
        warnings.shouldWarn(
            EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, List.of("Nexo", "Oraxen")));
  }

  @Test
  void blockedProviderWarningStateResetsWhenExternalChorusfixTakesOver() {
    var warnings = new EmbeddedChorusfixController.BlockedProviderWarningState();

    assertTrue(warnings.shouldWarn(EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, List.of("Nexo")));
    assertFalse(warnings.shouldWarn(EmbeddedChorusfixStatus.EXTERNAL, List.of()));
    assertTrue(warnings.shouldWarn(EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, List.of("Nexo")));
  }

  @Test
  void startupOrderWithExternalChorusfixAvoidsTransientProviderWarning() {
    var warnings = new EmbeddedChorusfixController.BlockedProviderWarningState();
    EmbeddedChorusfixConfig enabled = config(true);

    EmbeddedChorusfixStatus embedded =
        EmbeddedChorusfixController.evaluate(
            enabled, false, true, Carriers.CHORUS_MATERIAL, List.of());
    assertFalse(warnings.shouldWarn(embedded, List.of()));

    EmbeddedChorusfixStatus providerLoadedBeforeChorusfix =
        EmbeddedChorusfixController.evaluate(
            enabled, false, true, Carriers.CHORUS_MATERIAL, List.of("Nexo"));
    assertEquals(EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, providerLoadedBeforeChorusfix);

    EmbeddedChorusfixStatus externalChorusfixLoaded =
        EmbeddedChorusfixController.evaluate(
            enabled, true, true, Carriers.CHORUS_MATERIAL, List.of("Nexo"));
    assertFalse(warnings.shouldWarn(externalChorusfixLoaded, List.of()));
  }

  @Test
  void finalProviderStateWithoutExternalChorusfixWarnsOnce() {
    var warnings = new EmbeddedChorusfixController.BlockedProviderWarningState();
    EmbeddedChorusfixStatus status =
        EmbeddedChorusfixController.evaluate(
            config(true), false, true, Carriers.CHORUS_MATERIAL, List.of("Nexo"));

    assertEquals(EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER, status);
    assertTrue(warnings.shouldWarn(status, List.of("Nexo")));
    assertFalse(warnings.shouldWarn(status, List.of("Nexo")));
  }

  private static EmbeddedChorusfixConfig config(boolean enabled) {
    return new EmbeddedChorusfixConfig(
        enabled, true, 256, 512, 4096, Set.of(ChorusFaceMask.ALL), false);
  }
}
