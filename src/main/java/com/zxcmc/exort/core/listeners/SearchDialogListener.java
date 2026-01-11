package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.gui.SearchDialogService;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.connection.PlayerGameConnection;
import org.bukkit.plugin.Plugin;


public class SearchDialogListener implements Listener {
    private final SessionManager sessionManager;
    private final SearchDialogService dialogService;
    private final Plugin plugin;

    public SearchDialogListener(SessionManager sessionManager, SearchDialogService dialogService, Plugin plugin) {
        this.sessionManager = sessionManager;
        this.dialogService = dialogService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        Key key = event.getIdentifier();
        if (key == null) {
            return;
        }
        boolean apply = dialogService.isApply(key);
        boolean cancel = dialogService.isCancel(key);
        if (!apply && !cancel) {
            return;
        }
        Player player;
        if (event.getCommonConnection() instanceof PlayerGameConnection pgc) {
            player = pgc.getPlayer();
        } else {
            return;
        }
        if (!sessionManager.hasPendingSearch(player)) {
            return;
        }
        if (cancel) {
            sessionManager.cancelSearch(player);
            return;
        }
        String query = dialogService.extractQuery(event.getDialogResponseView());
        String normalized = query == null ? "" : query.trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> sessionManager.handleSearchInput(player, normalized));
    }
}
