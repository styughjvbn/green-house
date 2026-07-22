package com.greenhouse.backend.work.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class WorkE2ETestBase {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	private final HttpClient httpClient = HttpClient.newHttpClient();
	protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@LocalServerPort
	private int port;

	protected ApiResult get(String path) throws IOException, InterruptedException {
		return exchange(HttpRequest.newBuilder(uri(path)).GET().build());
	}

	protected ApiResult post(String path, String body) throws IOException, InterruptedException {
		return exchange(HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build());
	}

	private ApiResult exchange(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
