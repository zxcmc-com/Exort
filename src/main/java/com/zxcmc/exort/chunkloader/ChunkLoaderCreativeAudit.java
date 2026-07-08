package com.zxcmc.exort.chunkloader;

import java.util.UUID;
import org.bukkit.entity.Player;

public interface ChunkLoaderCreativeAudit {
  ChunkLoaderCreativeAudit NOOP =
      new ChunkLoaderCreativeAudit() {
        @Override
        public void recordCreativePickIssue(Player player, ChunkLoaderType type, int amount) {}

        @Override
        public void recordCreativePickReplacementDestroy(
            Player player, UUID loaderId, ChunkLoaderType type, int amount) {}
      };

  void recordCreativePickIssue(Player player, ChunkLoaderType type, int amount);

  void recordCreativePickReplacementDestroy(
      Player player, UUID loaderId, ChunkLoaderType type, int amount);
}
