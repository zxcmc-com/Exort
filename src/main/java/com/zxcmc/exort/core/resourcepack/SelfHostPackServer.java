package com.zxcmc.exort.core.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

final class SelfHostPackServer {
  private static final String PACK_PATH = "/exort.zip";

  private final JavaPlugin plugin;
  private HttpServer server;
  private ExecutorService executor;
  private String url;

  SelfHostPackServer(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  synchronized String start(File packFile, String bindHost, int port, String publicUrl)
      throws IOException {
    stop();
    String host = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost.trim();
    server = HttpServer.create(new InetSocketAddress(host, Math.max(0, port)), 0);
    server.createContext(PACK_PATH, exchange -> servePack(exchange, packFile));
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.start();
    int actualPort = server.getAddress().getPort();
    url =
        publicUrl != null && !publicUrl.isBlank()
            ? publicUrl.trim()
            : "http://" + inferPublicHost(host) + ":" + actualPort + PACK_PATH;
    return url;
  }

  synchronized void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
      url = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  synchronized String url() {
    return url;
  }

  private void servePack(HttpExchange exchange, File packFile) throws IOException {
    String method = exchange.getRequestMethod();
    if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
      exchange.sendResponseHeaders(405, -1);
      exchange.close();
      return;
    }
    if (!packFile.isFile()) {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }
    exchange.getResponseHeaders().set("Content-Type", "application/zip");
    exchange.getResponseHeaders().set("Cache-Control", "no-cache");
    exchange.sendResponseHeaders(200, "HEAD".equalsIgnoreCase(method) ? -1 : packFile.length());
    if (!"HEAD".equalsIgnoreCase(method)) {
      try (var out = exchange.getResponseBody()) {
        java.nio.file.Files.copy(packFile.toPath(), out);
      }
    } else {
      exchange.close();
    }
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
      plugin.getLogger().warning("Failed to infer resource-pack public host: " + e.getMessage());
    }
    plugin
        .getLogger()
        .warning(
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
      plugin
          .getLogger()
          .warning(
              "Failed to inspect network interfaces for resource-pack self-hosting: "
                  + e.getMessage());
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
}
