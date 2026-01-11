package com.zxcmc.exort.core.breaking;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public final class BreakSoundConfig {
    private static final double RANGE = 16.0;
    private static final int INTERVAL_TICKS = 4;
    private static final int PRE_BREAK_TICKS = 8;
    private static final float PITCH = 0.8f;

    private final boolean enabled;
    private final float volume;
    private final SoundProfile storage;
    private final SoundProfile terminal;
    private final SoundProfile monitor;
    private final SoundProfile bus;
    private final SoundProfile wire;

    private BreakSoundConfig(boolean enabled, float volume,
                             SoundProfile storage, SoundProfile terminal, SoundProfile monitor, SoundProfile bus, SoundProfile wire) {
        this.enabled = enabled;
        this.volume = volume;
        this.storage = storage;
        this.terminal = terminal;
        this.monitor = monitor;
        this.bus = bus;
        this.wire = wire;
    }

    public boolean enabled() {
        return enabled;
    }

    public double range() {
        return RANGE;
    }

    public int intervalTicks() {
        return INTERVAL_TICKS;
    }

    public int preBreakTicks() {
        return PRE_BREAK_TICKS;
    }

    public float volume() {
        return volume;
    }

    public float hitVolume() {
        return Math.max(0f, volume - 0.4f);
    }

    public float pitch() {
        return PITCH;
    }

    public String hitKey(BreakType type) {
        return profile(type).hitKey();
    }

    public String breakKey(BreakType type) {
        return profile(type).breakKey();
    }

    public String placeKey(BreakType type) {
        return profile(type).placeKey();
    }

    private SoundProfile profile(BreakType type) {
        return switch (type) {
            case TERMINAL -> terminal;
            case MONITOR -> monitor;
            case BUS -> bus;
            case WIRE -> wire;
            case STORAGE -> storage;
            default -> storage;
        };
    }

    public static BreakSoundConfig fromConfig(FileConfiguration config) {
        boolean enabled = config.getBoolean("break.sounds.enabled", true);
        float volume = (float) config.getDouble("break.sounds.volume", 0.2);
        SoundProfile storage = readProfile(config, "break.sounds.storage",
                "block.stone.hit", "block.stone.break", "block.stone.place");
        SoundProfile terminal = readProfile(config, "break.sounds.terminal",
                "block.wood.hit", "block.wood.break", "block.wood.place");
        SoundProfile monitor = readProfile(config, "break.sounds.monitor",
                "block.wood.hit", "block.wood.break", "block.wood.place");
        SoundProfile bus = readProfile(config, "break.sounds.bus",
                "block.wood.hit", "block.wood.break", "block.wood.place");
        SoundProfile wire = readProfile(config, "break.sounds.wire",
                "block.glass.hit", "block.glass.break", "block.glass.place");
        return new BreakSoundConfig(enabled, volume, storage, terminal, monitor, bus, wire);
    }

    private static SoundProfile readProfile(FileConfiguration config, String basePath, String defHit, String defBreak, String defPlace) {
        String hitKey = config.getString(basePath + ".hit", defHit);
        String breakKey = config.getString(basePath + ".break", defBreak);
        String placeKey = config.getString(basePath + ".place", defPlace);
        return new SoundProfile(normalizeKey(hitKey), normalizeKey(breakKey), normalizeKey(placeKey));
    }

    private static String normalizeKey(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.contains(":")) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return "minecraft:" + normalized.toLowerCase(Locale.ROOT);
    }

    private record SoundProfile(String hitKey, String breakKey, String placeKey) {}
}
