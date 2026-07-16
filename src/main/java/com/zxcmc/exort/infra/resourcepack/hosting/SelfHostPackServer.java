package com.zxcmc.exort.infra.resourcepack.hosting;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zxcmc.exort.infra.logging.ExortLog;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;

final class SelfHostPackServer {
  private static final String PACK_PATH = "/exort.zip";
  private static final int MAX_CONCURRENT_REQUESTS = 16;
  private static final PackBodyWriter FILE_BODY_WRITER = (pack, output) -> Files.copy(pack, output);

  private final int maxConcurrentRequests;
  private final PackBodyWriter bodyWriter;
  private ListenerRuntime runtime;

  SelfHostPackServer() {
    this(MAX_CONCURRENT_REQUESTS, FILE_BODY_WRITER);
  }

  SelfHostPackServer(int maxConcurrentRequests, PackBodyWriter bodyWriter) {
    if (maxConcurrentRequests <= 0) {
      throw new IllegalArgumentException("maxConcurrentRequests must be positive");
    }
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.bodyWriter = bodyWriter == null ? FILE_BODY_WRITER : bodyWriter;
  }

  synchronized String start(File packFile, String bindHost, int port, String publicUrl)
      throws IOException {
    stop();
    String host = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost.trim();
    HttpServer server = null;
    ExecutorService executor = null;
    try {
      server = HttpServer.create(new InetSocketAddress(host, Math.max(0, port)), 0);
      executor = Executors.newVirtualThreadPerTaskExecutor();
      ListenerRuntime candidate =
          new ListenerRuntime(server, executor, maxConcurrentRequests, bodyWriter);
      server.createContext(PACK_PATH, exchange -> servePack(candidate, exchange, packFile));
      server.setExecutor(executor);

      InetSocketAddress boundAddress = server.getAddress();
      int actualPort = boundAddress.getPort();
      String resolvedUrl =
          publicUrl != null && !publicUrl.isBlank()
              ? publicUrl.trim()
              : "http://" + inferPublicHost(host) + ":" + actualPort + PACK_PATH;
      candidate.activate(formatAddress(boundAddress), resolvedUrl);
      server.start();
      runtime = candidate;
      return resolvedUrl;
    } catch (IOException | RuntimeException failure) {
      if (server != null) {
        server.stop(0);
      }
      if (executor != null) {
        executor.shutdownNow();
      }
      throw failure;
    }
  }

  synchronized void stop() {
    ListenerRuntime current = runtime;
    runtime = null;
    if (current != null) {
      current.stop();
    }
  }

  synchronized String url() {
    return runtime == null ? null : runtime.publicUrl();
  }

  synchronized Snapshot snapshot() {
    return runtime == null ? Snapshot.stopped(maxConcurrentRequests) : runtime.snapshot();
  }

  private void servePack(ListenerRuntime owner, HttpExchange exchange, File packFile)
      throws IOException {
    owner.serve(exchange, packFile.toPath());
  }

  private String inferPublicHost(String bindHost) {
    String serverIp = Bukkit.getIp();
    if (isUsablePublicHost(serverIp)) {
      return bracketIpv6(serverIp);
    }
    if (isUsablePublicHost(bindHost)) {
      return bracketIpv6(bindHost);
    }
    String interfaceHost = inferInterfaceHost();
    if (interfaceHost != null) {
      return bracketIpv6(interfaceHost);
    }
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      if (!localHost.isAnyLocalAddress() && !localHost.isLoopbackAddress()) {
        return bracketIpv6(localHost.getHostAddress());
      }
    } catch (IOException e) {
      ExortLog.warn("Failed to infer resource-pack public host: " + e.getMessage());
    }
    ExortLog.warn(
        "Resource-pack self-hosting could not infer a non-loopback public host. Set"
            + " resourcePack.selfHost.publicUrl manually if clients cannot download the pack.");
    return "127.0.0.1";
  }

  private String inferInterfaceHost() {
    try {
      var interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
        if (!isCandidateInterface(networkInterface)) {
          continue;
        }
        var addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (address instanceof Inet4Address
              && !address.isAnyLocalAddress()
              && !address.isLoopbackAddress()
              && !address.isLinkLocalAddress()) {
            return address.getHostAddress();
          }
        }
      }
    } catch (SocketException e) {
      ExortLog.warn(
          "Failed to inspect network interfaces for resource-pack self-hosting: " + e.getMessage());
    }
    return null;
  }

  private boolean isCandidateInterface(NetworkInterface networkInterface) throws SocketException {
    if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
      return false;
    }
    String name = networkInterface.getName().toLowerCase(java.util.Locale.ROOT);
    return !name.startsWith("docker")
        && !name.startsWith("br-")
        && !name.startsWith("veth")
        && !name.startsWith("virbr");
  }

  private boolean isUsablePublicHost(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    String normalized = host.trim();
    if ("0.0.0.0".equals(normalized) || "::".equals(normalized) || "*".equals(normalized)) {
      return false;
    }
    try {
      InetAddress address = InetAddress.getByName(normalized);
      return !address.isAnyLocalAddress()
          && !address.isLoopbackAddress()
          && !address.isLinkLocalAddress();
    } catch (IOException ignored) {
      return true;
    }
  }

  private String bracketIpv6(String host) {
    return host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
  }

  private static String formatAddress(InetSocketAddress address) {
    return bracketAddress(address.getHostString()) + ":" + address.getPort();
  }

  private static String bracketAddress(String host) {
    return host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
  }

  @FunctionalInterface
  interface PackBodyWriter {
    void write(Path pack, OutputStream output) throws IOException;
  }

  record Snapshot(
      boolean running,
      String bindAddress,
      String publicUrl,
      int limit,
      int active,
      int peak,
      long accepted,
      long completed,
      long rejected,
      long failed) {
    private static Snapshot stopped(int limit) {
      return new Snapshot(false, null, null, limit, 0, 0, 0L, 0L, 0L, 0L);
    }
  }

  private static final class ListenerRuntime {
    private final HttpServer server;
    private final ExecutorService executor;
    private final Semaphore permits;
    private final int limit;
    private final PackBodyWriter bodyWriter;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile boolean running;
    private volatile String bindAddress;
    private volatile String publicUrl;

    private ListenerRuntime(
        HttpServer server, ExecutorService executor, int limit, PackBodyWriter bodyWriter) {
      this.server = server;
      this.executor = executor;
      this.limit = limit;
      this.permits = new Semaphore(limit);
      this.bodyWriter = bodyWriter;
    }

    private void activate(String bindAddress, String publicUrl) {
      this.bindAddress = bindAddress;
      this.publicUrl = publicUrl;
      running = true;
    }

    private void stop() {
      running = false;
      server.stop(0);
      executor.shutdownNow();
    }

    private void serve(HttpExchange exchange, Path pack) throws IOException {
      if (!permits.tryAcquire()) {
        rejected.incrementAndGet();
        exchange.getResponseHeaders().set("Retry-After", "1");
        exchange.sendResponseHeaders(503, -1);
        exchange.close();
        return;
      }

      accepted.incrementAndGet();
      int currentActive = active.incrementAndGet();
      peak.accumulateAndGet(currentActive, Math::max);
      try {
        serveWithPermit(exchange, pack);
        completed.incrementAndGet();
      } catch (IOException | RuntimeException failure) {
        failed.incrementAndGet();
        throw failure;
      } finally {
        active.decrementAndGet();
        permits.release();
        exchange.close();
      }
    }

    private void serveWithPermit(HttpExchange exchange, Path pack) throws IOException {
      String method = exchange.getRequestMethod();
      if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      if (!Files.isRegularFile(pack)) {
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      exchange.getResponseHeaders().set("Content-Type", "application/zip");
      exchange.getResponseHeaders().set("Cache-Control", "no-cache");
      boolean head = "HEAD".equalsIgnoreCase(method);
      exchange.sendResponseHeaders(200, head ? -1 : Files.size(pack));
      if (!head) {
        try (OutputStream output = exchange.getResponseBody()) {
          bodyWriter.write(pack, output);
        }
      }
    }

    private Snapshot snapshot() {
      return new Snapshot(
          running,
          bindAddress,
          publicUrl,
          limit,
          active.get(),
          peak.get(),
          accepted.get(),
          completed.get(),
          rejected.get(),
          failed.get());
    }

    private String publicUrl() {
      return publicUrl;
    }
  }
}
