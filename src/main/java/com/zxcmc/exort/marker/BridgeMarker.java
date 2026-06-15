package com.zxcmc.exort.marker;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class BridgeMarker {
  private BridgeMarker() {}

  private static final String SECTION = "bridge";
  private static final String FIELD_PRESENT = "present";
  private static final String FIELD_LINK_WORLD = "link_world";
  private static final String FIELD_LINK_X = "link_x";
  private static final String FIELD_LINK_Y = "link_y";
  private static final String FIELD_LINK_Z = "link_z";

  public record Link(UUID worldId, int x, int y, int z) {
    public Link {
      Objects.requireNonNull(worldId, "worldId");
    }

    public static Link of(Block block) {
      Objects.requireNonNull(block, "block");
      return new Link(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public boolean sameBlock(Block block) {
      return block != null
          && block.getWorld() != null
          && worldId.equals(block.getWorld().getUID())
          && x == block.getX()
          && y == block.getY()
          && z == block.getZ();
    }

    public Block loadedBlock() {
      World world = Bukkit.getWorld(worldId);
      if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
        return null;
      }
      return world.getBlockAt(x, y, z);
    }
  }

  public static void set(Plugin plugin, Block block) {
    ChunkMarkerStore.setByte(plugin, block, SECTION, FIELD_PRESENT, (byte) 1);
  }

  public static boolean isBridge(Plugin plugin, Block block) {
    return ChunkMarkerStore.getByte(plugin, block, SECTION, FIELD_PRESENT)
        .map(val -> val == (byte) 1)
        .orElse(false);
  }

  public static Optional<Link> link(Plugin plugin, Block block) {
    Optional<String> worldRaw =
        ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_LINK_WORLD);
    if (worldRaw.isEmpty()) {
      return Optional.empty();
    }
    UUID worldId;
    try {
      worldId = UUID.fromString(worldRaw.get());
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
    Optional<String> xRaw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_LINK_X);
    Optional<String> yRaw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_LINK_Y);
    Optional<String> zRaw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_LINK_Z);
    if (xRaw.isEmpty() || yRaw.isEmpty() || zRaw.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new Link(
              worldId,
              Integer.parseInt(xRaw.get()),
              Integer.parseInt(yRaw.get()),
              Integer.parseInt(zRaw.get())));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  public static void link(Plugin plugin, Block first, Block second) {
    set(plugin, first);
    set(plugin, second);
    setLink(plugin, first, Link.of(second));
    setLink(plugin, second, Link.of(first));
  }

  public static void setLink(Plugin plugin, Block block, Link link) {
    if (link == null) {
      clearLink(plugin, block);
      return;
    }
    set(plugin, block);
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_LINK_WORLD, link.worldId().toString());
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_LINK_X, Integer.toString(link.x()));
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_LINK_Y, Integer.toString(link.y()));
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_LINK_Z, Integer.toString(link.z()));
  }

  public static void clearLink(Plugin plugin, Block block) {
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_LINK_WORLD);
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_LINK_X);
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_LINK_Y);
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_LINK_Z);
  }

  public static void unlinkLoadedPair(Plugin plugin, Block block) {
    Optional<Link> peerLink = link(plugin, block);
    clearLink(plugin, block);
    Block peer = peerLink.map(Link::loadedBlock).orElse(null);
    if (peer == null || !isBridge(plugin, peer)) {
      return;
    }
    link(plugin, peer)
        .filter(local -> local.sameBlock(block))
        .ifPresent(local -> clearLink(plugin, peer));
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
