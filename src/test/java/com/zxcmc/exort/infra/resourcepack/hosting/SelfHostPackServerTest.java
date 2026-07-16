package com.zxcmc.exort.infra.resourcepack.hosting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class SelfHostPackServerTest {
  private static final String PUBLIC_URL = "https://packs.example.test/exort.zip";

  @TempDir Path tempDir;

  @Test
  @Timeout(15)
  void servesGetHeadMissingAndUnsupportedMethodWithoutChangingHttpContract() throws Exception {
    byte[] content = "zip-content".getBytes(StandardCharsets.UTF_8);
    Path pack = tempDir.resolve("exort.zip");
    Files.write(pack, content);
    SelfHostPackServer server = new SelfHostPackServer();
    try {
      server.start(pack.toFile(), "127.0.0.1", 0, PUBLIC_URL);
      URI endpoint = endpoint(server.snapshot());
      HttpClient client = client();

      HttpResponse<byte[]> get =
          client.send(
              HttpRequest.newBuilder(endpoint).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, get.statusCode());
      assertArrayEquals(content, get.body());
      assertEquals("application/zip", get.headers().firstValue("Content-Type").orElseThrow());
      assertEquals("no-cache", get.headers().firstValue("Cache-Control").orElseThrow());

      HttpResponse<byte[]> head =
          client.send(
              HttpRequest.newBuilder(endpoint)
                  .method("HEAD", HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, head.statusCode());
      assertEquals(0, head.body().length);

      HttpResponse<Void> unsupported =
          client.send(
              HttpRequest.newBuilder(endpoint).POST(HttpRequest.BodyPublishers.noBody()).build(),
              HttpResponse.BodyHandlers.discarding());
      assertEquals(405, unsupported.statusCode());

      Files.delete(pack);
      HttpResponse<Void> missing =
          client.send(
              HttpRequest.newBuilder(endpoint).GET().build(),
              HttpResponse.BodyHandlers.discarding());
      assertEquals(404, missing.statusCode());

      SelfHostPackServer.Snapshot snapshot = server.snapshot();
      assertEquals(4L, snapshot.accepted());
      assertEquals(4L, snapshot.completed());
      assertEquals(0L, snapshot.rejected());
      assertEquals(0L, snapshot.failed());
      assertEquals(0, snapshot.active());
    } finally {
      server.stop();
    }
  }

  @Test
  @Timeout(15)
  void saturationReturns503AndReportsExactGenerationMetrics() throws Exception {
    byte[] content = "bounded-pack".getBytes(StandardCharsets.UTF_8);
    Path pack = tempDir.resolve("bounded.zip");
    Files.write(pack, content);
    CountDownLatch entered = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    SelfHostPackServer server =
        new SelfHostPackServer(
            2,
            (source, output) -> {
              entered.countDown();
              awaitUninterruptibly(release);
              output.write(content);
            });
    try {
      server.start(pack.toFile(), "127.0.0.1", 0, PUBLIC_URL);
      URI endpoint = endpoint(server.snapshot());
      HttpClient client = client();
      List<CompletableFuture<HttpResponse<byte[]>>> accepted = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        accepted.add(
            client.sendAsync(
                HttpRequest.newBuilder(endpoint).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()));
      }
      assertTrue(entered.await(5, TimeUnit.SECONDS));

      HttpResponse<Void> rejected =
          client.send(
              HttpRequest.newBuilder(endpoint).GET().build(),
              HttpResponse.BodyHandlers.discarding());
      assertEquals(503, rejected.statusCode());
      assertEquals("1", rejected.headers().firstValue("Retry-After").orElseThrow());

      SelfHostPackServer.Snapshot saturated = server.snapshot();
      assertTrue(saturated.running());
      assertEquals(PUBLIC_URL, saturated.publicUrl());
      assertEquals(2, saturated.limit());
      assertEquals(2, saturated.active());
      assertEquals(2, saturated.peak());
      assertEquals(2L, saturated.accepted());
      assertEquals(0L, saturated.completed());
      assertEquals(1L, saturated.rejected());
      assertEquals(0L, saturated.failed());

      release.countDown();
      for (CompletableFuture<HttpResponse<byte[]>> response : accepted) {
        HttpResponse<byte[]> completed = response.get(5, TimeUnit.SECONDS);
        assertEquals(200, completed.statusCode());
        assertArrayEquals(content, completed.body());
      }
      await(() -> server.snapshot().active() == 0);
      SelfHostPackServer.Snapshot settled = server.snapshot();
      assertEquals(2L, settled.completed());
      assertEquals(0L, settled.failed());
    } finally {
      release.countDown();
      server.stop();
    }
  }

  @Test
  @Timeout(15)
  void failedBodyWriteReleasesPermitAndCountsFailure() throws Exception {
    Path pack = tempDir.resolve("failure.zip");
    Files.writeString(pack, "failure");
    CountDownLatch writeAttempted = new CountDownLatch(1);
    SelfHostPackServer server =
        new SelfHostPackServer(
            1,
            (source, output) -> {
              writeAttempted.countDown();
              throw new IOException("forced body failure");
            });
    try {
      server.start(pack.toFile(), "127.0.0.1", 0, PUBLIC_URL);
      try (Socket socket = new Socket()) {
        socket.setSoTimeout(5_000);
        socket.connect(parseAddress(server.snapshot().bindAddress()), 2_000);
        socket
            .getOutputStream()
            .write(
                ("GET /exort.zip HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "Connection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        assertTrue(writeAttempted.await(5, TimeUnit.SECONDS));
      }
      await(() -> server.snapshot().active() == 0);

      SelfHostPackServer.Snapshot snapshot = server.snapshot();
      assertEquals(1L, snapshot.accepted());
      assertEquals(0L, snapshot.completed());
      assertEquals(1L, snapshot.failed(), snapshot.toString());
      assertEquals(0, snapshot.active());
    } finally {
      server.stop();
    }
  }

  @Test
  @Timeout(15)
  void rebuildUsesFreshPermitsAndMetricsWhileOldRequestFinishes() throws Exception {
    Path oldPack = tempDir.resolve("old.zip");
    Path newPack = tempDir.resolve("new.zip");
    Files.writeString(oldPack, "old");
    byte[] newContent = "new".getBytes(StandardCharsets.UTF_8);
    Files.write(newPack, newContent);
    CountDownLatch oldEntered = new CountDownLatch(1);
    CountDownLatch releaseOld = new CountDownLatch(1);
    SelfHostPackServer server =
        new SelfHostPackServer(
            1,
            (source, output) -> {
              if (source.equals(oldPack)) {
                oldEntered.countDown();
                awaitUninterruptibly(releaseOld);
                output.write("old".getBytes(StandardCharsets.UTF_8));
                return;
              }
              output.write(newContent);
            });
    CompletableFuture<HttpResponse<byte[]>> oldRequest = null;
    try {
      server.start(oldPack.toFile(), "127.0.0.1", 0, PUBLIC_URL);
      oldRequest =
          client()
              .sendAsync(
                  HttpRequest.newBuilder(endpoint(server.snapshot())).GET().build(),
                  HttpResponse.BodyHandlers.ofByteArray());
      assertTrue(oldEntered.await(5, TimeUnit.SECONDS));
      assertEquals(1, server.snapshot().active());

      server.start(newPack.toFile(), "127.0.0.1", 0, PUBLIC_URL);
      SelfHostPackServer.Snapshot fresh = server.snapshot();
      assertEquals(0, fresh.active());
      assertEquals(0L, fresh.accepted());
      assertEquals(0L, fresh.rejected());

      HttpResponse<byte[]> newResponse =
          client()
              .send(
                  HttpRequest.newBuilder(endpoint(server.snapshot())).GET().build(),
                  HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, newResponse.statusCode());
      assertArrayEquals(newContent, newResponse.body());
      assertEquals(1L, server.snapshot().accepted());
      assertEquals(1L, server.snapshot().completed());

      releaseOld.countDown();
      if (oldRequest != null) {
        try {
          oldRequest.get(5, TimeUnit.SECONDS);
        } catch (Exception expected) {
          // stop(0) may close the old exchange before its body writer returns.
        }
      }
      assertEquals(1L, server.snapshot().accepted());
      assertEquals(1L, server.snapshot().completed());
    } finally {
      releaseOld.countDown();
      server.stop();
    }
  }

  @Test
  @Timeout(45)
  void slowClientsAndFloodStayBoundedThenNormalLargeDownloadSucceeds() throws Exception {
    Path pack = tempDir.resolve("large.zip");
    try (RandomAccessFile sparse = new RandomAccessFile(pack.toFile(), "rw")) {
      sparse.setLength(64L * 1024L * 1024L);
    }
    SelfHostPackServer server = new SelfHostPackServer();
    List<Socket> slowClients = new ArrayList<>();
    try {
      server.start(pack.toFile(), "127.0.0.1", 0, PUBLIC_URL);
      SelfHostPackServer.Snapshot started = server.snapshot();
      InetSocketAddress address = parseAddress(started.bindAddress());
      for (int i = 0; i < started.limit(); i++) {
        Socket socket = new Socket();
        socket.setReceiveBufferSize(1024);
        socket.setSoTimeout(5_000);
        socket.connect(address, 2_000);
        socket
            .getOutputStream()
            .write(
                ("GET /exort.zip HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "Connection: keep-alive\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        assertTrue(readHeaders(socket.getInputStream()).startsWith("HTTP/1.1 200"));
        slowClients.add(socket);
      }
      await(() -> server.snapshot().active() == server.snapshot().limit());

      int floodRequests = started.limit() * 2;
      HttpClient client = client();
      for (int i = 0; i < floodRequests; i++) {
        HttpResponse<Void> response =
            client.send(
                HttpRequest.newBuilder(endpoint(server.snapshot())).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(503, response.statusCode());
      }
      SelfHostPackServer.Snapshot saturated = server.snapshot();
      assertEquals(started.limit(), saturated.active());
      assertEquals(started.limit(), saturated.peak());
      assertEquals(floodRequests, saturated.rejected());

      for (Socket socket : slowClients) {
        socket.close();
      }
      slowClients.clear();
      await(() -> server.snapshot().active() == 0);
      assertTrue(server.snapshot().failed() > 0L);

      Path downloaded = tempDir.resolve("downloaded.zip");
      HttpResponse<Path> normal =
          client.send(
              HttpRequest.newBuilder(endpoint(server.snapshot())).GET().build(),
              HttpResponse.BodyHandlers.ofFile(downloaded));
      assertEquals(200, normal.statusCode());
      assertEquals(-1L, Files.mismatch(pack, downloaded));
      assertEquals(0, server.snapshot().active());
    } finally {
      for (Socket socket : slowClients) {
        try {
          socket.close();
        } catch (IOException ignored) {
          // Best-effort test cleanup.
        }
      }
      server.stop();
    }
  }

  private static HttpClient client() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  private static URI endpoint(SelfHostPackServer.Snapshot snapshot) {
    assertTrue(snapshot.running());
    assertNotNull(snapshot.bindAddress());
    return URI.create("http://" + snapshot.bindAddress() + "/exort.zip");
  }

  private static InetSocketAddress parseAddress(String bindAddress) {
    int separator = bindAddress.lastIndexOf(':');
    assertTrue(separator > 0, "invalid bind address: " + bindAddress);
    String host = bindAddress.substring(0, separator);
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    return new InetSocketAddress(host, Integer.parseInt(bindAddress.substring(separator + 1)));
  }

  private static String readHeaders(InputStream input) throws IOException {
    ByteArrayOutputStream headers = new ByteArrayOutputStream();
    int matched = 0;
    while (headers.size() < 16_384) {
      int next = input.read();
      if (next < 0) {
        break;
      }
      headers.write(next);
      matched =
          switch (matched) {
            case 0 -> next == '\r' ? 1 : 0;
            case 1 -> next == '\n' ? 2 : next == '\r' ? 1 : 0;
            case 2 -> next == '\r' ? 3 : 0;
            case 3 -> next == '\n' ? 4 : 0;
            default -> matched;
          };
      if (matched == 4) {
        return headers.toString(StandardCharsets.US_ASCII);
      }
    }
    throw new IOException("HTTP response headers were not completed");
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(10L);
    }
    assertTrue(condition.getAsBoolean(), "condition was not reached before timeout");
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (InterruptedException ignored) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
