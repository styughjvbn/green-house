package com.greenhouse.backend.ai.slip.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.SlipConversationInput;
import com.greenhouse.backend.ai.slip.application.SlipConversationInput.Photo;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpAgentRuntimeAdapterTest {
  @Test
  void sendsScopedCredentialsToConfiguredRuntimeAndMapsBusyResponse() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var authorization = new AtomicReference<String>();
    var requestBody = new AtomicReference<String>();
    server.createContext(
        "/infer/slip",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(429, -1);
          exchange.close();
        });
    server.start();
    try {
      var adapter =
          new HttpAgentRuntimeAdapter(
              "http://127.0.0.1:" + server.getAddress().getPort(), "secret", 5);
      assertThatThrownBy(() -> adapter.extract("r", "scope-token", "image/png", new byte[] {1}))
          .isInstanceOf(AgentException.class)
          .extracting("code")
          .isEqualTo("AI_BUSY");
      assertThat(authorization.get()).isEqualTo("Bearer secret");
      assertThat(requestBody.get()).contains("scope-token", "requestId", "contentBase64");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void preservesStructuredToolLimitCodeWithoutExposingRuntimeMessages() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/infer/slip",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] body =
              "{\"error\":{\"code\":\"AI_TOOL_LIMIT\",\"message\":\"private-runtime-details\"}}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(429, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var adapter =
          new HttpAgentRuntimeAdapter(
              "http://127.0.0.1:" + server.getAddress().getPort(), "secret", 5);
      assertThatThrownBy(() -> adapter.extract("r", "token", "image/png", new byte[] {1}))
          .isInstanceOf(AgentException.class)
          .hasMessageNotContaining("private-runtime-details")
          .extracting("code")
          .isEqualTo("AI_TOOL_LIMIT");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void conversationLifecycleAndTenImagesAreSentAsOneRuntimeRequestWithStableIdsAndFeedback()
      throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var requests = new ArrayList<String>();
    var payload = new AtomicReference<String>();
    var sessionPayload = new AtomicReference<String>();
    server.createContext(
        "/",
        exchange -> {
          requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          if (exchange.getRequestURI().getPath().equals("/infer/slip")) payload.set(body);
          if (exchange.getRequestURI().getPath().equals("/sessions")) sessionPayload.set(body);
          byte[] response =
              (exchange.getRequestURI().getPath().equals("/infer/slip")
                      ? "{\"photos\":[{\"photoId\":\"p-0\",\"result\":null}]}"
                      : "{}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      var adapter =
          new HttpAgentRuntimeAdapter(
              "http://127.0.0.1:" + server.getAddress().getPort(), "secret", 5);
      var names = List.of("하니문", "프라그랑스");
      adapter.openSession("session", names);
      var sessionJson = JsonMapper.builder().build().readTree(sessionPayload.get());
      assertThat(sessionJson.path("varietyNames").size()).isEqualTo(2);
      assertThat(sessionJson.path("varietyNames").get(0).asText()).isEqualTo("하니문");
      assertThat(sessionJson.path("varietyNames").get(1).asText()).isEqualTo("프라그랑스");
      var photos = new ArrayList<Photo>();
      for (int i = 0; i < 10; i++)
        photos.add(new Photo("p-" + i, "image/png", new byte[] {1}, i == 0 ? "수량 20개" : null));
      var result =
          adapter.extractConversation(
              "request", "session", "token", new SlipConversationInput(photos));
      assertThat(result.photos().getFirst().photoId()).isEqualTo("p-0");
      var json = JsonMapper.builder().build().readTree(payload.get());
      assertThat(json.path("sessionId").asText()).isEqualTo("session");
      assertThat(json.has("varietyNames")).isFalse();
      assertThat(json.path("photos").size()).isEqualTo(10);
      assertThat(json.path("photos").get(0).path("feedback").asText()).isEqualTo("수량 20개");
      adapter.closeSession("session");
      assertThat(requests)
          .containsExactly("POST /sessions", "POST /infer/slip", "DELETE /sessions/session");
    } finally {
      server.stop(0);
    }
  }
}
