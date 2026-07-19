package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import java.util.Optional;
import java.util.UUID;
import org.enginehub.linbus.tree.LinByteArrayTag;
import org.enginehub.linbus.tree.LinByteTag;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;

/** Encodes and decodes Exort marker state without accessing Bukkit or WorldEdit runtime state. */
final class WorldEditMarkerCodec {
  static final String EXORT_TAG = "exort";
  static final String SECTION_STORAGE = "storage";
  static final String SECTION_TERMINAL = "terminal";
  static final String SECTION_BUS = "bus";
  static final String SECTION_MONITOR = "monitor";
  static final String SECTION_RELAY = "relay";
  static final String SECTION_TRANSMITTER = "transmitter";
  static final String SECTION_CHUNK_LOADER = "chunk_loader";
  static final String SECTION_WIRE = "wire";
  static final String SECTION_STORAGE_CORE = "storage_core";

  static final String FIELD_ID = "id";
  static final String FIELD_TIER = "tier";
  static final String FIELD_TIER_MAX_ITEMS = "tierMaxItems";
  static final String FIELD_TYPE = "type";
  static final String FIELD_MODE = "mode";
  static final String FIELD_FACING = "facing";
  static final String FIELD_NAME = "name";
  static final String FIELD_ITEM_KEY = "item_key";
  static final String FIELD_ITEM_BLOB = "item_blob";
  static final String FIELD_TERMINAL_BLOB = "terminal_blob";
  static final String FIELD_BOOSTER_BLOB = "booster_blob";
  static final String FIELD_PRESENT = "present";
  static final String FIELD_LINK_WORLD = "link_world";
  static final String FIELD_LINK_X = "link_x";
  static final String FIELD_LINK_Y = "link_y";
  static final String FIELD_LINK_Z = "link_z";
  static final String FIELD_FILTERS = "filters";
  static final String FIELD_NBT_ID = "id";
  static final String FIELD_PLACED_BY_UUID = "placed_by_uuid";
  static final String FIELD_PLACED_BY_NAME = "placed_by_name";
  static final String FIELD_CREATED_AT = "created_at";
  static final String FIELD_ENABLED = "enabled";
  static final String FIELD_BYPASS_LIMITS = "bypass_limits";

  private WorldEditMarkerCodec() {}

  static LinCompoundTag buildExortTag(MarkerSnapshot snapshot) {
    if (snapshot == null) return null;
    LinCompoundTag.Builder exort = LinCompoundTag.builder();
    boolean any = false;
    if (snapshot.storage() != null) {
      LinCompoundTag.Builder storageTag = LinCompoundTag.builder();
      storageTag.putString(FIELD_ID, snapshot.storage().storageId());
      storageTag.putString(FIELD_TIER, snapshot.storage().tier());
      if (snapshot.storage().tierMaxItems() != null) {
        storageTag.putString(
            FIELD_TIER_MAX_ITEMS, Long.toString(snapshot.storage().tierMaxItems()));
      }
      if (snapshot.storage().displayName() != null) {
        storageTag.putString(FIELD_NAME, snapshot.storage().displayName());
      }
      if (snapshot.storage().facing() != null) {
        storageTag.putString(FIELD_FACING, snapshot.storage().facing());
      }
      exort.put(SECTION_STORAGE, storageTag.build());
      any = true;
    }
    if (snapshot.storageCore()) {
      LinCompoundTag.Builder coreTag = LinCompoundTag.builder();
      coreTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_STORAGE_CORE, coreTag.build());
      any = true;
    }
    if (snapshot.terminal() != null) {
      LinCompoundTag.Builder terminalTag = LinCompoundTag.builder();
      if (snapshot.terminal().type() != null) {
        terminalTag.putString(FIELD_TYPE, snapshot.terminal().type());
      }
      if (snapshot.terminal().facing() != null) {
        terminalTag.putString(FIELD_FACING, snapshot.terminal().facing());
      }
      exort.put(SECTION_TERMINAL, terminalTag.build());
      any = true;
    }
    if (snapshot.bus() != null) {
      LinCompoundTag.Builder busTag = LinCompoundTag.builder();
      if (snapshot.bus().type() != null) {
        busTag.putString(FIELD_TYPE, snapshot.bus().type());
      }
      if (snapshot.bus().mode() != null) {
        busTag.putString(FIELD_MODE, snapshot.bus().mode());
      }
      if (snapshot.bus().facing() != null) {
        busTag.putString(FIELD_FACING, snapshot.bus().facing());
      }
      byte[] filters = snapshot.bus().filters();
      if (filters != null && filters.length > 0) {
        busTag.putByteArray(FIELD_FILTERS, filters);
      }
      exort.put(SECTION_BUS, busTag.build());
      any = true;
    }
    if (snapshot.monitor() != null) {
      LinCompoundTag.Builder monitorTag = LinCompoundTag.builder();
      if (snapshot.monitor().facing() != null) {
        monitorTag.putString(FIELD_FACING, snapshot.monitor().facing());
      }
      if (snapshot.monitor().itemKey() != null) {
        monitorTag.putString(FIELD_ITEM_KEY, snapshot.monitor().itemKey());
      }
      byte[] itemBlob = snapshot.monitor().itemBlob();
      if (itemBlob != null) {
        monitorTag.putByteArray(FIELD_ITEM_BLOB, itemBlob);
      }
      exort.put(SECTION_MONITOR, monitorTag.build());
      any = true;
    }
    if (snapshot.relay() != null) {
      LinCompoundTag.Builder relayTag = LinCompoundTag.builder();
      relayTag.putByte(FIELD_PRESENT, (byte) 1);
      RelayLinkData link = snapshot.relay().link();
      if (link != null) {
        relayTag.putString(FIELD_LINK_WORLD, link.worldId().toString());
        relayTag.putString(FIELD_LINK_X, Integer.toString(link.x()));
        relayTag.putString(FIELD_LINK_Y, Integer.toString(link.y()));
        relayTag.putString(FIELD_LINK_Z, Integer.toString(link.z()));
      }
      exort.put(SECTION_RELAY, relayTag.build());
      any = true;
    }
    if (snapshot.transmitter()) {
      LinCompoundTag.Builder transmitterTag = LinCompoundTag.builder();
      transmitterTag.putByte(FIELD_PRESENT, (byte) 1);
      writeTransmitterData(transmitterTag, snapshot.transmitterData());
      exort.put(SECTION_TRANSMITTER, transmitterTag.build());
      any = true;
    }
    if (snapshot.chunkLoader() != null) {
      LinCompoundTag.Builder chunkLoaderTag = LinCompoundTag.builder();
      chunkLoaderTag.putString(FIELD_ID, snapshot.chunkLoader().id().toString());
      chunkLoaderTag.putString(FIELD_TYPE, snapshot.chunkLoader().type().id());
      if (snapshot.chunkLoader().placedByUuid() != null) {
        chunkLoaderTag.putString(
            FIELD_PLACED_BY_UUID, snapshot.chunkLoader().placedByUuid().toString());
      }
      if (snapshot.chunkLoader().placedByName() != null) {
        chunkLoaderTag.putString(FIELD_PLACED_BY_NAME, snapshot.chunkLoader().placedByName());
      }
      if (snapshot.chunkLoader().createdAt() > 0L) {
        chunkLoaderTag.putString(
            FIELD_CREATED_AT, Long.toString(snapshot.chunkLoader().createdAt()));
      }
      chunkLoaderTag.putString(FIELD_ENABLED, Boolean.toString(snapshot.chunkLoader().enabled()));
      chunkLoaderTag.putString(
          FIELD_BYPASS_LIMITS, Boolean.toString(snapshot.chunkLoader().bypassLimits()));
      exort.put(SECTION_CHUNK_LOADER, chunkLoaderTag.build());
      any = true;
    }
    if (snapshot.wire()) {
      LinCompoundTag.Builder wireTag = LinCompoundTag.builder();
      wireTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_WIRE, wireTag.build());
      any = true;
    }
    return any ? exort.build() : null;
  }

  static LinCompoundTag removeExort(LinCompoundTag root) {
    if (root == null) return null;
    return root.toBuilder().remove(EXORT_TAG).build();
  }

  static LinCompoundTag readExort(LinCompoundTag root) {
    if (root == null) return null;
    return root.findTag(EXORT_TAG, LinTagType.compoundTag());
  }

  static MarkerSnapshot parseSnapshot(LinCompoundTag exort) {
    if (exort == null) return null;
    StorageData storage = null;
    TerminalData terminal = null;
    BusData bus = null;
    MonitorData monitor = null;
    RelayData relay = null;
    TransmitterData transmitterData = null;
    ChunkLoaderData chunkLoader = null;
    boolean transmitter = false;
    boolean wire = false;
    boolean storageCore = false;

    LinCompoundTag storageTag = getCompound(exort, SECTION_STORAGE);
    if (storageTag != null) {
      String id = readString(storageTag, FIELD_ID);
      String tier = readString(storageTag, FIELD_TIER);
      Long tierMaxItems = readPositiveLongString(storageTag, FIELD_TIER_MAX_ITEMS);
      String facing = readString(storageTag, FIELD_FACING);
      String displayName = readString(storageTag, FIELD_NAME);
      if (id != null && tier != null) {
        storage = new StorageData(id, tier, tierMaxItems, facing, displayName);
      }
    }

    LinCompoundTag terminalTag = getCompound(exort, SECTION_TERMINAL);
    if (terminalTag != null) {
      terminal =
          new TerminalData(
              readString(terminalTag, FIELD_TYPE), readString(terminalTag, FIELD_FACING));
    }

    LinCompoundTag busTag = getCompound(exort, SECTION_BUS);
    if (busTag != null) {
      String type = readString(busTag, FIELD_TYPE);
      String mode = readString(busTag, FIELD_MODE);
      String facing = readString(busTag, FIELD_FACING);
      byte[] filters = readByteArray(busTag, FIELD_FILTERS);
      if (type != null || mode != null || facing != null) {
        bus = new BusData(type, facing, mode, filters);
      }
    }

    LinCompoundTag monitorTag = getCompound(exort, SECTION_MONITOR);
    if (monitorTag != null) {
      monitor =
          new MonitorData(
              readString(monitorTag, FIELD_FACING),
              readString(monitorTag, FIELD_ITEM_KEY),
              readByteArray(monitorTag, FIELD_ITEM_BLOB));
    }

    LinCompoundTag relayTag = getCompound(exort, SECTION_RELAY);
    if (relayTag != null && readPresent(relayTag, FIELD_PRESENT)) {
      relay = new RelayData(readRelayLink(relayTag));
    }

    LinCompoundTag transmitterTag = getCompound(exort, SECTION_TRANSMITTER);
    if (transmitterTag != null) {
      transmitter = readPresent(transmitterTag, FIELD_PRESENT);
      if (transmitter) {
        transmitterData =
            new TransmitterData(
                readString(transmitterTag, FIELD_MODE),
                readByteArray(transmitterTag, FIELD_TERMINAL_BLOB),
                readByteArray(transmitterTag, FIELD_BOOSTER_BLOB));
      }
    }

    LinCompoundTag chunkLoaderTag = getCompound(exort, SECTION_CHUNK_LOADER);
    if (chunkLoaderTag != null) {
      UUID id = readUuid(chunkLoaderTag, FIELD_ID);
      if (id != null) {
        Optional<ChunkLoaderType> type =
            ChunkLoaderType.fromNullableId(readString(chunkLoaderTag, FIELD_TYPE));
        if (type.isPresent()) {
          Long createdAt = readPositiveLongString(chunkLoaderTag, FIELD_CREATED_AT);
          chunkLoader =
              new ChunkLoaderData(
                  id,
                  type.orElseThrow(),
                  readUuid(chunkLoaderTag, FIELD_PLACED_BY_UUID),
                  readString(chunkLoaderTag, FIELD_PLACED_BY_NAME),
                  createdAt == null ? 0L : createdAt,
                  readBooleanString(chunkLoaderTag, FIELD_ENABLED, true),
                  readBooleanString(chunkLoaderTag, FIELD_BYPASS_LIMITS, false));
        }
      }
    }

    LinCompoundTag wireTag = getCompound(exort, SECTION_WIRE);
    if (wireTag != null) wire = readPresent(wireTag, FIELD_PRESENT);
    LinCompoundTag coreTag = getCompound(exort, SECTION_STORAGE_CORE);
    if (coreTag != null) storageCore = readPresent(coreTag, FIELD_PRESENT);

    if (storage == null
        && terminal == null
        && bus == null
        && monitor == null
        && relay == null
        && !transmitter
        && chunkLoader == null
        && !wire
        && !storageCore) {
      return null;
    }
    return new MarkerSnapshot(
        storage,
        terminal,
        bus,
        monitor,
        relay,
        transmitter,
        transmitterData,
        chunkLoader,
        wire,
        storageCore);
  }

  static int primaryMarkerCount(MarkerSnapshot snapshot) {
    if (snapshot == null) return 0;
    int count = 0;
    if (snapshot.storage() != null) count++;
    if (snapshot.storageCore()) count++;
    if (snapshot.terminal() != null) count++;
    if (snapshot.bus() != null) count++;
    if (snapshot.monitor() != null) count++;
    if (snapshot.relay() != null) count++;
    if (snapshot.transmitter()) count++;
    if (snapshot.chunkLoader() != null) count++;
    if (snapshot.wire()) count++;
    return count;
  }

  static String snapshotSections(MarkerSnapshot snapshot) {
    if (snapshot == null) return "none";
    StringBuilder sections = new StringBuilder();
    appendSection(sections, snapshot.storage() != null, SECTION_STORAGE);
    appendSection(sections, snapshot.storageCore(), SECTION_STORAGE_CORE);
    appendSection(sections, snapshot.terminal() != null, SECTION_TERMINAL);
    appendSection(sections, snapshot.bus() != null, SECTION_BUS);
    appendSection(sections, snapshot.monitor() != null, SECTION_MONITOR);
    appendSection(sections, snapshot.relay() != null, SECTION_RELAY);
    appendSection(sections, snapshot.transmitter(), SECTION_TRANSMITTER);
    appendSection(sections, snapshot.chunkLoader() != null, SECTION_CHUNK_LOADER);
    appendSection(sections, snapshot.wire(), SECTION_WIRE);
    return sections.isEmpty() ? "none" : sections.toString();
  }

  static void writeTransmitterData(LinCompoundTag.Builder transmitterTag, TransmitterData data) {
    if (transmitterTag == null || data == null) return;
    if (data.mode() != null) transmitterTag.putString(FIELD_MODE, data.mode());
    byte[] terminalBlob = data.terminalBlob();
    if (terminalBlob != null && terminalBlob.length > 0) {
      transmitterTag.putByteArray(FIELD_TERMINAL_BLOB, terminalBlob);
    }
    byte[] boosterBlob = data.boosterBlob();
    if (boosterBlob != null && boosterBlob.length > 0) {
      transmitterTag.putByteArray(FIELD_BOOSTER_BLOB, boosterBlob);
    }
  }

  private static LinCompoundTag getCompound(LinCompoundTag root, String key) {
    return root == null ? null : root.findTag(key, LinTagType.compoundTag());
  }

  private static String readString(LinCompoundTag root, String key) {
    if (root == null) return null;
    LinStringTag tag = root.findTag(key, LinTagType.stringTag());
    return tag == null ? null : tag.value();
  }

  private static byte[] readByteArray(LinCompoundTag root, String key) {
    if (root == null) return null;
    LinByteArrayTag tag = root.findTag(key, LinTagType.byteArrayTag());
    return tag == null ? null : tag.value();
  }

  private static Long readPositiveLongString(LinCompoundTag root, String key) {
    String raw = readString(root, key);
    if (raw == null || raw.isBlank()) return null;
    try {
      long value = Long.parseLong(raw.trim());
      return value > 0 ? value : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static boolean readPresent(LinCompoundTag root, String key) {
    if (root == null) return false;
    LinByteTag tag = root.findTag(key, LinTagType.byteTag());
    return tag != null && tag.valueAsByte() == (byte) 1;
  }

  private static boolean readBooleanString(LinCompoundTag root, String key, boolean defaultValue) {
    String raw = readString(root, key);
    return raw == null || raw.isBlank() ? defaultValue : Boolean.parseBoolean(raw.trim());
  }

  private static UUID readUuid(LinCompoundTag root, String key) {
    String raw = readString(root, key);
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static RelayLinkData readRelayLink(LinCompoundTag relayTag) {
    String worldRaw = readString(relayTag, FIELD_LINK_WORLD);
    String xRaw = readString(relayTag, FIELD_LINK_X);
    String yRaw = readString(relayTag, FIELD_LINK_Y);
    String zRaw = readString(relayTag, FIELD_LINK_Z);
    if (worldRaw == null || xRaw == null || yRaw == null || zRaw == null) return null;
    try {
      return new RelayLinkData(
          UUID.fromString(worldRaw),
          Integer.parseInt(xRaw),
          Integer.parseInt(yRaw),
          Integer.parseInt(zRaw));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static void appendSection(StringBuilder builder, boolean present, String section) {
    if (!present) return;
    if (!builder.isEmpty()) builder.append(',');
    builder.append(section);
  }
}
