package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.feedback.FeedbackReason;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiItems;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TransmitterSession implements InventoryHolder {
  private static final int SIZE = 9;
  private static final int MODE_SLOT = 0;
  private static final int INFO_SLOT = 1;
  private static final int TERMINAL_SLOT = 3;
  private static final int PREVIEW_SLOT = 5;
  private static final int UPGRADE_SLOT = 8;

  private enum BindingOutcome {
    NOT_REQUESTED(null, false),
    BOUND(null, true),
    WIRELESS_DISABLED("message.wireless.disabled", false),
    TRANSMITTER_INACTIVE("message.wireless.transmitter_inactive", false),
    NO_STORAGE("gui.transmitter.status.no_storage", false),
    MULTIPLE_STORAGES("gui.transmitter.status.multiple", false),
    NO_PERMISSION("message.no_permission", false);

    private final String feedbackKey;
    private final boolean bound;

    BindingOutcome(String feedbackKey, boolean bound) {
      this.feedbackKey = feedbackKey;
      this.bound = bound;
    }
  }

  private record PreparedTerminal(ItemStack stack, BindingOutcome bindingOutcome) {}

  private final TransmitterSessionManager manager;
  private final Player viewer;
  private final Block transmitter;
  private final WirelessTransmitterService transmitterService;
  private final WirelessTerminalService wirelessService;
  private final Lang lang;
  private final PlayerFeedback playerFeedback;
  private final RegionProtection regionProtection;
  private final boolean useFillers;
  private final Inventory inventory;
  private boolean closed;

  public TransmitterSession(
      TransmitterSessionManager manager,
      Player viewer,
      Block transmitter,
      WirelessTransmitterService transmitterService,
      WirelessTerminalService wirelessService,
      Lang lang,
      PlayerFeedback playerFeedback,
      RegionProtection regionProtection,
      boolean useFillers,
      Component title) {
    this.manager = manager;
    this.viewer = viewer;
    this.transmitter = transmitter;
    this.transmitterService = transmitterService;
    this.wirelessService = wirelessService;
    this.lang = lang;
    this.playerFeedback = playerFeedback;
    this.regionProtection = regionProtection;
    this.useFillers = useFillers;
    this.inventory = Bukkit.createInventory(this, SIZE, title);
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  Player viewer() {
    return viewer;
  }

  Block transmitter() {
    return transmitter;
  }

  public void onClose() {
    closed = true;
  }

  public void render() {
    if (closed) {
      return;
    }
    ItemStack[] contents = new ItemStack[SIZE];
    TransmitterMode mode = mode();
    WirelessTransmitterService.Status status = transmitterService.status(transmitter);
    manager.reconcileCharging(transmitter, status);
    contents[MODE_SLOT] = modeItem(mode);
    contents[INFO_SLOT] = infoItem(mode, status);
    contents[TERMINAL_SLOT] = storedTerminalDisplay();
    if (useFillers) {
      contents[4] = arrowFiller();
    }
    contents[PREVIEW_SLOT] = previewItem();
    ItemStack filler = GuiItems.filler(useFillers);
    for (int i = 0; i < SIZE; i++) {
      if (contents[i] != null) {
        continue;
      }
      if (i == TERMINAL_SLOT || i == 4 || i == UPGRADE_SLOT) {
        continue;
      }
      contents[i] = filler;
    }
    inventory.setContents(contents);
  }

  public void handleClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player) || !player.equals(viewer)) {
      event.setCancelled(true);
      return;
    }
    ClickType click = event.getClick();
    if (click == ClickType.NUMBER_KEY
        || click == ClickType.SWAP_OFFHAND
        || click == ClickType.DOUBLE_CLICK
        || click == ClickType.DROP
        || click == ClickType.CONTROL_DROP) {
      event.setCancelled(true);
      return;
    }
    int rawSlot = event.getRawSlot();
    if (rawSlot < SIZE) {
      event.setCancelled(true);
      handleTopClick(event, rawSlot);
      return;
    }
    if (event.isShiftClick()) {
      event.setCancelled(true);
      if (wirelessService.isWireless(event.getCurrentItem())) {
        shiftStoreTerminal(event);
      }
    }
  }

  public void handleDrag(InventoryDragEvent event) {
    for (int rawSlot : event.getRawSlots()) {
      if (rawSlot < SIZE) {
        event.setCancelled(true);
        return;
      }
    }
  }

  private void handleTopClick(InventoryClickEvent event, int rawSlot) {
    if (rawSlot == MODE_SLOT) {
      toggleMode();
      return;
    }
    if (rawSlot != TERMINAL_SLOT) {
      return;
    }
    ItemStack cursor = event.getView().getCursor();
    if (isEmpty(cursor)) {
      takeStoredToCursor(event);
      return;
    }
    storeFromCursor(event, cursor);
  }

  private void shiftStoreTerminal(InventoryClickEvent event) {
    if (storedTerminal().isPresent()) {
      return;
    }
    ItemStack source = event.getCurrentItem();
    if (!wirelessService.isWireless(source)) {
      return;
    }
    ItemStack toStore = source.clone();
    toStore.setAmount(1);
    PreparedTerminal prepared = prepareForMode(toStore, mode());
    if (prepared == null) {
      operationFailed();
      return;
    }
    TransmitterStoredTerminal.WriteResult write = saveStoredTerminal(prepared.stack());
    if (!write.success()) {
      storedTerminalWriteFailed(write, "shift insert");
      return;
    }
    source.setAmount(source.getAmount() - 1);
    if (source.getAmount() <= 0) {
      event.setCurrentItem(null);
    } else {
      event.setCurrentItem(source);
    }
    reportBindingOutcome(prepared.bindingOutcome());
    render();
    refreshTransmitterDisplay();
  }

  private void storeFromCursor(InventoryClickEvent event, ItemStack cursor) {
    if (storedTerminal().isPresent()) {
      transmitterInputDenied("message.wireless.transmitter_slot_occupied");
      render();
      return;
    }
    if (!wirelessService.isWireless(cursor)) {
      transmitterInputDenied("message.wireless.transmitter_terminal_only");
      render();
      return;
    }
    ItemStack toStore = cursor.clone();
    toStore.setAmount(1);
    PreparedTerminal prepared = prepareForMode(toStore, mode());
    if (prepared == null) {
      operationFailed();
      render();
      return;
    }
    TransmitterStoredTerminal.WriteResult write = saveStoredTerminal(prepared.stack());
    if (!write.success()) {
      storedTerminalWriteFailed(write, "cursor insert");
      render();
      return;
    }
    cursor.setAmount(cursor.getAmount() - 1);
    event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);
    reportBindingOutcome(prepared.bindingOutcome());
    render();
    refreshTransmitterDisplay();
  }

  private void takeStoredToCursor(InventoryClickEvent event) {
    TransmitterStoredTerminal.TakeReservation reservation =
        TransmitterStoredTerminal.reserveTake(
                plugin(),
                transmitter,
                wirelessService::isWireless,
                message -> ExortLog.warn(message))
            .orElse(null);
    if (reservation != null && reservation.commit()) {
      try {
        ItemStack out = wirelessService.extractFromStorage(reservation.item());
        event.getView().setCursor(out);
      } catch (RuntimeException failure) {
        boolean restored = false;
        try {
          restored = reservation.rollback();
        } catch (RuntimeException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
        if (!restored) {
          try {
            viewer.getWorld().dropItemNaturally(viewer.getLocation(), reservation.item());
            ExortLog.warn(
                "Wireless Terminal delivery failed; the item was dropped at the player: "
                    + failure.getMessage());
          } catch (RuntimeException dropFailure) {
            ExortLog.error(
                "Failed to restore or drop Wireless Terminal after transmitter delivery error: "
                    + failure.getMessage()
                    + "; fallback drop failed: "
                    + dropFailure.getMessage());
          }
        }
        operationFailed();
      }
    }
    render();
    refreshTransmitterDisplay();
  }

  private void toggleMode() {
    TransmitterMode previousMode = mode();
    TransmitterMode next = previousMode.next();
    java.util.Optional<ItemStack> stored = storedTerminal();
    BindingOutcome bindingOutcome = BindingOutcome.NOT_REQUESTED;
    byte[] previousBlob =
        TransmitterStoredTerminal.terminalBlob(plugin(), transmitter).orElse(null);
    if (stored.isPresent()) {
      ItemStack extracted = wirelessService.extractFromStorage(stored.get());
      PreparedTerminal prepared = prepareForMode(extracted, next);
      if (prepared == null) {
        operationFailed();
        render();
        return;
      }
      TransmitterStoredTerminal.WriteResult write = saveStoredTerminal(prepared.stack());
      if (!write.success()) {
        storedTerminalWriteFailed(write, "mode change");
        render();
        return;
      }
      bindingOutcome = prepared.bindingOutcome();
    }
    try {
      TransmitterStoredTerminal.setMode(plugin(), transmitter, next);
    } catch (RuntimeException failure) {
      try {
        TransmitterStoredTerminal.restore(plugin(), transmitter, previousMode, previousBlob);
      } catch (RuntimeException rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      ExortLog.error("Failed to persist Wireless Transmitter mode change: " + failure.getMessage());
      operationFailed();
      render();
      return;
    }
    transmitterService.invalidateStatus(transmitter);
    reportBindingOutcome(bindingOutcome);
    render();
    refreshTransmitterDisplay();
  }

  private PreparedTerminal prepareForMode(ItemStack stack, TransmitterMode mode) {
    BindingOutcome bindingOutcome = BindingOutcome.NOT_REQUESTED;
    if (mode == TransmitterMode.BIND) {
      bindingOutcome = bindStoredTerminal(stack);
      if (bindingOutcome == null) {
        return null;
      }
    }
    if (!manager.prepareStoredTerminal(transmitter, stack)) {
      return null;
    }
    return new PreparedTerminal(stack, bindingOutcome);
  }

  private BindingOutcome bindStoredTerminal(ItemStack stack) {
    wirelessService.unbind(stack);
    if (!wirelessService.isEnabled() || !transmitterService.isEnabled()) {
      return BindingOutcome.WIRELESS_DISABLED;
    }
    WirelessTransmitterService.Status status = transmitterService.status(transmitter);
    if (!status.active()) {
      return switch (status.state()) {
        case NO_STORAGE -> BindingOutcome.NO_STORAGE;
        case MULTIPLE_STORAGES -> BindingOutcome.MULTIPLE_STORAGES;
        case DISABLED -> BindingOutcome.WIRELESS_DISABLED;
        case MODE_DISABLED, MISSING -> BindingOutcome.TRANSMITTER_INACTIVE;
        case ACTIVE -> throw new IllegalStateException("Active transmitter reported inactive");
      };
    }
    if (!regionProtection.canUse(viewer, transmitter)
        || !regionProtection.canUse(viewer, status.storage().block())) {
      return BindingOutcome.NO_PERMISSION;
    }
    Location storageLocation = status.storage().block().getLocation();
    StorageTier tier = status.storage().tier();
    if (!wirelessService.bind(viewer, stack, status.storage().storageId(), tier, storageLocation)) {
      return null;
    }
    return BindingOutcome.BOUND;
  }

  private TransmitterStoredTerminal.WriteResult saveStoredTerminal(ItemStack stack) {
    return TransmitterStoredTerminal.setDetailed(
        plugin(), transmitter, stack, wirelessService::isWireless);
  }

  private void reportBindingOutcome(BindingOutcome outcome) {
    if (outcome == null || outcome == BindingOutcome.NOT_REQUESTED) {
      return;
    }
    if (outcome.bound) {
      playerFeedback.success(viewer, "message.wireless.bound");
      return;
    }
    playerFeedback.respond(viewer, FeedbackReason.WIRELESS_ACCESS, outcome.feedbackKey);
  }

  private void storedTerminalWriteFailed(
      TransmitterStoredTerminal.WriteResult result, String operation) {
    ExortLog.warn(
        "Failed to persist Wireless Terminal during "
            + operation
            + ": "
            + result.failure().name().toLowerCase(java.util.Locale.ROOT)
            + (result.detail().isBlank() ? "" : " (" + result.detail() + ")"));
    operationFailed();
  }

  private void transmitterInputDenied(String key) {
    playerFeedback.respond(viewer, FeedbackReason.TRANSMITTER_INPUT, key);
  }

  private void operationFailed() {
    playerFeedback.respond(viewer, FeedbackReason.OPERATION_FAILURE, "message.operation_failed");
  }

  private java.util.Optional<ItemStack> storedTerminal() {
    return TransmitterStoredTerminal.get(
        plugin(), transmitter, wirelessService::isWireless, message -> ExortLog.warn(message));
  }

  private ItemStack storedTerminalDisplay() {
    return storedTerminal().map(wirelessService::displaySample).orElse(null);
  }

  private TransmitterMode mode() {
    return TransmitterStoredTerminal.mode(plugin(), transmitter);
  }

  private org.bukkit.plugin.Plugin plugin() {
    return manager.plugin();
  }

  private void refreshTransmitterDisplay() {
    manager.refreshTransmitterDisplay(transmitter);
  }

  private ItemStack modeItem(TransmitterMode mode) {
    List<Component> lore =
        List.of(
            ExortText.itemText(lang.tr(viewer, "gui.transmitter.mode.hint")),
            ExortText.itemText(lang.tr(viewer, modeLoreKey(mode))));
    return GuiItems.button(
        modeMaterial(mode),
        ExortText.itemText(lang.tr(viewer, modeNameKey(mode))),
        lore,
        useFillers);
  }

  private ItemStack infoItem(TransmitterMode mode, WirelessTransmitterService.Status status) {
    List<Component> lore = new ArrayList<>();
    lore.add(ExortText.itemText(statusText(status)));
    if (status.active()) {
      lore.add(
          ExortText.itemText(
              lang.tr(viewer, "gui.transmitter.status.storage", storageTail(status))));
    }
    lore.add(
        ExortText.itemText(
            lang.tr(viewer, "gui.transmitter.status.range", transmitterService.rangeBlocks())));
    lore.add(
        ExortText.itemText(
            storedTerminal().isPresent()
                ? lang.tr(viewer, "gui.transmitter.slot.terminal_present")
                : lang.tr(viewer, "gui.transmitter.slot.terminal_empty")));
    lore.add(ExortText.itemText(lang.tr(viewer, modeNameKey(mode))));
    return GuiItems.infoButton(
        ExortText.itemText(lang.tr(viewer, "gui.transmitter.status.item")), lore, useFillers);
  }

  private Material modeMaterial(TransmitterMode mode) {
    return switch (mode) {
      case CHARGE_ONLY -> Material.REDSTONE;
      case BIND -> Material.ENDER_EYE;
      case DISABLED -> Material.BARRIER;
    };
  }

  private String modeNameKey(TransmitterMode mode) {
    return switch (mode) {
      case CHARGE_ONLY -> "gui.transmitter.mode.charge_only";
      case BIND -> "gui.transmitter.mode.bind";
      case DISABLED -> "gui.transmitter.mode.disabled";
    };
  }

  private String modeLoreKey(TransmitterMode mode) {
    return switch (mode) {
      case CHARGE_ONLY -> "gui.transmitter.mode.charge_only_lore";
      case BIND -> "gui.transmitter.mode.bind_lore";
      case DISABLED -> "gui.transmitter.mode.disabled_lore";
    };
  }

  private ItemStack previewItem() {
    ItemStack item = wirelessService.create();
    item.setAmount(1);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setHideTooltip(true);
      item.setItemMeta(meta);
    }
    return item;
  }

  private static ItemStack arrowFiller() {
    ItemStack item = GuiItems.pagePrev("", List.of(), true);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(ExortText.itemText(""));
      meta.displayName(ExortText.itemText(""));
      meta.lore(List.of());
      meta.setHideTooltip(true);
      item.setItemMeta(meta);
    }
    return item;
  }

  private String statusText(WirelessTransmitterService.Status status) {
    return switch (status.state()) {
      case ACTIVE -> lang.tr(viewer, "gui.transmitter.status.active");
      case MULTIPLE_STORAGES -> lang.tr(viewer, "gui.transmitter.status.multiple");
      case DISABLED -> lang.tr(viewer, "message.wireless.disabled");
      case MODE_DISABLED -> lang.tr(viewer, "gui.transmitter.status.disabled");
      case MISSING -> lang.tr(viewer, "gui.transmitter.status.missing");
      case NO_STORAGE -> lang.tr(viewer, "gui.transmitter.status.no_storage");
    };
  }

  private String storageTail(WirelessTransmitterService.Status status) {
    String id = status.storage() == null ? null : status.storage().storageId();
    if (id == null || id.length() <= 8) {
      return id == null ? "?" : id;
    }
    return id.substring(id.length() - 8);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
  }
}
