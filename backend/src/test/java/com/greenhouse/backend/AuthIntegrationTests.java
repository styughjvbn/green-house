package com.greenhouse.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.enabled=true")
class AuthIntegrationTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void loginCreatesSession() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"admin","password":"admin"}
				"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value("admin"))
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(result -> {
					if (result.getRequest().getSession(false) == null) {
						throw new AssertionError("No session created");
					}
					String setCookie = result.getResponse().getHeader("Set-Cookie");
					if (setCookie == null || !setCookie.contains("Max-Age=604800")) {
						throw new AssertionError("Session cookie max age is not seven days");
					}
				});
	}

	@Test
	void apiRequiresLogin() throws Exception {
		mockMvc.perform(get("/api/dashboard/summary"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void workerCannotUseAdminWorkTypeApi() throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"worker","password":"worker"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

		mockMvc.perform(get("/api/work-types?includeInactive=true")
						.session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}
}
