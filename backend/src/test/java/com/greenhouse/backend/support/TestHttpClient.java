package com.greenhouse.backend.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Bounded transport shared by real HTTP regression tests; never logs request bodies. */
public final class TestHttpClient {
  public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  private final HttpClient client;
  private final Duration requestTimeout;

  public TestHttpClient() {
    this(CONNECT_TIMEOUT, REQUEST_TIMEOUT);
  }

  TestHttpClient(Duration connectTimeout, Duration requestTimeout) {
    client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    if (requestTimeout.isZero() || requestTimeout.isNegative()) {
      throw new IllegalArgumentException("Positive request timeout required");
    }
    this.requestTimeout = requestTimeout;
  }

  public HttpResponse<String> send(String method, URI uri, String body, Map<String, String> headers)
      throws InterruptedException {
    var request = HttpRequest.newBuilder(uri).timeout(requestTimeout);
    headers.forEach(request::header);
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    try {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new AssertionError(
          "HTTP "
              + method
              + " "
              + uri.getPath()
              + " failed (connect="
              + client.connectTimeout().orElseThrow()
              + ", request="
              + requestTimeout
              + ")",
          exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw exception;
    }
  }
}
