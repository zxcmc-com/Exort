package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.carrier.Carriers;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;

/** Pure carrier compatibility rules shared by capture and extent interception. */
final class WorldEditCarrierPolicy {
  private final CarrierMaterials materials;

  WorldEditCarrierPolicy(CarrierMaterials materials) {
    this.materials = Objects.requireNonNull(materials, "materials");
  }

  boolean matchesWorldCarrier(Block block, MarkerSnapshot snapshot) {
    if (block == null || snapshot == null) return false;
    if (snapshot.storage() != null && !Carriers.matchesCarrier(block, materials.storageCarrier())) {
      return false;
    }
    if (snapshot.storageCore() && !Carriers.matchesCarrier(block, materials.storageCarrier())) {
      return false;
    }
    if (snapshot.terminal() != null
        && !Carriers.matchesCarrier(block, materials.terminalCarrier())) {
      return false;
    }
    if (snapshot.monitor() != null && !Carriers.matchesCarrier(block, materials.monitorCarrier())) {
      return false;
    }
    if (snapshot.bus() != null && !Carriers.matchesCarrier(block, materials.busCarrier())) {
      return false;
    }
    if (snapshot.relay() != null && !Carriers.matchesCarrier(block, materials.relayCarrier())) {
      return false;
    }
    if (snapshot.transmitter() && !Carriers.matchesCarrier(block, materials.transmitterCarrier())) {
      return false;
    }
    if (snapshot.chunkLoader() != null
        && !Carriers.matchesCarrier(block, materials.chunkLoaderCarrier())) {
      return false;
    }
    return !snapshot.wire() || Carriers.matchesCarrier(block, materials.wire());
  }

  boolean isCarrierCandidate(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    return type == materials.storageCarrier()
        || type == materials.terminalCarrier()
        || type == materials.monitorCarrier()
        || type == materials.busCarrier()
        || type == materials.relayCarrier()
        || type == materials.transmitterCarrier()
        || type == materials.chunkLoaderCarrier()
        || type == materials.wire()
        || type == Carriers.CARRIER_BARRIER
        || type == Carriers.CHORUS_MATERIAL;
  }

  boolean isCarrierCandidate(BaseBlock block) {
    if (block == null || block.getBlockType() == null) return false;
    BlockType type = block.getBlockType();
    return matchesMaterial(type, materials.storageCarrier())
        || matchesMaterial(type, materials.terminalCarrier())
        || matchesMaterial(type, materials.monitorCarrier())
        || matchesMaterial(type, materials.busCarrier())
        || matchesMaterial(type, materials.relayCarrier())
        || matchesMaterial(type, materials.transmitterCarrier())
        || matchesMaterial(type, materials.chunkLoaderCarrier())
        || matchesMaterial(type, materials.wire())
        || matchesMaterial(type, Carriers.CARRIER_BARRIER)
        || matchesMaterial(type, Carriers.CHORUS_MATERIAL);
  }

  private static boolean matchesMaterial(BlockType type, Material material) {
    if (type == null || material == null) return false;
    BlockType materialType = BlockTypes.get(material.getKey().toString());
    return type.equals(materialType);
  }
}
