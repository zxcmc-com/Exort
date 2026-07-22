package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxcmc.exort.items.CustomItemModelConfig;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.storage.StorageTierTestFixtures;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CommandRuntimeAccessTest {
  @Test
  void resolvesLatestRuntimeServicesWithoutCachingModeSpecificItems() {
    CustomItems resourceItems = customItems();
    CustomItems vanillaItems = customItems();
    WirelessTerminalService resourceWireless = wirelessService(resourceItems);
    WirelessTerminalService vanillaWireless = wirelessService(vanillaItems);
    AtomicReference<CustomItems> items = new AtomicReference<>(resourceItems);
    AtomicReference<WirelessTerminalService> wireless = new AtomicReference<>(resourceWireless);
    CommandRuntimeAccess access =
        new CommandRuntimeAccess(items::get, wireless::get, () -> null, () -> null);

    assertSame(resourceItems, access.customItems());
    assertSame(resourceWireless, access.wirelessService());

    items.set(vanillaItems);
    wireless.set(vanillaWireless);

    assertSame(vanillaItems, access.customItems());
    assertSame(vanillaWireless, access.wirelessService());
  }

  @Test
  void rejectsMissingRuntimeServiceAtUseSite() {
    CommandRuntimeAccess access =
        new CommandRuntimeAccess(() -> null, () -> null, () -> null, () -> null);

    assertThrows(NullPointerException.class, access::customItems);
    assertThrows(NullPointerException.class, access::wirelessService);
    assertThrows(NullPointerException.class, access::chunkLoaderService);
  }

  private static CustomItems customItems() {
    return new CustomItems(
        null,
        null,
        CustomItemModelConfig.empty(),
        WirelessRuntimeConfig.defaults(),
        false,
        StorageTierTestFixtures.current());
  }

  private static WirelessTerminalService wirelessService(CustomItems customItems) {
    return new WirelessTerminalService(
        null, null, customItems, true, 0, StorageTierTestFixtures.current());
  }
}
