package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.i18n.Lang;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

public class SearchDialogService {
  private static final String NAMESPACE = "exort";
  private static final String ACTION_APPLY = "search_apply";
  private static final String ACTION_CANCEL = "search_cancel";
  private static final String INPUT_QUERY = "query";

  private final Lang lang;
  private Dialog cached;

  public SearchDialogService(Lang lang) {
    this.lang = lang;
  }

  public void invalidate() {
    cached = null;
  }

  public Dialog buildDialog() {
    if (cached != null) {
      return cached;
    }
    Component title = Component.text(lang.tr("gui.search.dialog.title"));
    Component bodyText = Component.text(lang.tr("gui.search.dialog.body"));
    Component inputLabel = Component.empty();
    Component applyLabel = Component.text(lang.tr("gui.search.dialog.apply"));
    Component cancelLabel = Component.text(lang.tr("gui.search.dialog.cancel"));
    ActionButton apply =
        ActionButton.create(
            applyLabel,
            null,
            150,
            DialogAction.customClick(Key.key(NAMESPACE, ACTION_APPLY), null));
    ActionButton cancel =
        ActionButton.create(
            cancelLabel,
            null,
            150,
            DialogAction.customClick(Key.key(NAMESPACE, ACTION_CANCEL), null));
    DialogBase base =
        DialogBase.builder(title)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(List.of(DialogBody.plainMessage(bodyText)))
            .inputs(List.of(DialogInput.text(INPUT_QUERY, 200, inputLabel, false, "", 128, null)))
            .build();
    DialogType type = DialogType.multiAction(List.of(apply), cancel, 2);
    cached = Dialog.create(builder -> builder.empty().base(base).type(type));
    return cached;
  }

  public boolean isApply(Key key) {
    return Key.key(NAMESPACE, ACTION_APPLY).equals(key);
  }

  public boolean isCancel(Key key) {
    return Key.key(NAMESPACE, ACTION_CANCEL).equals(key);
  }

  public String extractQuery(io.papermc.paper.dialog.DialogResponseView view) {
    if (view == null) return "";
    return view.getText(INPUT_QUERY);
  }
}
