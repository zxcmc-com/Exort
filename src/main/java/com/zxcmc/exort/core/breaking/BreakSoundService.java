package com.zxcmc.exort.core.breaking;

import org.bukkit.block.Block;

public final class BreakSoundService {
    private final BreakSoundConfig soundConfig;

    public BreakSoundService(BreakSoundConfig soundConfig) {
        this.soundConfig = soundConfig;
    }

    public boolean enabled() {
        return soundConfig != null && soundConfig.enabled();
    }

    public BreakSoundEvent handleTick(Block block, BreakType type, BreakSoundTracker tracker, long tick, double progress, double delta) {
        if (!enabled()) return BreakSoundEvent.NONE;
        BreakSoundEvent event = tracker.update(soundConfig, tick, progress, delta);
        if (event == BreakSoundEvent.HIT || event == BreakSoundEvent.BOTH) {
            playHit(block, type);
        }
        if (event == BreakSoundEvent.BREAK || event == BreakSoundEvent.BOTH) {
            playBreak(block, type);
        }
        return event;
    }

    public void playHit(Block block, BreakType type) {
        if (!enabled()) return;
        playSound(block, soundConfig.hitKey(type), soundConfig.range(), soundConfig.hitVolume(), soundConfig.pitch());
    }

    public void playBreak(Block block, BreakType type) {
        if (!enabled()) return;
        playSound(block, soundConfig.breakKey(type), soundConfig.range(), soundConfig.volume(), soundConfig.pitch());
    }

    private void playSound(Block block, String key, double range, float volume, float pitch) {
        var world = block.getWorld();
        if (world == null) return;
        var loc = block.getLocation().add(0.5, 0.5, 0.5);
        BreakSoundPlayer.play(world, loc, key, range, volume, pitch);
    }
}
