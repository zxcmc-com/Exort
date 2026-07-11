package com.zxcmc.exort.debug;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.runtime.RuntimeNetworkConfig;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoadTestService {
  private static final int DEFAULT_DURATION_TICKS = 20 * 60;
  private static final int BUS_FILTER_SLOTS = 10;
  private static final int MAX_DB_POSITIONS = 64;
  private static final String BENCHMARK_TAG = "exort_benchmark";
  private static final String BENCHMARK_GUARD_TAG = "exort_benchmark_guard";
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
  private static final int DEFAULT_GUARDS_PER_PLAYER = 2;
  private static final int DEFAULT_GUARD_ROW_LENGTH = 12;
  private static final int MAX_GUARD_ARMOR_STANDS = 4096;
  private static final double GUARD_ARMOR_STAND_SCALE = 0.0625;
  private static final DecimalFormat ONE_DECIMAL =
      new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
  private static final DecimalFormat TWO_DECIMALS =
      new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
  private static final int RECENT_RUN_LIMIT = 3;
  private static final Deque<RunSummary> RECENT_RUNS = new ArrayDeque<>();

  private final JavaPlugin plugin;
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
  private int wirelessRangeBlocks;
  private boolean useMaxStoragePerChunk;
  private boolean loadChunks;
  private int busCountPerChunk;
  private int monitorCountPerChunk;
  private boolean measurementStarted;
  private int guardPlayers;
  private int effectiveGuardPlayers;
  private int targetGuardEntities;
  private int guardChurnPerTick;
  private List<UUID> displayEntities;
  private List<GuardSimulationState> guardStates;
  private List<ChunkTicket> loadedChunks;
  private World benchmarkWorld;
  private Random jitterRandom;
  private LoadTestRuntimeDependencies runtimeDependencies;
  private LoadTestWorldWorkload worldWorkload;

  public LoadTestService(
      JavaPlugin plugin, Database database, BossBarManager bossBarManager, Lang lang) {
    this.plugin = plugin;
    this.database = database;
    this.bossBarManager = bossBarManager;
    this.lang = lang;
  }

  public boolean isRunning() {
    return taskId != -1;
  }

  public void setRuntimeDependencies(LoadTestRuntimeDependencies dependencies) {
    this.runtimeDependencies = dependencies;
  }

  public void clearRuntimeDependencies() {
    if (isRunning()) {
      stop(false);
    }
    cleanupWorldWorkload();
    this.runtimeDependencies = null;
  }

  public void start(CommandSender sender, int players) {
    start(sender, players, 0);
  }

  public void start(CommandSender sender, int players, int durationSecondsOverride) {
    if (isRunning()) {
      CommandFeedback.send(sender, tr(sender, "message.debug_load_running"));
      return;
    }
    this.owner = sender;
    this.ownerId = sender instanceof Player p ? p.getUniqueId() : null;
    this.simulatedPlayers = players;
    readConfig(durationSecondsOverride);
    this.startTick = Bukkit.getCurrentTick();
    this.lastTickNs = 0L;
    this.sampleIndex = 0;
    this.measurementStarted = false;
    this.tickMs = new double[Math.max(1, durationTicks - warmupTicks)];
    this.msptSamples = new double[tickMs.length];
    this.jitterRandom = new Random(System.nanoTime());
    PerfStats.resetAndEnable();
    prepareChunks(sender);
    cleanupTaggedBenchmarkEntitiesAll();
    prepareWorldWorkload();
    prepareDbPositions(simulatedPlayers);
    prepareDisplaySamples();
    prepareGuardSimulation();
    String ownerLanguage = languageFor(sender);
    String summary = summaryLine(ownerLanguage);
    CommandFeedback.send(sender, summary);
    sendToConsoleIfNeeded(summaryLine(configuredLanguage()));
    String started = trLanguage(ownerLanguage, "message.debug_load_started", simulatedPlayers);
    CommandFeedback.send(sender, started);
    sendToConsoleIfNeeded(
        trLanguage(configuredLanguage(), "message.debug_load_started", simulatedPlayers));
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
  }

  public void stop(boolean notify) {
    if (!isRunning()) return;
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    if (notify && owner != null) {
      String stopped = trLanguage(ownerLanguage(), "message.debug_load_stopped");
      CommandFeedback.send(owner, stopped);
    }
    if (notify) {
      sendToConsoleIfNeeded(trLanguage(configuredLanguage(), "message.debug_load_stopped"));
    }
    cleanupDbPositions();
    cleanupWorldWorkload();
    cleanupGuardSimulation();
    cleanupDisplays();
    cleanupTaggedBenchmarkEntitiesAll();
    cleanupChunks();
    PerfStats.disable();
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
    PerfStats.tick(Bukkit.getCurrentTick());
    long elapsed = Bukkit.getCurrentTick() - startTick;
    long now = System.nanoTime();
    if (lastTickNs != 0L) {
      if (measurementStarted && sampleIndex < tickMs.length) {
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
    simulateGuardChurn();
    simulateWorldWorkload(elapsed);

    if (!measurementStarted && readyToStartMeasurement(elapsed)) {
      startMeasurement();
      return;
    }

    if (measurementStarted && sampleIndex % Math.max(1, progressIntervalTicks) == 0) {
      sendProgress(sampleIndex);
    }
    if (elapsed % Math.max(1, monitorIntervalTicks) == 0L) {
      simulateMonitorUpdates();
    }
    if (measurementStarted && stopTpsThreshold > 0 && currentTps() < stopTpsThreshold) {
      finishForced("message.debug_load_grade_awful");
      return;
    }
    if (measurementStarted && sampleIndex >= tickMs.length) {
      finish();
    }
  }

  private boolean readyToStartMeasurement(long elapsedTicks) {
    if (elapsedTicks < warmupTicks) {
      return false;
    }
    if (worldWorkload == null || worldWorkload.readyForMeasurement(elapsedTicks)) {
      return true;
    }
    int fallbackTicks = Math.max(20 * 30, durationTicks);
    return elapsedTicks >= warmupTicks + fallbackTicks;
  }

  private void startMeasurement() {
    measurementStarted = true;
    sampleIndex = 0;
    Arrays.fill(tickMs, 0.0);
    Arrays.fill(msptSamples, 0.0);
    PerfStats.resetAndEnable();
    lastTickNs = System.nanoTime();
    String started = trLanguage(ownerLanguage(), "message.debug_load_measurement_started");
    if (owner != null) {
      CommandFeedback.send(owner, started);
    }
    sendToConsoleIfNeeded(
        trLanguage(configuredLanguage(), "message.debug_load_measurement_started"));
  }

  private void finish() {
    finishForced(null);
  }

  private void finishForced(String forcedGradeKey) {
    LoadTestVerdictCalculator.Result result = verdictResult(forcedGradeKey);
    sendBenchmarkResultLine(language -> formatVerdictComponent(result, language));
    String sustainableGradeKey = sustainableMsptGrade(result.msptAvg(), result.msptP95());
    sendBenchmarkResultLine(
        language -> sustainableMsptComponent(result, sustainableGradeKey, language));
    if (rareStallWarning(result) != null) {
      sendBenchmarkResultLine(language -> rareStallWarningComponent(result, language));
    }
    if (profileHintsData() != null) {
      sendBenchmarkResultLine(this::profileHintsComponent);
    }
    if (PerfStats.snapshot().hasSamples()) {
      sendBenchmarkResultLine(this::measuredProfileComponent);
    }
    sendBenchmarkResultLine(this::metadataComponent);
    boolean hasRecentRuns = rememberRun(result);
    if (hasRecentRuns) {
      sendBenchmarkResultLine(this::formatRecentRunsComponent);
    }
    stop(false);
  }

  private String sustainableMsptLine(
      LoadTestVerdictCalculator.Result result, String gradeKey, String language) {
    return trLanguage(
        language,
        "message.debug_load_mspt_verdict",
        trLanguage(language, gradeKey),
        ONE_DECIMAL.format(result.msptAvg()),
        ONE_DECIMAL.format(result.msptP95()),
        ONE_DECIMAL.format(result.msptP99()));
  }

  private String sustainableMsptGrade(double avg, double p95) {
    if (avg >= 70.0 || p95 >= 80.0) return "message.debug_load_grade_awful";
    if (avg >= 60.0 || p95 >= 70.0) return "message.debug_load_grade_bad";
    if (avg > 50.0 || p95 > 60.0) return "message.debug_load_grade_poor";
    if (avg <= 35.0 && p95 <= 45.0) return "message.debug_load_grade_good";
    return "message.debug_load_grade_warn";
  }

  private NamedTextColor gradeColor(String gradeKey) {
    return switch (gradeKey) {
      case "message.debug_load_grade_good" -> NamedTextColor.GREEN;
      case "message.debug_load_grade_warn" -> NamedTextColor.YELLOW;
      case "message.debug_load_grade_poor" -> NamedTextColor.GOLD;
      case "message.debug_load_grade_bad" -> NamedTextColor.RED;
      case "message.debug_load_grade_awful" -> NamedTextColor.RED;
      default -> NamedTextColor.GRAY;
    };
  }

  private void sendBenchmarkResultLine(BenchmarkComponentRenderer renderer) {
    if (renderer == null) {
      return;
    }
    if (owner != null) {
      Component component = renderer.render(ownerLanguage());
      if (component != null) {
        CommandFeedback.send(owner, component);
      }
    }
    sendToConsoleIfNeeded(renderer.render(configuredLanguage()));
  }

  private Component formatVerdictComponent(
      LoadTestVerdictCalculator.Result result, String language) {
    HighlightedLine line = new HighlightedLine(formatVerdict(result, language));
    line.highlight(trLanguage(language, result.gradeKey()), gradeColor(result.gradeKey()));
    line.highlight(TWO_DECIMALS.format(result.tpsMin()), tpsColor(result.tpsMin()));
    line.highlight(TWO_DECIMALS.format(result.tpsMax()), tpsColor(result.tpsMax()));
    line.highlight(TWO_DECIMALS.format(result.tpsAvg()), tpsColor(result.tpsAvg()));
    line.highlight(TWO_DECIMALS.format(result.tpsP1()), tpsColor(result.tpsP1()));
    line.highlight(ONE_DECIMAL.format(result.msptMin()), msptColor(result.msptMin()));
    line.highlight(ONE_DECIMAL.format(result.msptMax()), msptColor(result.msptMax()));
    line.highlight(ONE_DECIMAL.format(result.msptAvg()), msptColor(result.msptAvg()));
    line.highlight(ONE_DECIMAL.format(result.msptP95()), msptColor(result.msptP95()));
    line.highlight(ONE_DECIMAL.format(result.msptP99()), msptColor(result.msptP99()));
    return line.finish();
  }

  private Component sustainableMsptComponent(
      LoadTestVerdictCalculator.Result result, String gradeKey, String language) {
    HighlightedLine line = new HighlightedLine(sustainableMsptLine(result, gradeKey, language));
    line.highlight(trLanguage(language, gradeKey), gradeColor(gradeKey));
    line.highlight(ONE_DECIMAL.format(result.msptAvg()), msptColor(result.msptAvg()));
    line.highlight(ONE_DECIMAL.format(result.msptP95()), msptColor(result.msptP95()));
    line.highlight(ONE_DECIMAL.format(result.msptP99()), msptColor(result.msptP99()));
    return line.finish();
  }

  private Component rareStallWarningComponent(
      LoadTestVerdictCalculator.Result result, String language) {
    String message = rareStallWarning(result, language);
    if (message == null || message.isBlank()) {
      return null;
    }
    HighlightedLine line = new HighlightedLine(message);
    line.highlight(Integer.toString(result.severeStalls()), NamedTextColor.RED);
    line.highlight(TWO_DECIMALS.format(result.tpsMin()), tpsColor(result.tpsMin()));
    return line.finish();
  }

  private Component profileHintsComponent(String language) {
    ProfileHints hints = profileHintsData();
    if (hints == null) {
      return null;
    }
    String message =
        trLanguage(
            language,
            "message.debug_load_hints",
            hints.dominant(),
            hints.cpuPct(),
            hints.wirePct(),
            hints.monitorPct(),
            hints.dbPct(),
            hints.wirelessPct(),
            hints.guardPct(),
            hints.worldPct(),
            hints.placements());
    HighlightedLine line = new HighlightedLine(message);
    line.highlight(hints.dominant(), NamedTextColor.AQUA);
    line.highlight(hints.cpuPct() + "%", percentageColor(hints.cpuPct()));
    line.highlight(hints.wirePct() + "%", percentageColor(hints.wirePct()));
    line.highlight(hints.monitorPct() + "%", percentageColor(hints.monitorPct()));
    line.highlight(hints.dbPct() + "%", percentageColor(hints.dbPct()));
    line.highlight(hints.wirelessPct() + "%", percentageColor(hints.wirelessPct()));
    line.highlight(hints.guardPct() + "%", percentageColor(hints.guardPct()));
    line.highlight(hints.worldPct() + "%", percentageColor(hints.worldPct()));
    line.highlight(messageNumber(hints.placements()), placementsColor(hints.placements()));
    return line.finish();
  }

  private Component measuredProfileComponent(String language) {
    PerfStats.Snapshot snapshot = PerfStats.snapshot();
    if (!snapshot.hasSamples()) {
      return null;
    }
    List<String> parts = new ArrayList<>();
    HighlightedLineTokens tokens = new HighlightedLineTokens();
    for (PerfStats.MetricStats metric : snapshot.metrics()) {
      if (metric.calls() <= 0L) continue;
      int pct =
          snapshot.totalNanos() <= 0L
              ? 0
              : (int) Math.round(metric.totalNanos() * 100.0 / snapshot.totalNanos());
      String p95 = ONE_DECIMAL.format(metric.p95Micros());
      String p99 = ONE_DECIMAL.format(metric.p99Micros());
      String tickP95 = ONE_DECIMAL.format(metric.p95TickMicros());
      String tickP99 = ONE_DECIMAL.format(metric.p99TickMicros());
      parts.add(
          metric.label()
              + " "
              + pct
              + "%/"
              + metric.calls()
              + " calls p95 "
              + p95
              + "us p99 "
              + p99
              + "us tick-p95/p99 "
              + tickP95
              + "/"
              + tickP99
              + "us");
      tokens.add(metric.label(), subsystemColor(metric.label()));
      tokens.add(pct + "%", percentageColor(pct));
      tokens.add(metric.calls() + " calls", neutralValueColor(parts.size()));
      tokens.add(p95 + "us", latencyMicrosColor(metric.p95Micros()));
      tokens.add(p99 + "us", latencyMicrosColor(metric.p99Micros()));
      tokens.add(tickP95 + "/" + tickP99 + "us", latencyMicrosColor(metric.p99TickMicros()));
      if (parts.size() >= 5) {
        break;
      }
    }
    appendMeasuredQueueStats(snapshot, parts, tokens);
    if (parts.isEmpty()) {
      return null;
    }
    HighlightedLine line =
        new HighlightedLine(
            trLanguage(language, "message.debug_load_measured_profile", String.join(", ", parts)));
    tokens.apply(line);
    return line.finish();
  }

  private void appendMeasuredQueueStats(
      PerfStats.Snapshot snapshot, List<String> parts, HighlightedLineTokens tokens) {
    List<MeasuredQueueStat> queues = new ArrayList<>();
    long storageQueue = snapshot.gauges().getOrDefault("storage-db.queueDepth", -1L);
    long displayQueue = snapshot.gauges().getOrDefault("display.queueDepth", -1L);
    long monitorQueue = snapshot.gauges().getOrDefault("monitor.queueDepth", -1L);
    long busDue = snapshot.gauges().getOrDefault("bus.dueDepth", -1L);
    long displayOverruns = snapshot.counters().getOrDefault("display.budgetOverrun", 0L);
    long monitorOverruns = snapshot.counters().getOrDefault("monitor.budgetOverrun", 0L);
    long busOverruns = snapshot.counters().getOrDefault("bus.budgetOverrun", 0L);
    if (storageQueue >= 0L) queues.add(new MeasuredQueueStat("dbQ", storageQueue, false));
    if (displayQueue >= 0L) queues.add(new MeasuredQueueStat("displayQ", displayQueue, false));
    if (monitorQueue >= 0L) queues.add(new MeasuredQueueStat("monitorQ", monitorQueue, false));
    if (busDue >= 0L) queues.add(new MeasuredQueueStat("busDue", busDue, false));
    if (displayOverruns > 0L) {
      queues.add(new MeasuredQueueStat("displayOverruns", displayOverruns, true));
    }
    if (monitorOverruns > 0L) {
      queues.add(new MeasuredQueueStat("monitorOverruns", monitorOverruns, true));
    }
    if (busOverruns > 0L) queues.add(new MeasuredQueueStat("busOverruns", busOverruns, true));
    if (queues.isEmpty()) {
      return;
    }
    List<String> rendered = new ArrayList<>(queues.size());
    for (MeasuredQueueStat queue : queues) {
      String text = queue.label() + " " + queue.value();
      rendered.add(text);
      tokens.add(text, queueColor(queue.value(), queue.overrun()));
    }
    parts.add(String.join(" ", rendered));
  }

  private Component metadataComponent(String language) {
    String server = Bukkit.getName() + " " + Bukkit.getMinecraftVersion();
    String java = System.getProperty("java.version", "unknown");
    String plugins =
        Arrays.stream(Bukkit.getPluginManager().getPlugins())
            .map(plugin -> plugin.getName() + " " + plugin.getPluginMeta().getVersion())
            .limit(12)
            .reduce((left, right) -> left + ", " + right)
            .orElse("-");
    HighlightedLine line =
        new HighlightedLine(
            trLanguage(
                language,
                "message.debug_load_metadata",
                server,
                plugin.getPluginMeta().getVersion(),
                java,
                plugins));
    line.highlight(server, NamedTextColor.AQUA);
    line.highlight(plugin.getPluginMeta().getVersion(), NamedTextColor.GREEN);
    line.highlight(java, NamedTextColor.YELLOW);
    String[] pluginNames = plugins.split(", ");
    for (int i = 0; i < pluginNames.length; i++) {
      line.highlight(pluginNames[i], neutralValueColor(i));
    }
    return line.finish();
  }

  private boolean rememberRun(LoadTestVerdictCalculator.Result result) {
    synchronized (RECENT_RUNS) {
      RECENT_RUNS.addLast(
          new RunSummary(result.gradeKey(), result.tpsAvg(), result.msptAvg(), result.msptP95()));
      while (RECENT_RUNS.size() > RECENT_RUN_LIMIT) {
        RECENT_RUNS.removeFirst();
      }
      return RECENT_RUNS.size() >= 2;
    }
  }

  private Component formatRecentRunsComponent(String language) {
    synchronized (RECENT_RUNS) {
      if (RECENT_RUNS.size() < 2) {
        return null;
      }
      double tpsAvg = 0.0;
      double msptAvg = 0.0;
      double msptP95 = 0.0;
      for (RunSummary run : RECENT_RUNS) {
        tpsAvg += run.tpsAvg();
        msptAvg += run.msptAvg();
        msptP95 += run.msptP95();
      }
      int count = RECENT_RUNS.size();
      double avgTps = tpsAvg / count;
      double avgMspt = msptAvg / count;
      double avgMsptP95 = msptP95 / count;
      HighlightedLine line =
          new HighlightedLine(
              trLanguage(
                  language,
                  "message.debug_load_recent_runs",
                  count,
                  TWO_DECIMALS.format(avgTps),
                  ONE_DECIMAL.format(avgMspt),
                  ONE_DECIMAL.format(avgMsptP95)));
      line.highlight(Integer.toString(count), NamedTextColor.AQUA);
      line.highlight(TWO_DECIMALS.format(avgTps), tpsColor(avgTps));
      line.highlight(ONE_DECIMAL.format(avgMspt), msptColor(avgMspt));
      line.highlight(ONE_DECIMAL.format(avgMsptP95), msptColor(avgMsptP95));
      return line.finish();
    }
  }

  private NamedTextColor neutralValueColor(int index) {
    return index % 2 == 0 ? NamedTextColor.WHITE : NamedTextColor.GRAY;
  }

  private NamedTextColor tpsColor(double tps) {
    if (tps >= 19.5) return NamedTextColor.GREEN;
    if (tps >= 18.0) return NamedTextColor.YELLOW;
    if (tps >= 15.0) return NamedTextColor.GOLD;
    return NamedTextColor.RED;
  }

  private NamedTextColor msptColor(double mspt) {
    if (mspt <= 35.0) return NamedTextColor.GREEN;
    if (mspt <= 45.0) return NamedTextColor.YELLOW;
    if (mspt <= 60.0) return NamedTextColor.GOLD;
    return NamedTextColor.RED;
  }

  private NamedTextColor percentageColor(int percentage) {
    if (percentage >= 50) return NamedTextColor.GOLD;
    if (percentage >= 20) return NamedTextColor.YELLOW;
    if (percentage > 0) return NamedTextColor.WHITE;
    return NamedTextColor.GRAY;
  }

  private NamedTextColor subsystemColor(String label) {
    if (label == null) return NamedTextColor.WHITE;
    if (label.startsWith("bus")) return NamedTextColor.GOLD;
    if (label.startsWith("network")) return NamedTextColor.AQUA;
    if (label.startsWith("display") || label.startsWith("monitor")) {
      return NamedTextColor.LIGHT_PURPLE;
    }
    if (label.startsWith("storage-db") || label.startsWith("db")) return NamedTextColor.YELLOW;
    if (label.startsWith("placement-guard")) return NamedTextColor.GREEN;
    return NamedTextColor.WHITE;
  }

  private NamedTextColor latencyMicrosColor(double micros) {
    if (micros >= 10_000.0) return NamedTextColor.RED;
    if (micros >= 5_000.0) return NamedTextColor.GOLD;
    if (micros >= 1_000.0) return NamedTextColor.YELLOW;
    return NamedTextColor.GREEN;
  }

  private NamedTextColor queueColor(long value, boolean overrun) {
    if (overrun) return NamedTextColor.RED;
    if (value <= 0L) return NamedTextColor.GRAY;
    if (value >= 100L) return NamedTextColor.GOLD;
    return NamedTextColor.YELLOW;
  }

  private NamedTextColor placementsColor(long placements) {
    return placements > 0L ? NamedTextColor.AQUA : NamedTextColor.GRAY;
  }

  private String messageNumber(long value) {
    return java.text.MessageFormat.format("{0}", value);
  }

  private void sendProgress(int measuredTicks) {
    int totalMeasuredTicks = Math.max(1, tickMs.length);
    int remainingTicks = Math.max(0, totalMeasuredTicks - measuredTicks);
    int remainingSeconds = remainingTicks / 20;
    double progress = Math.min(1.0, measuredTicks / (double) totalMeasuredTicks);
    int percent = (int) Math.round(progress * 100.0);
    double tps = currentTps();
    double mspt = currentMspt();
    String message =
        trLanguage(
            ownerLanguage(),
            "message.debug_load_progress",
            percent,
            remainingSeconds,
            ONE_DECIMAL.format(tps),
            ONE_DECIMAL.format(mspt));
    if (owner != null) {
      CommandFeedback.send(owner, message);
      if (ownerId != null) {
        Player player = Bukkit.getPlayer(ownerId);
        if (player != null && player.isOnline()) {
          bossBarManager.showProgress(player, message, progress, progressColor(tps));
        }
      }
    }
    sendToConsoleIfNeeded(
        trLanguage(
            configuredLanguage(),
            "message.debug_load_progress",
            percent,
            remainingSeconds,
            ONE_DECIMAL.format(tps),
            ONE_DECIMAL.format(mspt)));
  }

  private double currentTps() {
    double[] tps = Bukkit.getTPS();
    return tps.length > 0 ? Math.min(20.0, tps[0]) : 20.0;
  }

  private double currentMspt() {
    return Bukkit.getAverageTickTime();
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
    UUID world =
        UUID.nameUUIDFromBytes(
            "exort-stress-world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

  private void prepareWorldWorkload() {
    cleanupWorldWorkload();
    if (runtimeDependencies == null || benchmarkWorld == null || loadedChunks == null) {
      return;
    }
    List<LoadTestWorldWorkload.LoadedChunk> chunks = new ArrayList<>(loadedChunks.size());
    for (ChunkTicket ticket : loadedChunks) {
      chunks.add(new LoadTestWorldWorkload.LoadedChunk(ticket.world(), ticket.x(), ticket.z()));
    }
    worldWorkload =
        LoadTestWorldWorkload.create(
                plugin, database, runtimeDependencies, benchmarkWorld, chunks, simulatedPlayers)
            .orElse(null);
  }

  private void simulateWorldWorkload(long elapsedTicks) {
    if (worldWorkload == null) {
      return;
    }
    try {
      worldWorkload.tick(elapsedTicks);
    } catch (RuntimeException | LinkageError e) {
      plugin.getLogger().log(Level.WARNING, "Benchmark world workload failed and was disabled.", e);
      cleanupWorldWorkload();
    }
  }

  private void cleanupWorldWorkload() {
    if (worldWorkload == null) {
      return;
    }
    try {
      worldWorkload.cleanup();
    } catch (RuntimeException | LinkageError e) {
      plugin.getLogger().log(Level.WARNING, "Benchmark world workload cleanup failed.", e);
    } finally {
      worldWorkload = null;
    }
  }

  private LoadTestVerdictCalculator.Result verdictResult(String forcedGradeKey) {
    int n = Math.min(sampleIndex, tickMs.length);
    return LoadTestVerdictCalculator.calculate(tickMs, msptSamples, n, forcedGradeKey);
  }

  private String formatVerdict(LoadTestVerdictCalculator.Result result, String language) {
    String grade = trLanguage(language, result.gradeKey());
    return trLanguage(
        language,
        "message.debug_load_verdict",
        grade,
        TWO_DECIMALS.format(result.tpsMin()),
        TWO_DECIMALS.format(result.tpsMax()),
        TWO_DECIMALS.format(result.tpsAvg()),
        TWO_DECIMALS.format(result.tpsP1()),
        ONE_DECIMAL.format(result.msptMin()),
        ONE_DECIMAL.format(result.msptMax()),
        ONE_DECIMAL.format(result.msptAvg()),
        ONE_DECIMAL.format(result.msptP95()),
        ONE_DECIMAL.format(result.msptP99()));
  }

  private String rareStallWarning(LoadTestVerdictCalculator.Result result) {
    return rareStallWarning(result, ownerLanguage());
  }

  private String rareStallWarning(LoadTestVerdictCalculator.Result result, String language) {
    if (result.severeStalls() <= 0) return null;
    if ("message.debug_load_grade_awful".equals(result.gradeKey())) return null;
    return trLanguage(
        language,
        "message.debug_load_rare_stalls",
        result.severeStalls(),
        TWO_DECIMALS.format(result.tpsMin()));
  }

  private int estimateOperationsPerTick() {
    int chunks = activeChunkCount();
    double activeRatio = Math.min(1.0, Math.max(0.0, busActiveRatio));
    double perBus =
        activeRatio / Math.max(1.0, activeIntervalTicks)
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

  private ProfileHints profileHintsData() {
    int ops = estimateOperationsPerTick();
    int dbOps = estimateDbOpsPerTick(ops);
    int chunks = activeChunkCount();
    int wireLen = Math.max(1, Math.min(wireLimit, wireHardCap > 0 ? wireHardCap : wireLimit));
    long cpuCost = (long) ops * (long) itemsPerOperation * (long) cpuIterationsPerOp;
    long wireCost = 0L;
    if (simulateWireScan) {
      int storages = Math.max(0, storagePerChunk * chunks);
      int bfsNetworks = Math.max(0, (int) Math.ceil(storages * wireScanMissRatio));
      double busesPerStorage =
          storagePerChunk > 0 ? busCountPerChunk / (double) storagePerChunk : 0.0;
      double monitorsPerStorage =
          storagePerChunk > 0 ? monitorCountPerChunk / (double) storagePerChunk : 0.0;
      double nodesPerStorage = 1.0 + (6.0 * wireLen) + busesPerStorage + monitorsPerStorage;
      double visited = nodesPerStorage * Math.max(0.0, Math.min(1.0, wireScanCoverage));
      wireCost = (long) Math.ceil(bfsNetworks * visited * Math.max(1, wireIterationsPerBlock));
    }
    int updates = 0;
    if (simulateDisplays && monitorIterationsPerUpdate > 0) {
      int storageChanges = Math.max(0, Math.min(storagePerChunk, lastOpsPerChunk));
      int monitorsPerStorage =
          storagePerChunk > 0
              ? Math.max(1, (int) Math.ceil(monitorCountPerChunk / (double) storagePerChunk))
              : monitorCountPerChunk;
      int updatesPerChunk = Math.min(monitorCountPerChunk, storageChanges * monitorsPerStorage);
      updates = updatesPerChunk * chunks;
    }
    long monitorCost =
        simulateDisplays ? (long) updates * (long) Math.max(1, monitorIterationsPerUpdate) : 0L;
    long dbCost = dbOps > 0 ? (long) dbOps * (long) Math.max(1, cpuIterationsPerOp / 2) : 0L;
    long wirelessCost =
        wirelessEnabled
            ? (long) Math.max(1, simulatedPlayers)
                * (long) Math.max(1, wirelessRangeBlocks)
                * (long) Math.max(1, cpuIterationsPerOp / 12)
            : 0L;
    long guardCost = (long) Math.max(0, guardChurnPerTick) * (long) Math.max(1, cpuIterationsPerOp);
    long worldCost = worldWorkload == null ? 0L : worldWorkload.estimatedCost(cpuIterationsPerOp);
    long total = cpuCost + wireCost + monitorCost + dbCost + wirelessCost + guardCost + worldCost;
    if (total <= 0) return null;
    int cpuPct = (int) Math.round(cpuCost * 100.0 / total);
    int wirePct = (int) Math.round(wireCost * 100.0 / total);
    int monitorPct = (int) Math.round(monitorCost * 100.0 / total);
    int dbPct = (int) Math.round(dbCost * 100.0 / total);
    int wirelessPct = (int) Math.round(wirelessCost * 100.0 / total);
    int guardPct = (int) Math.round(guardCost * 100.0 / total);
    int worldPct = (int) Math.round(worldCost * 100.0 / total);
    String dominant = "CPU";
    long max = cpuCost;
    if (wireCost > max) {
      max = wireCost;
      dominant = "WIRE";
    }
    if (monitorCost > max) {
      max = monitorCost;
      dominant = "DISPLAYS";
    }
    if (dbCost > max) {
      max = dbCost;
      dominant = "DB";
    }
    if (wirelessCost > max) {
      max = wirelessCost;
      dominant = "WIRELESS";
    }
    if (guardCost > max) {
      max = guardCost;
      dominant = "GUARDS";
    }
    if (worldCost > max) {
      dominant = "WORLD";
    }
    return new ProfileHints(
        dominant,
        cpuPct,
        wirePct,
        monitorPct,
        dbPct,
        wirelessPct,
        guardPct,
        worldPct,
        worldWorkload == null ? 0L : worldWorkload.totalPlacements());
  }

  private String summaryLine(String language) {
    int chunks = activeChunkCount();
    int ops = estimateOperationsPerTick();
    int dbOps = estimateDbOpsPerTick(ops);
    int worldLanes = worldWorkload == null ? 0 : worldWorkload.laneCount();
    int worldBudget = worldWorkload == null ? 0 : worldWorkload.operationBudget();
    int worldBlocks = worldWorkload == null ? 0 : worldWorkload.plannedBlockCount();
    int waterWireLength = worldWorkload == null ? 0 : worldWorkload.waterWireLength();
    int waterSources = worldWorkload == null ? 0 : worldWorkload.waterSourceCount();
    int waterExtraChunks = worldWorkload == null ? 0 : worldWorkload.extraChunkTicketCount();
    return trLanguage(
        language,
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
        Math.max(1, tickMs == null ? (durationTicks - warmupTicks) / 20 : tickMs.length / 20),
        Math.max(0, warmupTicks / 20),
        ONE_DECIMAL.format(Math.max(0.0, opsJitterPercent * 100.0)),
        guardPlayers,
        targetGuardEntities,
        requestedGuardEntities(),
        guardChurnPerTick,
        worldLanes,
        worldBudget,
        worldBlocks,
        waterWireLength,
        waterSources,
        waterExtraChunks);
  }

  private String ownerLanguage() {
    return languageFor(owner);
  }

  private String languageFor(CommandSender sender) {
    if (sender instanceof Player player) {
      return lang.pluginTextLanguage(player);
    }
    return configuredLanguage();
  }

  private String configuredLanguage() {
    return lang.configuredLanguage();
  }

  private String trLanguage(String language, String key, Object... params) {
    return lang.trLanguage(language, key, params);
  }

  private String tr(CommandSender sender, String key, Object... params) {
    return lang.tr(sender, key, params);
  }

  @FunctionalInterface
  private interface BenchmarkComponentRenderer {
    Component render(String language);
  }

  private long requestedGuardEntities() {
    return (long) Math.max(0, guardPlayers) * (long) DEFAULT_GUARDS_PER_PLAYER;
  }

  private void simulateWireScan(int ops) {
    if (!simulateWireScan) return;
    int chunks = activeChunkCount();
    int wireLen = Math.max(1, Math.min(wireLimit, wireHardCap > 0 ? wireHardCap : wireLimit));
    int storages = Math.max(0, storagePerChunk * chunks);
    if (storages <= 0) return;
    int bfsNetworks = Math.max(0, (int) Math.ceil(storages * wireScanMissRatio));
    if (bfsNetworks <= 0) return;
    double busesPerStorage =
        storagePerChunk > 0 ? busCountPerChunk / (double) storagePerChunk : 0.0;
    double monitorsPerStorage =
        storagePerChunk > 0 ? monitorCountPerChunk / (double) storagePerChunk : 0.0;
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
    int monitorsPerStorage =
        storagePerChunk > 0
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
    int iterations = players * iterationsPerPlayer * Math.max(1, wirelessRangeBlocks);
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

  private boolean prepareDisplaySamples() {
    cleanupDisplays();
    if (!simulateDisplays) return true;
    int chunks = Math.max(1, simulatedPlayers * chunksPerPlayer);
    int target = monitorCountPerChunk * chunks;
    int count = Math.max(0, Math.min(displaySampleLimit, target));
    if (count <= 0) return true;
    displayEntities = new ArrayList<>(count * 2);
    for (int i = 0; i < count; i++) {
      Location base = benchmarkLocation(i, 0.25);
      if (base == null) break;
      ItemDisplay item =
          base.getWorld()
              .spawn(
                  base,
                  ItemDisplay.class,
                  display -> {
                    display.setItemStack(new ItemStack(Material.STONE));
                    display.setBillboard(Display.Billboard.FIXED);
                    display.setBrightness(new Display.Brightness(15, 15));
                    display.addScoreboardTag(BENCHMARK_TAG);
                  });
      TextDisplay text =
          base.getWorld()
              .spawn(
                  base.clone().add(0, 0.1, 0),
                  TextDisplay.class,
                  display -> {
                    display.text(net.kyori.adventure.text.Component.text("0"));
                    display.setBillboard(Display.Billboard.FIXED);
                    display.setBrightness(new Display.Brightness(15, 15));
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
      UUID id = displayEntities.get(i);
      Entity entity = Bukkit.getEntity(id);
      if (entity instanceof ItemDisplay item) {
        item.setItemStack(new ItemStack(Material.IRON_INGOT));
      } else if (entity instanceof TextDisplay text) {
        text.text(net.kyori.adventure.text.Component.text(Integer.toString(updates)));
      }
    }
  }

  private void cleanupDisplays() {
    if (displayEntities == null) return;
    for (UUID id : displayEntities) {
      Entity entity = Bukkit.getEntity(id);
      if (entity != null) {
        entity.remove();
      }
    }
    displayEntities.clear();
    displayEntities = null;
  }

  private void prepareGuardSimulation() {
    cleanupGuardSimulation();
    guardPlayers = Math.max(0, simulatedPlayers);
    int maxPlayersByEntityCap = Math.max(1, MAX_GUARD_ARMOR_STANDS / DEFAULT_GUARDS_PER_PLAYER);
    effectiveGuardPlayers = Math.min(guardPlayers, maxPlayersByEntityCap);
    targetGuardEntities = effectiveGuardPlayers * DEFAULT_GUARDS_PER_PLAYER;
    guardChurnPerTick = targetGuardEntities;
    if (effectiveGuardPlayers <= 0 || benchmarkWorld == null) {
      effectiveGuardPlayers = 0;
      targetGuardEntities = 0;
      guardChurnPerTick = 0;
      return;
    }
    guardStates = new ArrayList<>(effectiveGuardPlayers);
    for (int i = 0; i < effectiveGuardPlayers; i++) {
      int initialPosition = i % DEFAULT_GUARD_ROW_LENGTH;
      int initialDirection = initialPosition == DEFAULT_GUARD_ROW_LENGTH - 1 ? -1 : 1;
      GuardSimulationState state = new GuardSimulationState(i, initialPosition, initialDirection);
      spawnGuardPair(state);
      guardStates.add(state);
    }
  }

  private void simulateGuardChurn() {
    if (guardStates == null || guardStates.isEmpty()) return;
    for (GuardSimulationState state : guardStates) {
      removeGuardPair(state);
      advanceGuardState(state);
      spawnGuardPair(state);
    }
  }

  private void advanceGuardState(GuardSimulationState state) {
    int next = state.rowPosition + state.direction;
    if (next < 0 || next >= DEFAULT_GUARD_ROW_LENGTH) {
      state.direction = -state.direction;
      next = state.rowPosition + state.direction;
    }
    state.rowPosition = Math.max(0, Math.min(DEFAULT_GUARD_ROW_LENGTH - 1, next));
  }

  private void spawnGuardPair(GuardSimulationState state) {
    normalizeGuardDirection(state);
    state.currentGuard =
        spawnBenchmarkGuard(guardLocation(state.playerIndex, state.rowPosition, 0));
    int predictedPosition = state.rowPosition + state.direction;
    state.predictedGuard =
        spawnBenchmarkGuard(guardLocation(state.playerIndex, predictedPosition, 1));
  }

  private void normalizeGuardDirection(GuardSimulationState state) {
    int predictedPosition = state.rowPosition + state.direction;
    if (predictedPosition < 0 || predictedPosition >= DEFAULT_GUARD_ROW_LENGTH) {
      state.direction = -state.direction;
    }
  }

  private UUID spawnBenchmarkGuard(Location location) {
    if (location == null || location.getWorld() == null) return null;
    ArmorStand guard =
        location
            .getWorld()
            .spawn(
                location,
                ArmorStand.class,
                entity -> {
                  entity.setPersistent(false);
                  entity.setInvulnerable(true);
                  entity.setSilent(true);
                  entity.setVisible(false);
                  entity.setGravity(false);
                  entity.setNoPhysics(true);
                  entity.setVisibleByDefault(false);
                  entity.setSmall(true);
                  entity.setMarker(false);
                  AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
                  if (scale != null) {
                    scale.setBaseValue(GUARD_ARMOR_STAND_SCALE);
                  }
                  entity.setBasePlate(false);
                  entity.setArms(false);
                  entity.setAI(false);
                  entity.setCanTick(false);
                  entity.setCollidable(false);
                  entity.setCanPickupItems(false);
                  entity.setRemoveWhenFarAway(false);
                  entity.addScoreboardTag(BENCHMARK_TAG);
                  entity.addScoreboardTag(BENCHMARK_GUARD_TAG);
                });
    return guard.getUniqueId();
  }

  private Location guardLocation(int playerIndex, int rowPosition, int guardSlot) {
    if (benchmarkWorld == null) return null;
    ChunkTicket ticket = benchmarkTicket(playerIndex);
    int chunkCount = loadedChunks == null || loadedChunks.isEmpty() ? 1 : loadedChunks.size();
    int lane = playerIndex / chunkCount;
    double x =
        ticket.x() * 16.0 + 2.5 + Math.max(0, Math.min(DEFAULT_GUARD_ROW_LENGTH - 1, rowPosition));
    double y = safeBenchmarkY(benchmarkWorld, (lane / 12) * 0.15 + guardSlot * 0.02);
    double z = ticket.z() * 16.0 + 2.5 + (lane % DEFAULT_GUARD_ROW_LENGTH);
    return new Location(benchmarkWorld, x, y, z);
  }

  private Location benchmarkLocation(int index, double yOffset) {
    if (benchmarkWorld == null) return null;
    ChunkTicket ticket = benchmarkTicket(index);
    int chunkCount = loadedChunks == null || loadedChunks.isEmpty() ? 1 : loadedChunks.size();
    int lane = index / chunkCount;
    double x = ticket.x() * 16.0 + 2.0 + ((lane * 3) % 12);
    double y = safeBenchmarkY(benchmarkWorld, yOffset + (lane / 16) * 0.1);
    double z = ticket.z() * 16.0 + 2.0 + (((lane / 4) * 3) % 12);
    return new Location(benchmarkWorld, x, y, z);
  }

  private ChunkTicket benchmarkTicket(int index) {
    if (loadedChunks != null && !loadedChunks.isEmpty()) {
      return loadedChunks.get(Math.floorMod(index, loadedChunks.size()));
    }
    Location spawn = benchmarkWorld.getSpawnLocation();
    return new ChunkTicket(benchmarkWorld.getUID(), spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4);
  }

  private double safeBenchmarkY(World world, double offset) {
    return BenchmarkHeight.entityY(world, offset);
  }

  private void cleanupGuardSimulation() {
    if (guardStates == null) {
      guardPlayers = 0;
      effectiveGuardPlayers = 0;
      targetGuardEntities = 0;
      guardChurnPerTick = 0;
      return;
    }
    for (GuardSimulationState state : guardStates) {
      removeGuardPair(state);
    }
    guardStates.clear();
    guardStates = null;
    guardPlayers = 0;
    effectiveGuardPlayers = 0;
    targetGuardEntities = 0;
    guardChurnPerTick = 0;
  }

  private void removeGuardPair(GuardSimulationState state) {
    removeEntity(state.currentGuard);
    removeEntity(state.predictedGuard);
    state.currentGuard = null;
    state.predictedGuard = null;
  }

  private void removeEntity(UUID entityId) {
    if (entityId == null) return;
    Entity entity = Bukkit.getEntity(entityId);
    if (entity != null) {
      entity.remove();
    }
  }

  private void cleanupTaggedBenchmarkEntities(World world) {
    if (world == null) return;
    for (Entity entity : world.getEntities()) {
      if (entity.getScoreboardTags().contains(BENCHMARK_TAG)) {
        entity.remove();
      }
    }
  }

  private void cleanupTaggedBenchmarkEntitiesAll() {
    for (World world : Bukkit.getWorlds()) {
      cleanupTaggedBenchmarkEntities(world);
    }
  }

  private void readConfig(int durationSecondsOverride) {
    if (simulatedPlayers <= 0) {
      simulatedPlayers = DEFAULT_PLAYERS;
    }
    BusRuntimeConfig busConfig = BusRuntimeConfig.fromConfig(plugin.getConfig());
    activeIntervalTicks = busConfig.activeIntervalTicks();
    idleIntervalTicks = busConfig.idleIntervalTicks();
    itemsPerOperation = busConfig.itemsPerOperation();
    maxOperationsPerTick = busConfig.maxOperationsPerTick();
    maxOperationsPerChunk = busConfig.maxOperationsPerChunk();
    RuntimeNetworkConfig networkConfig = RuntimeNetworkConfig.fromConfig(plugin.getConfig());
    wireLimit = networkConfig.wireLimit();
    wireHardCap = networkConfig.wireHardCap();
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
    StorageRuntimeConfig storageConfig = StorageRuntimeConfig.fromConfig(plugin.getConfig());
    int flushSeconds = Math.max(0, storageConfig.flushIntervalSeconds());
    flushIntervalTicks = flushSeconds > 0 ? flushSeconds * 20 : 0;
    int idleUnloadSeconds = Math.max(0, (int) storageConfig.cacheIdleUnloadSeconds());
    idleUnloadTicks = idleUnloadSeconds > 0 ? idleUnloadSeconds * 20 : 0;
    int idleCheckSeconds = Math.max(0, (int) storageConfig.cacheIdleCheckSeconds());
    idleCheckTicks = idleCheckSeconds > 0 ? idleCheckSeconds * 20 : 0;
    WirelessRuntimeConfig wirelessConfig = WirelessRuntimeConfig.fromConfig(plugin.getConfig());
    wirelessEnabled = wirelessConfig.enabled();
    wirelessRangeBlocks = wirelessConfig.rangeBlocks();
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
    ExortLog.info(message);
  }

  private void sendToConsoleIfNeeded(Component message) {
    if (owner != null && owner == Bukkit.getConsoleSender()) {
      return;
    }
    if (message == null) {
      return;
    }
    Bukkit.getConsoleSender().sendMessage(CommandFeedback.prefixed(message));
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
      storagePerChunk =
          Math.max(1, Math.min(filled, (int) Math.floor(filled / (double) perStorageBlocks)));
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
    benchmarkWorld =
        (sender instanceof Player p)
            ? p.getWorld()
            : Bukkit.getWorlds().stream().findFirst().orElse(null);
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
    List<ChunkPos> plan = planChunkGroups(groups, chunksPerPlayer, centerX, centerZ, step);
    if (plan.size() > count) {
      plan = plan.subList(0, count);
    }
    loadedChunks = new ArrayList<>(plan.size());
    for (ChunkPos pos : plan) {
      Chunk chunk = benchmarkWorld.getChunkAt(pos.x(), pos.z());
      chunk.addPluginChunkTicket(plugin);
      loadedChunks.add(new ChunkTicket(benchmarkWorld.getUID(), pos.x(), pos.z()));
    }
  }

  private void cleanupChunks() {
    if (loadedChunks == null || loadedChunks.isEmpty() || benchmarkWorld == null) return;
    for (ChunkTicket ticket : loadedChunks) {
      if (!benchmarkWorld.getUID().equals(ticket.world())) continue;
      Chunk chunk = benchmarkWorld.getChunkAt(ticket.x(), ticket.z());
      chunk.removePluginChunkTicket(plugin);
    }
    loadedChunks.clear();
    loadedChunks = null;
    benchmarkWorld = null;
  }

  private List<ChunkPos> planChunkGroups(
      int groups, int chunksPerGroup, int centerX, int centerZ, int step) {
    List<ChunkPos> offsets = new ArrayList<>();
    int side = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, chunksPerGroup))));
    for (int dz = 0; dz < side && offsets.size() < chunksPerGroup; dz++) {
      for (int dx = 0; dx < side && offsets.size() < chunksPerGroup; dx++) {
        offsets.add(new ChunkPos(dx, dz));
      }
    }
    List<ChunkPos> bases = spiralPositions(groups, step);
    List<ChunkPos> result = new ArrayList<>(groups * chunksPerGroup);
    for (ChunkPos base : bases) {
      for (ChunkPos offset : offsets) {
        result.add(new ChunkPos(centerX + base.x() + offset.x(), centerZ + base.z() + offset.z()));
      }
    }
    return result;
  }

  private List<ChunkPos> spiralPositions(int count, int step) {
    List<ChunkPos> positions = new ArrayList<>(count);
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

  private static final class HighlightedLine {
    private static final NamedTextColor PLAIN_COLOR = NamedTextColor.GRAY;

    private final String text;
    private Component component = Component.empty();
    private int cursor;

    HighlightedLine(String text) {
      this.text = text == null ? "" : text;
    }

    void highlight(String token, NamedTextColor color) {
      if (token == null || token.isBlank()) {
        return;
      }
      int index = text.indexOf(token, cursor);
      if (index < 0) {
        return;
      }
      appendPlain(index);
      component =
          component.append(
              Component.text(
                  text.substring(index, index + token.length()),
                  color == null ? NamedTextColor.WHITE : color));
      cursor = index + token.length();
    }

    Component finish() {
      appendPlain(text.length());
      return component;
    }

    private void appendPlain(int endExclusive) {
      if (endExclusive <= cursor) {
        return;
      }
      component =
          component.append(Component.text(text.substring(cursor, endExclusive), PLAIN_COLOR));
      cursor = endExclusive;
    }
  }

  private static final class HighlightedLineTokens {
    private final List<HighlightedLineToken> tokens = new ArrayList<>();

    void add(String token, NamedTextColor color) {
      tokens.add(new HighlightedLineToken(token, color));
    }

    void apply(HighlightedLine line) {
      for (HighlightedLineToken token : tokens) {
        line.highlight(token.token(), token.color());
      }
    }
  }

  private static final class GuardSimulationState {
    private final int playerIndex;
    private int rowPosition;
    private int direction;
    private UUID currentGuard;
    private UUID predictedGuard;

    GuardSimulationState(int playerIndex, int rowPosition, int direction) {
      this.playerIndex = playerIndex;
      this.rowPosition = rowPosition;
      this.direction = direction;
    }
  }

  private record ChunkPos(int x, int z) {}

  private record ProfileHints(
      String dominant,
      int cpuPct,
      int wirePct,
      int monitorPct,
      int dbPct,
      int wirelessPct,
      int guardPct,
      int worldPct,
      long placements) {}

  private record HighlightedLineToken(String token, NamedTextColor color) {}

  private record MeasuredQueueStat(String label, long value, boolean overrun) {}

  private record RunSummary(String gradeKey, double tpsAvg, double msptAvg, double msptP95) {}

  private record ChunkTicket(UUID world, int x, int z) {}
}
