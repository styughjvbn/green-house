package com.greenhouse.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class TestHttpClientTest {
  @Test
  void stalledResponseFailsWithMethodPathAndTimeoutWithoutRequestBody() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try (var executor = Executors.newFixedThreadPool(2)) {
      server.setExecutor(executor);
      server.createContext(
          "/stalled",
          exchange -> {
            entered.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            } finally {
              exchange.close();
            }
          });
      server.start();
      try {
        var client = new TestHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(1));
        var failure =
            executor.submit(
                () ->
                    assertThatThrownBy(
                            () ->
                                client.send(
                                    "POST", uri(server, "/stalled"), "private payload", Map.of()))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("HTTP POST /stalled", "PT1S")
                        .hasMessageNotContaining("private payload")
                        .hasCauseInstanceOf(HttpTimeoutException.class));
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        failure.get(4, TimeUnit.SECONDS);
      } finally {
        release.countDown();
        server.stop(0);
      }
    }
  }

  @Test
  void sendsPatchBodyAndHeadersAndPreservesErrorResponse() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var received = new AtomicReference<String>();
    server.createContext(
        "/patch",
        exchange -> {
          received.set(
              exchange.getRequestMethod()
                  + ":"
                  + exchange.getRequestHeaders().getFirst("X-Test")
                  + ":"
                  + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = "{\"error\":\"expected\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(409, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try {
      var response =
          new TestHttpClient()
              .send("PATCH", uri(server, "/patch"), "{}", Map.of("X-Test", "header"));
      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(response.body()).isEqualTo("{\"error\":\"expected\"}");
      assertThat(received.get()).isEqualTo("PATCH:header:{}");
    } finally {
      server.stop(0);
    }
  }

  private URI uri(HttpServer server, String path) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
  }
}
