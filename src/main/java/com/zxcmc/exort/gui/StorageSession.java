package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.i18n.ItemNameService;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageSession extends AbstractStorageSession {
    private final boolean useFillers;
    private final Map<Integer, DisplayEntry> slotEntries = new HashMap<>();
    private long infoErrorUntilMs;
    private int infoErrorTaskId = -1;
    private static final long INFO_ERROR_TICKS = 20L * 5;
    private final long confirmTimeoutMs;
    private int infoConfirmRemaining;
    private long infoConfirmLastAt;
    private long infoBlockedUntilMs;
    private boolean showStorageId;

    public StorageSession(Player viewer,
                          StorageCache cache,
                          StorageTier tier,
                          Lang lang,
                          ItemNameService itemNames,
                          Block terminalBlock,
                          org.bukkit.Location storageLocation,
                          SessionManager manager,
                          boolean readOnly,
                          Component title,
                          boolean useFillers,
                          long confirmTimeoutMs,
                          SortMode sortMode,
                          boolean wireless) {
        super(viewer, cache, tier, lang, itemNames, manager, terminalBlock, storageLocation, readOnly, title, GuiLayout.INVENTORY_SIZE, sortMode, wireless);
        this.useFillers = useFillers;
        this.confirmTimeoutMs = Math.max(0L, confirmTimeoutMs);
    }

    public void onClose() {
        sortFrozen = false;
        sortOrder.clear();
        bossBar.removeAll();
        cancelWirelessRefreshTask();
        if (infoErrorTaskId != -1) {
            Bukkit.getScheduler().cancelTask(infoErrorTaskId);
            infoErrorTaskId = -1;
        }
        resetInfoConfirm();
    }

    @Override
    public SessionType type() {
        return SessionType.STORAGE;
    }

    public void render() {
        displayList = buildDisplayList();
        int totalSlots = displayList.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalSlots / (double) GuiLayout.PAGE_SIZE));
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        slotEntries.clear();
        ItemStack[] contents = new ItemStack[GuiLayout.INVENTORY_SIZE];
        boolean fillSearchPad = useFillers && isSearchResultsPage();
        int startIndex = page * GuiLayout.PAGE_SIZE;
        for (int i = 0; i < GuiLayout.PAGE_SIZE; i++) {
            int idx = startIndex + i;
            DisplayEntry entry = entryAt(idx);
            if (entry == null) {
                contents[i] = fillSearchPad ? GuiItems.filler(true) : null;
                continue;
            }
            ItemStack stack = entry.sample().clone();
            stack.setAmount(entry.amount());
            contents[i] = stack;
            slotEntries.put(i, entry);
        }

        // Buttons and fillers
        String pageInfo = lang.tr("gui.page_info", page + 1, totalPages);
        List<Component> pageLore = new ArrayList<>();
        pageLore.add(Component.text(pageInfo).decoration(TextDecoration.ITALIC, false));
        String searchLabel = currentPageSearchLabel();
        if (searchLabel != null) {
            pageLore.add(Component.text(searchLabel).decoration(TextDecoration.ITALIC, false));
        } else {
            String categoryLabel = currentPageCategoryLabel();
            if (categoryLabel != null) {
                pageLore.add(Component.text(categoryLabel).decoration(TextDecoration.ITALIC, false));
            }
        }
        contents[GuiLayout.Storage.SLOT_PREV] = GuiItems.pagePrev(lang.tr("gui.prev_page"), pageLore, useFillers);
        contents[GuiLayout.Storage.SLOT_NEXT] = GuiItems.pageNext(lang.tr("gui.next_page"), pageLore, useFillers);
        if (useFillers) {
            ItemStack filler = GuiItems.filler(true);
            for (int i = 46; i <= 52; i++) {
                contents[i] = filler;
            }
        }
        contents[GuiLayout.Storage.SLOT_SORT] = sortButton();
        contents[GuiLayout.Storage.SLOT_INFO] = infoButton();
        contents[GuiLayout.Storage.SLOT_SEARCH] = searchButton();

        if (!useFillers) {
            ItemStack filler = GuiItems.filler(false);
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null) {
                    contents[i] = filler;
                }
            }
        }

        inventory.setContents(contents);
        updateBossBar();
        handleWirelessRefreshAfterRender();
    }

    private ItemStack sortButton() {
        String title = switch (sortMode) {
            case AMOUNT -> lang.tr("gui.sort.amount");
            case NAME -> lang.tr("gui.sort.name");
            case ID -> lang.tr("gui.sort.id");
            case CATEGORY -> lang.tr("gui.sort.category");
        };
        Component name = Component.text(title).decoration(TextDecoration.ITALIC, false);
        List<Component> lore = List.of(Component.text(lang.tr("gui.sort.hint")).decoration(TextDecoration.ITALIC, false));
        return GuiItems.sortButton(name, lore, useFillers);
    }

    private ItemStack searchButton() {
        String title = hasSearch()
                ? lang.tr("gui.search.button") + ": " + searchQuery
                : lang.tr("gui.search.button");
        Component name = Component.text(title).decoration(TextDecoration.ITALIC, false);
        List<Component> lore = List.of(
                Component.text(lang.tr("gui.search.hint")).decoration(TextDecoration.ITALIC, false),
                Component.text(lang.tr("gui.search.hint_clear")).decoration(TextDecoration.ITALIC, false)
        );
        return GuiItems.searchButton(name, lore, useFillers);
    }

    private ItemStack infoButton() {
        long current = cache.effectiveTotal();
        long max = Math.max(1, tier.maxItems());
        double filled = Math.min(1.0, Math.max(0.0, (double) current / (double) max));
        double free = 1.0 - filled;
        Component title = Component.text(lang.tr("gui.info.used") + " " + formatNumber(current) + "/" + formatNumber(max) + " ")
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("(" + FORMAT_PERCENT.format(filled * 100.0) + "%)", freeColor(free)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(tier.displayName()).decoration(TextDecoration.ITALIC, false));
        if (showStorageId) {
            lore.add(Component.text(lang.tr("gui.info.storage_id", cache.getStorageId()))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (isInfoErrorActive() && infoErrorMessage != null && !infoErrorMessage.isBlank()) {
            lore.add(Component.text(infoErrorMessage)
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (readOnly) {
            lore.add(Component.text(lang.tr("gui.info.force_hint")).decoration(TextDecoration.ITALIC, false));
            int remaining = infoConfirmRemaining();
            if (remaining > 0) {
                lore.add(Component.text(lang.tr("gui.info.force_warning"))
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(lang.tr("gui.info.force_confirm", remaining))
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (isInfoBlocked()) {
                lore.add(Component.text(lang.tr("gui.info.force_blocked"))
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        if (isInfoErrorActive()) {
            return GuiItems.infoErrorButton(title.decoration(TextDecoration.ITALIC, false), lore);
        }
        return GuiItems.infoButton(title.decoration(TextDecoration.ITALIC, false), lore, useFillers);
    }

    private boolean isInfoErrorActive() {
        return System.currentTimeMillis() < infoErrorUntilMs;
    }

    @Override
    protected void triggerInfoError() {
        infoErrorUntilMs = System.currentTimeMillis() + INFO_ERROR_TICKS * 50L;
        if (infoErrorTaskId != -1) {
            Bukkit.getScheduler().cancelTask(infoErrorTaskId);
        }
        infoErrorTaskId = Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            infoErrorTaskId = -1;
            infoErrorMessage = null;
            if (!viewer.isOnline()) return;
            render();
        }, INFO_ERROR_TICKS + 1).getTaskId();
        render();
    }

    private void handleInfoClick(InventoryClickEvent event) {
        if (!event.isShiftClick() || !event.isRightClick()) {
            resetInfoConfirm();
            return;
        }
        if (!showStorageId) {
            showStorageId = true;
            render();
            return;
        }
        if (!readOnly) {
            resetInfoConfirm();
            return;
        }
        if (!isInfoConfirming()) {
            startInfoConfirm();
            render();
            return;
        }
        int remaining = decrementInfoConfirm();
        if (remaining > 0) {
            render();
            return;
        }
        if (manager.isModeratorLocked(cache.getStorageId(), viewer.getUniqueId())) {
            markInfoBlocked();
            render();
            return;
        }
        if (!manager.forceWriterFromInfo(this)) {
            markInfoBlocked();
            render();
        }
    }

    private boolean isInfoConfirming() {
        if (infoConfirmRemaining <= 0) return false;
        if (confirmTimeoutMs > 0 && System.currentTimeMillis() - infoConfirmLastAt > confirmTimeoutMs) {
            resetInfoConfirm();
            return false;
        }
        return true;
    }

    private int infoConfirmRemaining() {
        return isInfoConfirming() ? infoConfirmRemaining : 0;
    }

    private void startInfoConfirm() {
        infoConfirmRemaining = 4;
        infoConfirmLastAt = System.currentTimeMillis();
        infoBlockedUntilMs = 0L;
    }

    private int decrementInfoConfirm() {
        if (!isInfoConfirming()) return 0;
        infoConfirmRemaining = Math.max(0, infoConfirmRemaining - 1);
        infoConfirmLastAt = System.currentTimeMillis();
        return infoConfirmRemaining;
    }

    private void resetInfoConfirm() {
        infoConfirmRemaining = 0;
        infoConfirmLastAt = 0L;
    }

    private void markInfoBlocked() {
        resetInfoConfirm();
        if (confirmTimeoutMs > 0) {
            infoBlockedUntilMs = System.currentTimeMillis() + confirmTimeoutMs;
        }
    }

    private boolean isInfoBlocked() {
        return infoBlockedUntilMs > System.currentTimeMillis();
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (event.getInventory().getHolder() != this) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot >= inventory.getSize()) {
            if (readOnly && event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            // Bottom inventory zone
            if (event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR) {
                    setInfoErrorMessage(null);
                    long deposited = depositFromStack(clicked);
                    if (deposited > 0) {
                        if (deposited < clicked.getAmount()) {
                            setInfoErrorMessage(null);
                            triggerInfoError();
                        }
                        int remaining = (int) (clicked.getAmount() - deposited);
                        if (remaining <= 0) {
                            event.setCurrentItem(null);
                        } else {
                            clicked.setAmount(remaining);
                            event.setCurrentItem(clicked);
                        }
                        manager.renderStorage(cache.getStorageId(), SortEvent.DEPOSIT);
                    } else {
                        if (infoErrorMessage == null || infoErrorMessage.isBlank()) {
                            setInfoErrorMessage(null);
                            triggerInfoError();
                        }
                    }
                    event.setCancelled(true);
                }
            }
            return;
        }

        event.setCancelled(true);
        if (rawSlot >= GuiLayout.PAGE_SIZE) {
            if (rawSlot == GuiLayout.Storage.SLOT_SEARCH && event.isShiftClick()) {
                clearSearch();
                render();
                return;
            }
            if (rawSlot == GuiLayout.Storage.SLOT_INFO) {
                handleInfoClick(event);
                return;
            }
            handleButtonClick(rawSlot);
            return;
        }

        if (readOnly) {
            return;
        }

        ItemStack cursor = event.getView().getCursor();
        DisplayEntry entry = slotEntries.get(rawSlot);
        if (cursor != null && cursor.getType() != Material.AIR) {
            setInfoErrorMessage(null);
            int moveAmount = event.isRightClick() ? 1 : cursor.getAmount();
            moveAmount = Math.min(moveAmount, cursor.getAmount());
            long deposited = depositFromCursor(cursor, moveAmount, event);
            if (deposited > 0) {
                manager.renderStorage(cache.getStorageId(), SortEvent.DEPOSIT);
            }
            return;
        }

        if (entry == null) {
            return;
        }

        if (event.isShiftClick()) {
            int moved = moveToInventory(event.getWhoClicked(), entry.sample(), entry.itemKey(), entry.amount());
            if (moved > 0) {
                cache.removeItem(entry.itemKey(), moved);
                manager.renderStorage(cache.getStorageId(), SortEvent.WITHDRAW);
            }
            return;
        }

        int desired = entry.amount();
        if (event.isRightClick()) {
            desired = (desired + 1) / 2;
        }
        int given = moveToCursor(event.getWhoClicked(), entry, desired, event);
        if (given > 0) {
            cache.removeItem(entry.itemKey(), given);
            manager.renderStorage(cache.getStorageId(), SortEvent.WITHDRAW);
        }
    }

    private void handleButtonClick(int rawSlot) {
        if (rawSlot == GuiLayout.Storage.SLOT_PREV && page > 0) {
            page--;
            render();
        } else if (rawSlot == GuiLayout.Storage.SLOT_NEXT) {
            int totalPages = Math.max(1, (int) Math.ceil(displayList.size() / (double) pageSize()));
            if (page + 1 < totalPages) {
                page++;
                render();
            }
        } else if (rawSlot == GuiLayout.Storage.SLOT_INFO) {
            // info button: no action
        } else if (rawSlot == GuiLayout.Storage.SLOT_SEARCH) {
            manager.openSearch(viewer, this);
        } else if (rawSlot == GuiLayout.Storage.SLOT_SORT) {
            if (readOnly) return;
            sortMode = switch (sortMode) {
                case AMOUNT -> SortMode.NAME;
                case NAME -> SortMode.ID;
                case ID -> SortMode.CATEGORY;
                case CATEGORY -> SortMode.AMOUNT;
            };
            sortFrozen = false;
            sortOrder.clear();
            manager.updateSortMode(cache, sortMode);
        }
    }

    @Override
    public void onSortEvent(SortEvent event) {
        if (event == SortEvent.WITHDRAW) {
            sortFrozen = true;
        } else if (event == SortEvent.DEPOSIT) {
            sortFrozen = false;
            sortOrder.clear();
        }
    }

}
