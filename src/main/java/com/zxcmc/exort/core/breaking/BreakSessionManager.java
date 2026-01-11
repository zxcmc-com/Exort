package com.zxcmc.exort.core.breaking;

import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BreakSessionManager {
    private final Map<BlockKey, BreakSession> sessions = new HashMap<>();
    private final Map<UUID, BlockKey> playerSessions = new HashMap<>();

    public Map<BlockKey, BreakSession> sessions() {
        return sessions;
    }

    public BlockKey getPlayerSession(UUID playerId) {
        return playerSessions.get(playerId);
    }

    public void clear() {
        sessions.clear();
        playerSessions.clear();
    }

    public BreakSession getSession(BlockKey key) {
        return sessions.get(key);
    }

    public BreakSession getOrCreate(Block block, BreakType type, BreakSettings settings) {
        BlockKey key = BlockKey.from(block);
        return sessions.computeIfAbsent(key, k -> new BreakSession(block, type, settings));
    }

    public void attachPlayer(BlockKey key, UUID playerId, long tick) {
        BreakSession session = sessions.get(key);
        if (session == null) return;
        playerSessions.put(playerId, key);
        session.touch(playerId, tick);
    }

    public void detachPlayer(UUID playerId) {
        BlockKey key = playerSessions.remove(playerId);
        if (key == null) return;
        BreakSession session = sessions.get(key);
        if (session == null) return;
        session.players.removeIf(state -> state.playerId.equals(playerId));
        if (session.players.isEmpty()) {
            sessions.remove(key);
        }
    }

    public void clearPlayerMapping(UUID playerId) {
        playerSessions.remove(playerId);
    }

    public void clearSession(BreakSession session) {
        for (PlayerState state : session.players) {
            playerSessions.remove(state.playerId);
        }
    }

    public void removeSession(BlockKey key) {
        BreakSession session = sessions.remove(key);
        if (session != null) {
            clearSession(session);
        }
    }

    public record BlockKey(UUID world, int x, int y, int z) {
        public static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    public static final class PlayerState {
        public final UUID playerId;
        public long lastSwingTick;

        private PlayerState(UUID playerId, long lastSwingTick) {
            this.playerId = playerId;
            this.lastSwingTick = lastSwingTick;
        }
    }

    public static final class BreakSession {
        public final Block block;
        public final BreakType type;
        public final BreakSettings settings;
        public final List<PlayerState> players = new ArrayList<>();
        public double progress = 0.0;
        public final BreakSoundTracker soundTracker = new BreakSoundTracker();

        private BreakSession(Block block, BreakType type, BreakSettings settings) {
            this.block = block;
            this.type = type;
            this.settings = settings;
        }

        public void touch(UUID playerId, long now) {
            for (PlayerState state : players) {
                if (state.playerId.equals(playerId)) {
                    state.lastSwingTick = now;
                    return;
                }
            }
            players.add(new PlayerState(playerId, now));
        }
    }
}
