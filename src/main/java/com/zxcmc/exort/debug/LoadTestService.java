package com.zxcmc.exort.debug;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.db.Database;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.ui.BossBarManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

public final class LoadTestService {
    private static final int DEFAULT_DURATION_TICKS = 20 * 60;
    private static final int BUS_FILTER_SLOTS = 10;
    private static final int MAX_DB_POSITIONS = 64;
    private static final String BENCHMARK_TAG = "exort_benchmark";
    // Hardcoded benchmark model parameters (no config overrides).
    private static final int DEFAULT_PLAYERS = 25;
    private static final int DEFAULT_CHUNKS_PER_PLAYER = 4;
    private static final double DEFAULT_CHUNK_FILL_RATIO = 0.20;
    private static final double DEFAULT_BUS_RATIO = 0.60;
    private static final double DEFAULT_MONITOR_RATIO = 0.40;
    private static final int DEFAULT_STORAGE_PER_CHUNK = 1;
    private static final boolean DEFAULT_USE_MAX_STORAGE_PER_CHUNK = true;
    private static final int DEFAULT_AVG_Y_HEIGHT = 16;
    private static final boolean DEFAULT_SIMULATE_WIRE_SCAN = true;
    private static final double DEFAULT_WIRE_SCAN_MISS_RATIO = 0.10;
    private static final double DEFAULT_WIRE_SCAN_COVERAGE = 0.35;
    private static final boolean DEFAULT_SIMULATE_DISPLAYS = true;
    private static final int DEFAULT_DISPLAY_SAMPLE_LIMIT = 120;
    private static final boolean DEFAULT_LOAD_CHUNKS = true;
    private static final int DEFAULT_MAX_LOADED_CHUNKS = 4096;
    private static final int DEFAULT_CHUNK_GROUP_SPACING = 1;
    private static final int DEFAULT_CPU_ITERATIONS_PER_OP = 300;
    private static final int DEFAULT_WIRE_ITERATIONS_PER_BLOCK = 8;
    private static final int DEFAULT_MONITOR_ITERATIONS_PER_UPDATE = 40;
    private static final double DEFAULT_DB_OPS_PER_TICK_FACTOR = 0.25;
    private static final int DEFAULT_MAX_DB_OPS_PER_TICK = 1000;
    private static final double DEFAULT_BUS_ACTIVE_RATIO = 0.8;
    private static final double DEFAULT_STOP_TPS_THRESHOLD = 5.0;
    private static final double DEFAULT_OPS_JITTER_PERCENT = 0.10;
    private static final int DEFAULT_PROGRESS_INTERVAL_TICKS = 20;
    private static final int DEFAULT_MONITOR_INTERVAL_TICKS = 20;
    private static final int DEFAULT_WARMUP_SECONDS = 5;
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String LOG_PREFIX = "[Exort] ";

    private final ExortPlugin plugin;
    private final Database database;
    private final BossBarManager bossBarManager;
    private final Lang lang;

    private int taskId = -1;
    private UUID ownerId;
    private CommandSender owner;
    private int simulatedPlayers;
    private long startTick;
    private long lastTickNs;
    private double[] tickMs;
    private double[] msptSamples;
    private int sampleIndex;
    private BusPos[] dbPositions;
    private BusSettings[] dbSettings;
    private int activeIntervalTicks;
    private int idleIntervalTicks;
    private int itemsPerOperation;
    private int maxOperationsPerTick;
    private int maxOperationsPerChunk;
    private int wireLimit;
    private int wireHardCap;
    private int chunksPerPlayer;
    private int storagePerChunk;
    private int avgYHeight;
    private int displaySampleLimit;
    private int cpuIterationsPerOp;
    private int wireIterationsPerBlock;
    private int monitorIterationsPerUpdate;
    private int maxDbOpsPerTick;
    private int durationTicks;
    private int warmupTicks;
    private int progressIntervalTicks;
    private int monitorIntervalTicks;
    private int flushIntervalTicks;
    private int idleCheckTicks;
    private int idleUnloadTicks;
    private int maxLoadedChunks;
    private int chunkGroupSpacing;
    private int lastOpsPerChunk;
    private double chunkFillRatio;
    private double busRatio;
    private double monitorRatio;
    private double dbOpsPerTickFactor;
    private double busActiveRatio;
    private double stopTpsThreshold;
    private double opsJitterPercent;
    private double wireScanMissRatio;
    private double wireScanCoverage;
    private boolean simulateWireScan;
    private boolean simulateDisplays;
    private boolean wirelessEnabled;
    private int wirelessRangeChunks;
    private boolean useMaxStoragePerChunk;
    private boolean loadChunks;
    private int busCountPerChunk;
    private int monitorCountPerChunk;
    private java.util.List<java.util.UUID> displayEntities;
    private java.util.List<ChunkTicket> loadedChunks;
    private org.bukkit.World benchmarkWorld;
    private java.util.Random jitterRandom;

    public LoadTestService(ExortPlugin plugin, Database database, BossBarManager bossBarManager, Lang lang) {
        this.plugin = plugin;
        this.database = database;
        this.bossBarManager = bossBarManager;
        this.lang = lang;
    }

    public boolean isRunning() {
        return taskId != -1;
    }

    public void start(CommandSender sender, int players) {
        start(sender, players, 0);
    }

    public void start(CommandSender sender, int players, int durationSecondsOverride) {
        if (isRunning()) {
            sender.sendMessage(lang.tr("message.debug_load_running"));
            return;
        }
        this.owner = sender;
        this.ownerId = sender instanceof Player p ? p.getUniqueId() : null;
        this.simulatedPlayers = players;
        readConfig(durationSecondsOverride);
        this.startTick = Bukkit.getCurrentTick();
        this.lastTickNs = 0L;
        this.sampleIndex = 0;
        this.tickMs = new double[Math.max(1, durationTicks - warmupTicks)];
        this.msptSamples = new double[tickMs.length];
        this.jitterRandom = new java.util.Random(System.nanoTime());
        prepareChunks(sender);
        prepareDbPositions(simulatedPlayers);
        prepareDisplaySamples(sender);
        String summary = summaryLine();
        String prefixedSummary = LOG_PREFIX + summary;
        sender.sendMessage(sender == Bukkit.getConsoleSender() ? prefixedSummary : summary);
        sendToConsoleIfNeeded(summary);
        String started = lang.tr("message.debug_load_started", simulatedPlayers);
        String prefixedStarted = LOG_PREFIX + started;
        sender.sendMessage(sender == Bukkit.getConsoleSender() ? prefixedStarted : started);
        sendToConsoleIfNeeded(started);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
    }

    public void stop(boolean notify) {
        if (!isRunning()) return;
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        if (notify && owner != null) {
            String stopped = lang.tr("message.debug_load_stopped");
            owner.sendMessage(owner == Bukkit.getConsoleSender() ? LOG_PREFIX + stopped : stopped);
        }
        if (notify) {
            sendToConsoleIfNeeded(lang.tr("message.debug_load_stopped"));
        }
        cleanupDbPositions();
        cleanupDisplays();
        cleanupChunks();
        if (ownerId != null) {
            Player player = Bukkit.getPlayer(ownerId);
            if (player != null && player.isOnline()) {
                bossBarManager.remove(player);
            }
        }
        owner = null;
        ownerId = null;
    }

    private void tick() {
        long elapsed = Bukkit.getCurrentTick() - startTick;
        long now = System.nanoTime();
        if (lastTickNs != 0L) {
            if (elapsed >= warmupTicks && sampleIndex < tickMs.length) {
                double ms = (now - lastTickNs) / 1_000_000.0;
                tickMs[sampleIndex] = ms;
                msptSamples[sampleIndex] = currentMspt();
                sampleIndex++;
            }
        }
        lastTickNs = now;
        int ops = estimateOperationsPerTick();
        ops = applyJitter(ops);
        int chunks = Math.max(1, activeChunkCount());
        lastOpsPerChunk = (int) Math.max(0, Math.floor(ops / (double) chunks));
        simulateCpu(ops);
        simulateWireScan(ops);
        simulateDb(ops);
        simulateCacheMaintenance(elapsed);
        simulateWirelessChecks();

        if (elapsed % Math.max(1, progressIntervalTicks) == 0L) {
            sendProgress((int) elapsed);
        }
        if (elapsed % Math.max(1, monitorIntervalTicks) == 0L) {
            simulateMonitorUpdates();
        }
        if (stopTpsThreshold > 0 && currentTps() < stopTpsThreshold) {
            finishForced("message.debug_load_grade_awful");
            return;
        }
        if (elapsed >= durationTicks) {
            finish();
        }
    }

    private void finish() {
        finishForced(null);
    }

    private void finishForced(String forcedGradeKey) {
        String verdict = verdict(forcedGradeKey);
        if (owner != null) {
            owner.sendMessage(owner == Bukkit.getConsoleSender() ? LOG_PREFIX + verdict : verdict);
        }
        sendToConsoleIfNeeded(verdict);
        String hints = profileHints();
        if (hints != null && !hints.isBlank()) {
            if (owner != null) {
                owner.sendMessage(owner == Bukkit.getConsoleSender() ? LOG_PREFIX + hints : hints);
            }
            sendToConsoleIfNeeded(hints);
        }
        stop(false);
    }

    private void sendProgress(int elapsedTicks) {
        int remainingTicks = Math.max(0, durationTicks - elapsedTicks);
        int remainingSeconds = remainingTicks / 20;
        double progress = Math.min(1.0, elapsedTicks / (double) durationTicks);
        int percent = (int) Math.round(progress * 100.0);
        double tps = currentTps();
        double mspt = currentMspt();
        String message = lang.tr("message.debug_load_progress",
                percent,
                remainingSeconds,
                ONE_DECIMAL.format(tps),
                ONE_DECIMAL.format(mspt));
        if (owner != null) {
            owner.sendMessage(owner == Bukkit.getConsoleSender() ? LOG_PREFIX + message : message);
            if (ownerId != null) {
                Player player = Bukkit.getPlayer(ownerId);
                if (player != null && player.isOnline()) {
                    bossBarManager.showProgress(player, message, progress, progressColor(tps));
                }
            }
        }
        sendToConsoleIfNeeded(message);
    }

    private double currentTps() {
        try {
            double[] tps = Bukkit.getTPS();
            return tps.length > 0 ? Math.min(20.0, tps[0]) : 20.0;
        } catch (NoSuchMethodError ignored) {
            return 20.0;
        }
    }

    private double currentMspt() {
        try {
            return Bukkit.getAverageTickTime();
        } catch (NoSuchMethodError ignored) {
            return 50.0;
        }
    }

    private BarColor progressColor(double tps) {
        if (tps >= 18.0) return BarColor.GREEN;
        if (tps >= 15.0) return BarColor.YELLOW;
        return BarColor.RED;
    }

    private void simulateCpu(int ops) {
        int iterations = Math.max(1, ops * itemsPerOperation * cpuIterationsPerOp);
        long acc = 1;
        for (int i = 0; i < iterations; i++) {
            acc = acc * 31 + i;
        }
        if (acc == 42) {
            Bukkit.getLogger().fine("stress");
        }
    }

    private void simulateDb(int ops) {
        if (dbPositions == null || dbPositions.length == 0) return;
        int dbOps = estimateDbOpsPerTick(ops);
        simulateDbOps(dbOps);
    }

    private void simulateDbOps(int ops) {
        if (dbPositions == null || dbPositions.length == 0 || ops <= 0) return;
        int count = Math.max(1, dbPositions.length);
        for (int i = 0; i < ops; i++) {
            int idx = i % count;
            BusSettings settings = dbSettings[idx];
            database.saveBusSettings(settings, BUS_FILTER_SLOTS);
            database.loadBusSettings(settings.pos(), BUS_FILTER_SLOTS);
        }
    }

    private void prepareDbPositions(int players) {
        int count = Math.max(1, Math.min(MAX_DB_POSITIONS, players * chunksPerPlayer));
        dbPositions = new BusPos[count];
        dbSettings = new BusSettings[count];
        UUID world = UUID.nameUUIDFromBytes("exort-stress-world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < count; i++) {
            BusPos pos = new BusPos(world, 300000 + i, 64, 300000 + i);
            dbPositions[i] = pos;
            dbSettings[i] = new BusSettings(pos, BusType.IMPORT, BusMode.ALL, new ItemStack[0]);
        }
    }

    private void cleanupDbPositions() {
        if (dbPositions == null) return;
        for (BusPos pos : dbPositions) {
            database.deleteBusSettings(pos);
        }
        dbPositions = null;
        dbSettings = null;
    }

    private String verdict(String forcedGradeKey) {
        int n = Math.min(sampleIndex, tickMs.length);
        if (n <= 0) {
            return lang.tr("message.debug_load_verdict", "UNKNOWN", "0", "0", "0", "0", "0", "0");
        }
        double[] tpsSamples = new double[n];
        for (int i = 0; i < n; i++) {
            double ms = tickMs[i];
            tpsSamples[i] = ms <= 0.0 ? 20.0 : Math.min(20.0, 1000.0 / ms);
        }
        double min = tpsSamples[0];
        double max = tpsSamples[0];
        double sum = 0.0;
        for (double v : tpsSamples) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        double avg = sum / tpsSamples.length;
        java.util.Arrays.sort(tpsSamples);
        int idx = Math.max(0, (int) Math.ceil(tpsSamples.length * 0.001) - 1);
        double p001 = tpsSamples[idx];
        double[] msptSorted = java.util.Arrays.copyOf(msptSamples, n);
        java.util.Arrays.sort(msptSorted);
        double msptAvg = 0.0;
        double msptMin = msptSorted[0];
        double msptMax = msptSorted[n - 1];
        for (int i = 0; i < n; i++) {
            msptAvg += msptSamples[i];
        }
        msptAvg /= n;
        double msptP95 = msptSorted[Math.max(0, (int) Math.ceil(n * 0.95) - 1)];
        double msptP99 = msptSorted[Math.max(0, (int) Math.ceil(n * 0.99) - 1)];
        String gradeKey = forcedGradeKey != null ? forcedGradeKey : gradeKey(min, avg, p001, msptAvg, msptP95);
        String grade = lang.tr(gradeKey);
        return lang.tr("message.debug_load_verdict",
                grade,
                TWO_DECIMALS.format(min),
                TWO_DECIMALS.format(max),
                TWO_DECIMALS.format(avg),
                TWO_DECIMALS.format(p001),
                ONE_DECIMAL.format(msptMin),
                ONE_DECIMAL.format(msptMax),
                ONE_DECIMAL.format(msptAvg),
                ONE_DECIMAL.format(msptP95),
                ONE_DECIMAL.format(msptP99));
    }

    private String gradeKey(double min, double avg, double p001, double msptAvg, double msptP95) {
        if (avg < 15.0 || p001 < 12.0 || msptAvg >= 70.0 || msptP95 >= 80.0) {
            return "message.debug_load_grade_awful";
        }
        if (avg >= 19.5 && p001 >= 18.0 && msptAvg <= 35.0 && msptP95 <= 45.0) {
            return "message.debug_load_grade_good";
        }
        if (avg >= 18.5 && p001 >= 17.0 && msptAvg <= 50.0 && msptP95 <= 60.0) {
            return "message.debug_load_grade_warn";
        }
        if (avg >= 16.0 && msptAvg <= 60.0) {
            return "message.debug_load_grade_poor";
        }
        return "message.debug_load_grade_bad";
    }

    private int estimateOperationsPerTick() {
        int chunks = activeChunkCount();
        double activeRatio = Math.min(1.0, Math.max(0.0, busActiveRatio));
        double perBus = activeRatio / Math.max(1.0, activeIntervalTicks)
                + (1.0 - activeRatio) / Math.max(1.0, idleIntervalTicks);
        double activePerChunk = busCountPerChunk * perBus;
        int perChunkOps = Math.max(0, (int) Math.round(activePerChunk));
        if (maxOperationsPerChunk > 0) {
            perChunkOps = Math.min(perChunkOps, maxOperationsPerChunk);
        }
        long total = (long) perChunkOps * (long) chunks;
        if (total > maxOperationsPerTick) {
            double scale = maxOperationsPerTick / (double) Math.max(1L, total);
            lastOpsPerChunk = Math.max(0, (int) Math.floor(perChunkOps * scale));
            return maxOperationsPerTick;
        }
        lastOpsPerChunk = perChunkOps;
        return (int) Math.max(0, total);
    }

    private int estimateDbOpsPerTick(int ops) {
        int dbOps = (int) Math.round(ops * dbOpsPerTickFactor);
        if (maxDbOpsPerTick > 0) {
            dbOps = Math.min(dbOps, maxDbOpsPerTick);
        }
        return Math.max(0, dbOps);
    }

    private String profileHints() {
        int ops = estimateOperationsPerTick();
        int dbOps = estimateDbOpsPerTick(ops);
        int chunks = activeChunkCount();
        int wireLen = Math.max(1, Math.min(wireLimit, wireHardCap > 0 ? wireHardCap : wireLimit));
        long cpuCost = (long) ops * (long) itemsPerOperation * (long) cpuIterationsPerOp;
        long wireCost = 0L;
        if (simulateWireScan) {
            int storages = Math.max(0, storagePerChunk * chunks);
            int bfsNetworks = Math.max(0, (int) Math.ceil(storages * wireScanMissRatio));
            double busesPerStorage = storagePerChunk > 0 ? busCountPerChunk / (double) storagePerChunk : 0.0;
            double monitorsPerStorage = storagePerChunk > 0 ? monitorCountPerChunk / (double) storagePerChunk : 0.0;
            double nodesPerStorage = 1.0 + (6.0 * wireLen) + busesPerStorage + monitorsPerStorage;
            double visited = nodesPerStorage * Math.max(0.0, Math.min(1.0, wireScanCoverage));
            wireCost = (long) Math.ceil(bfsNetworks * visited * Math.max(1, wireIterationsPerBlock));
        }
        int updates = 0;
        if (simulateDisplays && monitorIterationsPerUpdate > 0) {
            int storageChanges = Math.max(0, Math.min(storagePerChunk, lastOpsPerChunk));
            int monitorsPerStorage = storagePerChunk > 0
                    ? Math.max(1, (int) Math.ceil(monitorCountPerChunk / (double) storagePerChunk))
                    : monitorCountPerChunk;
            int updatesPerChunk = Math.min(monitorCountPerChunk, storageChanges * monitorsPerStorage);
            updates = updatesPerChunk * chunks;
        }
        long monitorCost = simulateDisplays ? (long) updates * (long) Math.max(1, monitorIterationsPerUpdate) : 0L;
        long dbCost = dbOps > 0 ? (long) dbOps * (long) Math.max(1, cpuIterationsPerOp / 2) : 0L;
        long wirelessCost = wirelessEnabled
                ? (long) Math.max(1, simulatedPlayers) * (long) Math.max(1, wirelessRangeChunks) * (long) Math.max(1, cpuIterationsPerOp / 12)
                : 0L;
        long total = cpuCost + wireCost + monitorCost + dbCost + wirelessCost;
        if (total <= 0) return null;
        int cpuPct = (int) Math.round(cpuCost * 100.0 / total);
        int wirePct = (int) Math.round(wireCost * 100.0 / total);
        int monitorPct = (int) Math.round(monitorCost * 100.0 / total);
        int dbPct = (int) Math.round(dbCost * 100.0 / total);
        int wirelessPct = (int) Math.round(wirelessCost * 100.0 / total);
        String dominant = "CPU";
        long max = cpuCost;
        if (wireCost > max) { max = wireCost; dominant = "WIRE"; }
        if (monitorCost > max) { max = monitorCost; dominant = "DISPLAYS"; }
        if (dbCost > max) { max = dbCost; dominant = "DB"; }
        if (wirelessCost > max) { dominant = "WIRELESS"; }
        return lang.tr("message.debug_load_hints",
                dominant,
                cpuPct,
                wirePct,
                monitorPct,
                dbPct,
                wirelessPct);
    }

    private String summaryLine() {
        int chunks = activeChunkCount();
        int ops = estimateOperationsPerTick();
        int dbOps = estimateDbOpsPerTick(ops);
        return lang.tr(
                "message.debug_load_summary",
                simulatedPlayers,
                chunks,
                storagePerChunk,
                busCountPerChunk,
                monitorCountPerChunk,
                ops,
                maxOperationsPerTick,
                maxOperationsPerChunk <= 0 ? "-" : Integer.toString(maxOperationsPerChunk),
                dbOps,
                monitorCountPerChunk * chunks,
                Math.max(1, monitorIntervalTicks),
                wireLimit,
                wireHardCap <= 0 ? "-" : Integer.toString(wireHardCap),
                ONE_DECIMAL.format(Math.max(0.0, wireScanMissRatio * 100.0)),
                ONE_DECIMAL.format(Math.max(0.0, wireScanCoverage * 100.0)),
                Math.max(1, durationTicks / 20),
                Math.max(0, warmupTicks / 20),
                ONE_DECIMAL.format(Math.max(0.0, opsJitterPercent * 100.0))
        );
    }

    private void simulateWireScan(int ops) {
        if (!simulateWireScan) return;
        int chunks = activeChunkCount();
        int wireLen = Math.max(1, Math.min(wireLimit, wireHardCap > 0 ? wireHardCap : wireLimit));
        int storages = Math.max(0, storagePerChunk * chunks);
        if (storages <= 0) return;
        int bfsNetworks = Math.max(0, (int) Math.ceil(storages * wireScanMissRatio));
        if (bfsNetworks <= 0) return;
        double busesPerStorage = storagePerChunk > 0 ? busCountPerChunk / (double) storagePerChunk : 0.0;
        double monitorsPerStorage = storagePerChunk > 0 ? monitorCountPerChunk / (double) storagePerChunk : 0.0;
        double nodesPerStorage = 1.0 + (6.0 * wireLen) + busesPerStorage + monitorsPerStorage;
        double visited = nodesPerStorage * Math.max(0.0, Math.min(1.0, wireScanCoverage));
        long iterations = (long) Math.ceil(bfsNetworks * visited * Math.max(1, wireIterationsPerBlock));
        if (iterations <= 0) return;
        long acc = 1;
        for (long i = 0; i < iterations; i++) {
            acc = acc * 17 + i;
        }
        if (acc == 42) {
            Bukkit.getLogger().fine("stress");
        }
    }

    private void simulateMonitorUpdates() {
        if (!simulateDisplays) return;
        if (monitorIterationsPerUpdate <= 0) return;
        int chunks = activeChunkCount();
        int storageChanges = Math.max(0, Math.min(storagePerChunk, lastOpsPerChunk));
        int monitorsPerStorage = storagePerChunk > 0
                ? Math.max(1, (int) Math.ceil(monitorCountPerChunk / (double) storagePerChunk))
                : monitorCountPerChunk;
        int updatesPerChunk = Math.min(monitorCountPerChunk, storageChanges * monitorsPerStorage);
        int updates = updatesPerChunk * chunks;
        if (updates <= 0) return;
        int iterations = updates * Math.max(1, monitorIterationsPerUpdate);
        long acc = 7;
        for (int i = 0; i < iterations; i++) {
            acc = acc * 13 + i;
        }
        if (acc == 42) {
            Bukkit.getLogger().fine("stress");
        }
        updateDisplaySamples(updates);
    }

    private void simulateCacheMaintenance(long elapsedTicks) {
        int storages = Math.max(0, activeChunkCount() * storagePerChunk);
        if (storages <= 0) return;
        if (flushIntervalTicks > 0 && elapsedTicks > 0 && elapsedTicks % flushIntervalTicks == 0L) {
            int dirtyStorages = Math.max(1, (int) Math.round(storages * busActiveRatio));
            int flushOps = maxDbOpsPerTick > 0 ? Math.min(dirtyStorages, maxDbOpsPerTick) : dirtyStorages;
            simulateDbOps(flushOps);
            simulateCpuIterations(dirtyStorages, Math.max(1, cpuIterationsPerOp / 6));
        }
        if (idleCheckTicks > 0 && elapsedTicks > 0 && elapsedTicks % idleCheckTicks == 0L) {
            simulateCpuIterations(storages, Math.max(1, cpuIterationsPerOp / 10));
            if (idleUnloadTicks > 0 && elapsedTicks >= idleUnloadTicks) {
                int idleStorages = Math.max(0, storages - (int) Math.round(storages * busActiveRatio));
                if (idleStorages > 0) {
                    simulateCpuIterations(idleStorages, Math.max(1, cpuIterationsPerOp / 8));
                }
            }
        }
    }

    private void simulateWirelessChecks() {
        if (!wirelessEnabled) return;
        int players = Math.max(1, simulatedPlayers);
        int iterationsPerPlayer = Math.max(1, cpuIterationsPerOp / 12);
        int iterations = players * iterationsPerPlayer * Math.max(1, wirelessRangeChunks);
        long acc = 3;
        for (int i = 0; i < iterations; i++) {
            acc = acc * 19 + i;
        }
        if (acc == 42) {
            Bukkit.getLogger().fine("wireless");
        }
    }

    private void simulateCpuIterations(int items, int iterationsPerItem) {
        int iterations = Math.max(1, items * Math.max(1, iterationsPerItem));
        long acc = 5;
        for (int i = 0; i < iterations; i++) {
            acc = acc * 23 + i;
        }
        if (acc == 42) {
            Bukkit.getLogger().fine("cache");
        }
    }

    private boolean prepareDisplaySamples(CommandSender sender) {
        cleanupDisplays();
        if (!simulateDisplays) return true;
        cleanupTaggedDisplaysAll();
        if (!(sender instanceof Player player)) return true;
        int chunks = Math.max(1, simulatedPlayers * chunksPerPlayer);
        int target = monitorCountPerChunk * chunks;
        int count = Math.max(0, Math.min(displaySampleLimit, target));
        if (count <= 0) return true;
        displayEntities = new java.util.ArrayList<>(count * 2);
        org.bukkit.Location anchor = player.getLocation().clone();
        int maxY = player.getWorld().getMaxHeight() - 3;
        int minY = player.getWorld().getMinHeight() + 3;
        int safeY = Math.min(maxY, Math.max(minY, anchor.getWorld().getHighestBlockYAt(anchor) + 5));
        anchor.setY(safeY);
        int side = (int) Math.ceil(Math.sqrt(count));
        double spacing = 0.3;
        for (int i = 0; i < count; i++) {
            int row = i / side;
            int col = i % side;
            org.bukkit.Location base = anchor.clone().add(col * spacing, row * spacing, 0);
            org.bukkit.entity.ItemDisplay item = base.getWorld().spawn(base, org.bukkit.entity.ItemDisplay.class, display -> {
                display.setItemStack(new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE));
                display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                display.addScoreboardTag(BENCHMARK_TAG);
            });
            org.bukkit.entity.TextDisplay text = base.getWorld().spawn(base.clone().add(0, 0.1, 0), org.bukkit.entity.TextDisplay.class, display -> {
                display.text(net.kyori.adventure.text.Component.text("0"));
                display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                display.addScoreboardTag(BENCHMARK_TAG);
            });
            displayEntities.add(item.getUniqueId());
            displayEntities.add(text.getUniqueId());
        }
        return true;
    }

    private void updateDisplaySamples(int updates) {
        if (displayEntities == null || displayEntities.isEmpty()) return;
        int count = Math.min(displayEntities.size(), Math.max(1, updates));
        for (int i = 0; i < count; i++) {
            java.util.UUID id = displayEntities.get(i);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity instanceof org.bukkit.entity.ItemDisplay item) {
                item.setItemStack(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_INGOT));
            } else if (entity instanceof org.bukkit.entity.TextDisplay text) {
                text.text(net.kyori.adventure.text.Component.text(Integer.toString(updates)));
            }
        }
    }

    private void cleanupDisplays() {
        if (displayEntities == null) return;
        for (java.util.UUID id : displayEntities) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        displayEntities.clear();
        displayEntities = null;
    }

    private void cleanupTaggedDisplays(org.bukkit.World world) {
        if (world == null) return;
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(BENCHMARK_TAG)) {
                entity.remove();
            }
        }
    }

    private void cleanupTaggedDisplaysAll() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            cleanupTaggedDisplays(world);
        }
    }

    private void readConfig(int durationSecondsOverride) {
        if (simulatedPlayers <= 0) {
            simulatedPlayers = DEFAULT_PLAYERS;
        }
        activeIntervalTicks = Math.max(1, plugin.getConfig().getInt("bus.activeIntervalTicks", 5));
        idleIntervalTicks = Math.max(1, plugin.getConfig().getInt("bus.idleIntervalTicks", 40));
        itemsPerOperation = Math.max(1, plugin.getConfig().getInt("bus.itemsPerOperation", 1));
        maxOperationsPerTick = Math.max(1, plugin.getConfig().getInt("bus.maxOperationsPerTick", 6000));
        maxOperationsPerChunk = Math.max(0, plugin.getConfig().getInt("bus.maxOperationsPerChunk", 600));
        wireLimit = Math.max(1, plugin.getConfig().getInt("wire.limit", plugin.getConfig().getInt("wireLimit", 32)));
        wireHardCap = Math.max(0, plugin.getConfig().getInt("wire.hardCap", plugin.getConfig().getInt("wireHardCap", Math.max(wireLimit * 2, wireLimit))));
        chunksPerPlayer = DEFAULT_CHUNKS_PER_PLAYER;
        chunkFillRatio = DEFAULT_CHUNK_FILL_RATIO;
        busRatio = DEFAULT_BUS_RATIO;
        monitorRatio = DEFAULT_MONITOR_RATIO;
        storagePerChunk = DEFAULT_STORAGE_PER_CHUNK;
        useMaxStoragePerChunk = DEFAULT_USE_MAX_STORAGE_PER_CHUNK;
        avgYHeight = DEFAULT_AVG_Y_HEIGHT;
        simulateWireScan = DEFAULT_SIMULATE_WIRE_SCAN;
        wireScanMissRatio = DEFAULT_WIRE_SCAN_MISS_RATIO;
        wireScanCoverage = DEFAULT_WIRE_SCAN_COVERAGE;
        simulateDisplays = DEFAULT_SIMULATE_DISPLAYS;
        displaySampleLimit = DEFAULT_DISPLAY_SAMPLE_LIMIT;
        cpuIterationsPerOp = DEFAULT_CPU_ITERATIONS_PER_OP;
        wireIterationsPerBlock = DEFAULT_WIRE_ITERATIONS_PER_BLOCK;
        monitorIterationsPerUpdate = DEFAULT_MONITOR_ITERATIONS_PER_UPDATE;
        dbOpsPerTickFactor = DEFAULT_DB_OPS_PER_TICK_FACTOR;
        maxDbOpsPerTick = DEFAULT_MAX_DB_OPS_PER_TICK;
        busActiveRatio = DEFAULT_BUS_ACTIVE_RATIO;
        stopTpsThreshold = DEFAULT_STOP_TPS_THRESHOLD;
        opsJitterPercent = DEFAULT_OPS_JITTER_PERCENT;
        progressIntervalTicks = DEFAULT_PROGRESS_INTERVAL_TICKS;
        monitorIntervalTicks = DEFAULT_MONITOR_INTERVAL_TICKS;
        int flushSeconds = Math.max(0, plugin.getConfig().getInt("flushIntervalSeconds", 10));
        flushIntervalTicks = flushSeconds > 0 ? flushSeconds * 20 : 0;
        int idleUnloadSeconds = Math.max(0, plugin.getConfig().getInt("cache.idleUnloadSeconds", 900));
        idleUnloadTicks = idleUnloadSeconds > 0 ? idleUnloadSeconds * 20 : 0;
        int idleCheckSeconds = Math.max(0, plugin.getConfig().getInt("cache.idleCheckSeconds", 60));
        idleCheckTicks = idleCheckSeconds > 0 ? idleCheckSeconds * 20 : 0;
        wirelessEnabled = plugin.getConfig().getBoolean("wireless.enabled", true);
        wirelessRangeChunks = Math.max(0, plugin.getConfig().getInt("wireless.rangeChunks", 3));
        loadChunks = DEFAULT_LOAD_CHUNKS;
        maxLoadedChunks = DEFAULT_MAX_LOADED_CHUNKS;
        chunkGroupSpacing = DEFAULT_CHUNK_GROUP_SPACING;
        double duration = DEFAULT_DURATION_TICKS / 20.0;
        double warmupSeconds = DEFAULT_WARMUP_SECONDS;
        if (durationSecondsOverride > 0) {
            duration = durationSecondsOverride;
        }
        durationTicks = Math.max(20, (int) Math.round(duration * 20.0));
        warmupTicks = Math.max(0, (int) Math.round(warmupSeconds * 20.0));
        if (warmupTicks >= durationTicks) {
            warmupTicks = 0;
        }
        computeCounts();
    }

    private void sendToConsoleIfNeeded(String message) {
        if (owner != null && owner == Bukkit.getConsoleSender()) {
            return;
        }
        Bukkit.getConsoleSender().sendMessage(LOG_PREFIX + message);
    }

    private void computeCounts() {
        double ratioSum = busRatio + monitorRatio;
        if (ratioSum <= 0) {
            busRatio = 0.6;
            monitorRatio = 0.4;
            ratioSum = 1.0;
        }
        busRatio /= ratioSum;
        monitorRatio /= ratioSum;
        int totalCells = 16 * 16 * Math.max(1, avgYHeight);
        int filled = (int) Math.max(1, Math.round(totalCells * chunkFillRatio));
        int wireLen = Math.max(1, Math.min(wireLimit, wireHardCap > 0 ? wireHardCap : wireLimit));
        int wiresPerStorage = 6 * wireLen;
        int connectionsPerStorage = wiresPerStorage;
        if (useMaxStoragePerChunk) {
            int perStorageBlocks = 1 + wiresPerStorage + connectionsPerStorage;
            storagePerChunk = Math.max(1, Math.min(filled, (int) Math.floor(filled / (double) perStorageBlocks)));
        } else {
            storagePerChunk = Math.max(1, Math.min(storagePerChunk, filled));
        }
        int remaining = Math.max(0, filled - storagePerChunk * (1 + wiresPerStorage));
        int connections = storagePerChunk * connectionsPerStorage;
        int devices = Math.min(remaining, connections);
        busCountPerChunk = Math.max(0, (int) Math.round(devices * busRatio));
        monitorCountPerChunk = Math.max(0, devices - busCountPerChunk);
    }

    private int applyJitter(int ops) {
        if (ops <= 0 || opsJitterPercent <= 0.0) return ops;
        double bound = Math.max(0.0, opsJitterPercent);
        double delta = (jitterRandom.nextDouble() * 2.0 - 1.0) * bound;
        double scaled = ops * (1.0 + delta);
        return Math.max(0, (int) Math.round(scaled));
    }

    private int activeChunkCount() {
        if (loadChunks && loadedChunks != null && !loadedChunks.isEmpty()) {
            return loadedChunks.size();
        }
        return Math.max(1, simulatedPlayers * chunksPerPlayer);
    }

    private void prepareChunks(CommandSender sender) {
        cleanupChunks();
        if (!loadChunks) return;
        benchmarkWorld = (sender instanceof Player p) ? p.getWorld() : Bukkit.getWorlds().stream().findFirst().orElse(null);
        if (benchmarkWorld == null) return;
        int desired = Math.max(1, simulatedPlayers * chunksPerPlayer);
        int limit = maxLoadedChunks > 0 ? maxLoadedChunks : desired;
        int count = Math.min(desired, limit);
        int centerX = benchmarkWorld.getSpawnLocation().getBlockX() >> 4;
        int centerZ = benchmarkWorld.getSpawnLocation().getBlockZ() >> 4;
        int groups = Math.max(1, (int) Math.ceil(count / (double) chunksPerPlayer));
        int groupSide = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, chunksPerPlayer))));
        int spacing = Math.max(0, chunkGroupSpacing);
        int step = Math.max(1, groupSide + spacing);
        java.util.List<ChunkPos> plan = planChunkGroups(groups, chunksPerPlayer, centerX, centerZ, step);
        if (plan.size() > count) {
            plan = plan.subList(0, count);
        }
        loadedChunks = new java.util.ArrayList<>(plan.size());
        for (ChunkPos pos : plan) {
            org.bukkit.Chunk chunk = benchmarkWorld.getChunkAt(pos.x(), pos.z());
            chunk.addPluginChunkTicket(plugin);
            loadedChunks.add(new ChunkTicket(benchmarkWorld.getUID(), pos.x(), pos.z()));
        }
    }

    private void cleanupChunks() {
        if (loadedChunks == null || loadedChunks.isEmpty() || benchmarkWorld == null) return;
        for (ChunkTicket ticket : loadedChunks) {
            if (!benchmarkWorld.getUID().equals(ticket.world())) continue;
            org.bukkit.Chunk chunk = benchmarkWorld.getChunkAt(ticket.x(), ticket.z());
            chunk.removePluginChunkTicket(plugin);
        }
        loadedChunks.clear();
        loadedChunks = null;
        benchmarkWorld = null;
    }

    private java.util.List<ChunkPos> planChunkGroups(int groups,
                                                     int chunksPerGroup,
                                                     int centerX,
                                                     int centerZ,
                                                     int step) {
        java.util.List<ChunkPos> offsets = new java.util.ArrayList<>();
        int side = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, chunksPerGroup))));
        for (int dz = 0; dz < side && offsets.size() < chunksPerGroup; dz++) {
            for (int dx = 0; dx < side && offsets.size() < chunksPerGroup; dx++) {
                offsets.add(new ChunkPos(dx, dz));
            }
        }
        java.util.List<ChunkPos> bases = spiralPositions(groups, step);
        java.util.List<ChunkPos> result = new java.util.ArrayList<>(groups * chunksPerGroup);
        for (ChunkPos base : bases) {
            for (ChunkPos offset : offsets) {
                result.add(new ChunkPos(centerX + base.x() + offset.x(), centerZ + base.z() + offset.z()));
            }
        }
        return result;
    }

    private java.util.List<ChunkPos> spiralPositions(int count, int step) {
        java.util.List<ChunkPos> positions = new java.util.ArrayList<>(count);
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        for (int i = 0; i < count; i++) {
            positions.add(new ChunkPos(x * step, z * step));
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int tmp = dx;
                dx = -dz;
                dz = tmp;
            }
            x += dx;
            z += dz;
        }
        return positions;
    }

    private record ChunkPos(int x, int z) {
    }

    private record ChunkTicket(java.util.UUID world, int x, int z) {
    }
}
