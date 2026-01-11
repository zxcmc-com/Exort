package com.zxcmc.exort.display;

import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * RESOURCE/VANILLA display manager for terminal blocks (carrier + ItemDisplay model).
 */
public class TerminalDisplayManager extends BaseCarrierDisplayManager {
    private final String terminalName;
    private final String craftingTerminalName;
    private final com.zxcmc.exort.core.keys.StorageKeys keys;
    private final int wireLimit;
    private final int wireHardCap;
    private final Material wireMaterial;
    private final Material storageCarrier;
    private final boolean dynamicState;
    private final String terminalEnabledModel;
    private final String terminalDisabledModel;
    private final String craftingEnabledModel;
    private final String craftingDisabledModel;

    public TerminalDisplayManager(Plugin plugin,
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
                                  String terminalName,
                                  String craftingTerminalName,
                                  com.zxcmc.exort.core.keys.StorageKeys keys,
                                  int wireLimit,
                                  int wireHardCap,
                                  Material wireMaterial,
                                  Material storageCarrier,
                                  boolean dynamicState) {
        super(plugin, carrierMaterial, terminalEnabledModel, displayBaseMaterial, displayScale, offsetX, offsetY, offsetZ, "terminal");
        this.terminalName = terminalName == null ? "" : terminalName;
        this.craftingTerminalName = craftingTerminalName == null ? "" : craftingTerminalName;
        this.keys = keys;
        this.wireLimit = wireLimit;
        this.wireHardCap = wireHardCap;
        this.wireMaterial = wireMaterial;
        this.storageCarrier = storageCarrier;
        this.dynamicState = dynamicState;
        this.terminalEnabledModel = terminalEnabledModel == null ? "" : terminalEnabledModel;
        this.terminalDisabledModel = terminalDisabledModel == null ? this.terminalEnabledModel : terminalDisabledModel;
        this.craftingEnabledModel = craftingEnabledModel == null ? "" : craftingEnabledModel;
        this.craftingDisabledModel = craftingDisabledModel == null ? this.craftingEnabledModel : craftingDisabledModel;
    }

    @Override
    protected boolean isValidBlock(Block block) {
        return Carriers.matchesCarrier(block, carrierMaterial) && TerminalMarker.isTerminal(plugin, block);
    }

    @Override
    protected void decorateMeta(ItemMeta meta, Block block) {
        String name = nameFor(block);
        if (!name.isEmpty()) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
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
    protected void applyTransform(ItemDisplay display, Block block) {
        Transformation t = display.getTransformation();
        t.getScale().set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
        t.getLeftRotation().set(DisplayRotation.rotationForFacing(faceFor(block)));
        t.getRightRotation().identity();
        String name = nameFor(block);
        if (!name.isEmpty()) {
            display.customName(Component.text(name));
            display.setCustomNameVisible(false);
        } else {
            display.customName(null);
        }
        display.setTransformation(t);
    }

    private org.bukkit.block.BlockFace faceFor(Block block) {
        var faceOpt = TerminalMarker.facing(plugin, block);
        return faceOpt.orElse(org.bukkit.block.BlockFace.SOUTH);
    }

    private String nameFor(Block block) {
        TerminalKind kind = TerminalMarker.kind(plugin, block);
        return kind == TerminalKind.CRAFTING ? craftingTerminalName : terminalName;
    }

    private boolean isActive(Block block) {
        if (keys == null) return false;
        var result = com.zxcmc.exort.core.network.TerminalLinkFinder.find(block, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
        return result.count() == 1 && result.data() != null;
    }
}
