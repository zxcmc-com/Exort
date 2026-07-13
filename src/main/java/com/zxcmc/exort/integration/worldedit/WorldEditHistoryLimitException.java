package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

/** Stops an edit before Exort marker history becomes incomplete. */
final class WorldEditHistoryLimitException extends WorldEditException {
  WorldEditHistoryLimitException() {
    super(
        TextComponent.of(
            "Exort stopped this edit because its custom-block undo history limit was reached. "
                + "Reduce the selection size and retry."));
  }
}
