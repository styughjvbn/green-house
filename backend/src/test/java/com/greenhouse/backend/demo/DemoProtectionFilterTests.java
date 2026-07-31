package com.greenhouse.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DemoProtectionFilterTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void limitsRequestsPerClientAndMinute() throws Exception {
		var properties = new DemoProperties(true, "demo", 1, 10, 100, 1024);
		var filter = new DemoProtectionFilter(properties, clock);

		var firstResponse = perform(filter, "GET");
		var secondResponse = perform(filter, "GET");

		assertThat(firstResponse.getStatus()).isEqualTo(200);
		assertThat(secondResponse.getStatus()).isEqualTo(429);
		assertThat(secondResponse.getContentAsString()).contains("DEMO_RATE_LIMIT_EXCEEDED");
	}

	@Test
	void limitsDailyMutations() throws Exception {
		var properties = new DemoProperties(true, "demo", 10, 10, 1, 1024);
		var filter = new DemoProtectionFilter(properties, clock);

		var firstResponse = perform(filter, "POST");
		var secondResponse = perform(filter, "POST");

		assertThat(firstResponse.getStatus()).isEqualTo(200);
		assertThat(secondResponse.getStatus()).isEqualTo(429);
		assertThat(secondResponse.getContentAsString()).contains("DEMO_DATA_LIMIT_EXCEEDED");
	}

	private MockHttpServletResponse perform(DemoProtectionFilter filter, String method) throws Exception {
		var request = new MockHttpServletRequest(method, "/api/dashboard/summary");
		request.setRemoteAddr("203.0.113.10");
		var response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}
}
