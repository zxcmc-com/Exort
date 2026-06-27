package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.display.core.DisplayRotation;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.network.TerminalLinkFinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/** RESOURCE/VANILLA display manager for terminal blocks (carrier + ItemDisplay model). */
public class TerminalDisplayManager extends BaseCarrierDisplayManager {
  private final Component terminalName;
  private final Component craftingTerminalName;
  private final StorageKeys keys;
  private final int wireLimit;
  private final int wireHardCap;
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material relayCarrier;
  private final boolean dynamicState;
  private final String terminalEnabledModel;
  private final String terminalDisabledModel;
  private final String craftingEnabledModel;
  private final String craftingDisabledModel;

  public TerminalDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String terminalEnabledModel,
      String terminalDisabledModel,
      String craftingEnabledModel,
      String craftingDisabledModel,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component terminalName,
      Component craftingTerminalName,
      StorageKeys keys,
      int wireLimit,
      int wireHardCap,
      int relayRangeChunks,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      boolean dynamicState) {
    super(
        plugin,
        carrierMaterial,
        terminalEnabledModel,
        displayBaseMaterial,
        displayScale,
        offsetX,
        offsetY,
        offsetZ,
        metadataService,
        "terminal");
    this.terminalName = terminalName;
    this.craftingTerminalName = craftingTerminalName;
    this.keys = keys;
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.relayCarrier = relayCarrier;
    this.dynamicState = dynamicState;
    this.terminalEnabledModel = terminalEnabledModel == null ? "" : terminalEnabledModel;
    this.terminalDisabledModel =
        terminalDisabledModel == null ? this.terminalEnabledModel : terminalDisabledModel;
    this.craftingEnabledModel = craftingEnabledModel == null ? "" : craftingEnabledModel;
    this.craftingDisabledModel =
        craftingDisabledModel == null ? this.craftingEnabledModel : craftingDisabledModel;
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial)
        && TerminalMarker.isTerminal(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    Component name = nameFor(block);
    if (name != null) {
      meta.displayName(name.decoration(TextDecoration.ITALIC, false));
    }
  }

  @Override
  protected String modelId(Block block) {
    TerminalKind kind = TerminalMarker.kind(plugin, block);
    String enabled = kind == TerminalKind.CRAFTING ? craftingEnabledModel : terminalEnabledModel;
    String disabled = kind == TerminalKind.CRAFTING ? craftingDisabledModel : terminalDisabledModel;
    if (!dynamicState) {
      return enabled;
    }
    boolean active = isActive(block);
    return active ? enabled : disabled;
  }

  @Override
  protected String localizationKey(Block block) {
    TerminalKind kind = TerminalMarker.kind(plugin, block);
    return kind == TerminalKind.CRAFTING ? "item.crafting_terminal" : "item.terminal";
  }

  @Override
  protected void applyTransform(ItemDisplay display, Block block) {
    Transformation t = display.getTransformation();
    t.getScale()
        .set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
    t.getLeftRotation().set(DisplayRotation.rotationForFacing(faceFor(block)));
    t.getRightRotation().identity();
    Component name = nameFor(block);
    if (name != null) {
      display.customName(name);
      display.setCustomNameVisible(false);
    } else {
      display.customName(null);
    }
    display.setTransformation(t);
  }

  private BlockFace faceFor(Block block) {
    var faceOpt = TerminalMarker.facing(plugin, block);
    return faceOpt.orElse(BlockFace.SOUTH);
  }

  private Component nameFor(Block block) {
    TerminalKind kind = TerminalMarker.kind(plugin, block);
    return kind == TerminalKind.CRAFTING ? craftingTerminalName : terminalName;
  }

  private boolean isActive(Block block) {
    if (keys == null) return false;
    var result =
        TerminalLinkFinder.find(
            block,
            keys,
            plugin,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            relayCarrier,
            relayRangeChunks);
    return result.count() == 1 && result.data() != null;
  }
}
