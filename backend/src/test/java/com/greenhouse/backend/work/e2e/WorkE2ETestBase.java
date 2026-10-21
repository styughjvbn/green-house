package com.greenhouse.backend.work.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.support.TestHttpClient;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SAME_THREAD)
@ActiveProfiles("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({OrchidGroupLedgerTestFixture.class, PostgresE2eConfiguration.class})
@TestExecutionListeners(
    listeners = PostgresE2eResetListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
abstract class WorkE2ETestBase {

  static final PostgreSQLContainer POSTGRES = PostgresE2eConfiguration.POSTGRES;

  @Autowired protected PostgresE2eConfiguration.TestDatabase testDatabase;

  private final TestHttpClient httpClient = new TestHttpClient();

  protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @LocalServerPort private int port;

  protected ApiResult get(String path) throws IOException, InterruptedException {
    return exchange("GET", path, null, Map.of());
  }

  protected ApiResult post(String path, String body) throws IOException, InterruptedException {
    return post(path, body, Map.of());
  }

  protected ApiResult post(String path, String body, Map<String, String> headers)
      throws IOException, InterruptedException {
    return exchange("POST", path, body, headers);
  }

  protected ApiResult patchJson(String path, String body) throws IOException, InterruptedException {
    return exchange("PATCH", path, body, Map.of());
  }

  protected ApiResult putJson(String path, String body) throws IOException, InterruptedException {
    return exchange("PUT", path, body, Map.of());
  }

  private ApiResult exchange(String method, String path, String body, Map<String, String> headers)
      throws IOException, InterruptedException {
    var requestHeaders = new LinkedHashMap<>(headers);
    requestHeaders.putIfAbsent("Content-Type", "application/json");
    var response = httpClient.send(method, uri(path), body, requestHeaders);
    return new ApiResult(response.statusCode(), objectMapper.readTree(response.body()));
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  protected record ApiResult(int status, JsonNode body) {
    JsonNode data() {
      return body.path("data");
    }
  }
}
