package com.greenhouse.backend.ai.slip.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.SlipConversationInput;
import com.greenhouse.backend.ai.slip.application.SlipConversationModelResult;
import com.greenhouse.backend.ai.slip.application.SlipModelResult;
import com.greenhouse.backend.ai.slip.application.port.out.AgentRuntimePort;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpAgentRuntimeAdapter implements AgentRuntimePort {
  private final String url;
  private final String secret;
  private final long timeout;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  public HttpAgentRuntimeAdapter(
      @Value("${app.ai.runtime-url:http://127.0.0.1:8091}") String url,
      @Value("${app.ai.runtime-secret:}") String secret,
      @Value("${app.ai.timeout-seconds:120}") long timeout) {
    this.url = url;
    this.secret = secret;
    this.timeout = timeout;
  }

  public SlipModelResult extract(String requestId, String token, String contentType, byte[] image) {
    return call(
        "/infer/slip",
        Map.of(
            "requestId",
            requestId,
            "toolToken",
            token,
            "contentType",
            contentType,
            "contentBase64",
            Base64.getEncoder().encodeToString(image),
            "timeoutSeconds",
            timeout),
        SlipModelResult.class);
  }

  public void openSession(String sessionId, List<String> varietyNames) {
    call("/sessions", Map.of("sessionId", sessionId, "varietyNames", varietyNames), Map.class);
  }

  public void closeSession(String sessionId) {
    call("/sessions/" + sessionId, null, Map.class);
  }

  public SlipConversationModelResult extractConversation(
      String requestId, String sessionId, String token, SlipConversationInput input) {
    var photos =
        input.photos().stream()
            .map(
                p -> {
                  var photo = new HashMap<String, Object>();
                  photo.put("photoId", p.photoId());
                  photo.put("contentType", p.contentType());
                  photo.put("contentBase64", Base64.getEncoder().encodeToString(p.image()));
                  photo.put("feedback", p.feedback());
                  return photo;
                })
            .toList();
    return call(
        "/infer/slip",
        Map.of(
            "requestId",
            requestId,
            "sessionId",
            sessionId,
            "toolToken",
            token,
            "photos",
            photos,
            "timeoutSeconds",
            timeout),
        SlipConversationModelResult.class);
  }

  private <T> T call(String path, Object body, Class<T> responseType) {
    if (secret.isBlank())
      throw new AgentException(503, "AI_NOT_CONFIGURED", "사진 인식 서비스 설정이 필요합니다.");
    try {
      var request =
          HttpRequest.newBuilder(URI.create(url + path))
              .timeout(Duration.ofSeconds(timeout + 5))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + secret)
              .method(
                  body == null ? "DELETE" : "POST",
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        String fallback =
            switch (response.statusCode()) {
              case 429 -> "AI_BUSY";
              case 504 -> "AI_TIMEOUT";
              case 503 -> "AI_UNAVAILABLE";
              default -> "AI_RUNTIME_FAILED";
            };
        String remoteCode =
            response.body().isBlank()
                ? ""
                : json.readTree(response.body()).path("error").path("code").asText();
        String code =
            switch (remoteCode) {
              case "AI_BUSY",
                  "AI_TIMEOUT",
                  "AI_TOOL_LIMIT",
                  "AI_INVALID_RESULT",
                  "AI_NOT_CONFIGURED",
                  "AI_UNAVAILABLE",
                  "AI_PROVIDER_FAILED",
                  "AI_INCOMPLETE",
                  "AI_SESSION_EXPIRED",
                  "AI_SESSION_LIMIT" ->
                  remoteCode;
              default -> fallback;
            };
        int status =
            switch (code) {
              case "AI_BUSY", "AI_TOOL_LIMIT" -> 429;
              case "AI_TIMEOUT" -> 504;
              case "AI_SESSION_EXPIRED" -> 410;
              case "AI_SESSION_LIMIT" -> 409;
              case "AI_NOT_CONFIGURED", "AI_UNAVAILABLE" -> 503;
              default -> 502;
            };
        String message =
            switch (code) {
              case "AI_TOOL_LIMIT" -> "후보 검색 횟수가 한도를 초과했습니다. 읽은 내용을 직접 확인하거나 다시 시도해주세요.";
              case "AI_NOT_CONFIGURED" -> "사진 인식 실행기 설정이 필요합니다. 직접 입력은 사용할 수 있습니다.";
              case "AI_BUSY" -> "다른 전표를 읽고 있습니다. 잠시 후 다시 시도해주세요.";
              case "AI_TIMEOUT" -> "사진 인식 시간이 초과되었습니다.";
              case "AI_SESSION_EXPIRED", "AI_SESSION_LIMIT" ->
                  "사진 등록 대화가 만료되었거나 한도를 초과했습니다. 창을 닫고 다시 등록해주세요.";
              default -> "사진 내용을 읽지 못했습니다. 잠시 후 다시 시도하거나 직접 입력해주세요.";
            };
        throw new AgentException(status, code, message);
      }
      return json.readValue(response.body(), responseType);
    } catch (JsonProcessingException e) {
      throw new AgentException(502, "AI_INVALID_RESULT", "사진 인식 결과 형식이 올바르지 않습니다.");
    } catch (HttpTimeoutException e) {
      throw new AgentException(504, "AI_TIMEOUT", "사진 인식 시간이 초과되었습니다.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AgentException(502, "AI_INTERRUPTED", "사진 인식이 중단되었습니다.");
    } catch (IOException e) {
      throw new AgentException(502, "AI_UNAVAILABLE", "사진 인식 서비스에 연결하지 못했습니다.");
    }
  }
}
