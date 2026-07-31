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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
		"app.auth.enabled=true",
		"app.demo.enabled=true",
		"app.demo.username=demo",
		"app.demo.request-limit-per-minute=1000",
		"app.demo.mutation-limit-per-minute=1000",
		"app.demo.mutation-limit-per-day=1000",
		"app.demo.max-request-bytes=1024"
})
class DemoModeIntegrationTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void accessesApiAsDemoUserWithoutSession() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value("demo"))
				.andExpect(jsonPath("$.data.role").value("DEMO"));
	}

	@Test
	void blocksLoginAndOperationalSettingsMutation() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"admin","password":"admin"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("DEMO_OPERATION_BLOCKED"));

		mockMvc.perform(post("/api/work-types")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("DEMO_OPERATION_BLOCKED"));
	}

	@Test
	void overwritesUserSuppliedCreatorWithDemoActor() throws Exception {
		mockMvc.perform(post("/api/orchid-group-collections")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "데모 그룹",
								  "description": "데모 인증 테스트",
								  "purpose": "테스트",
								  "createdBy": "forged-user"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.createdBy").value("demo"));
	}

	@Test
	void rejectsOversizedRequest() throws Exception {
		mockMvc.perform(post("/api/orchid-group-collections")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + "x".repeat(1200) + "\"}"))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.error.code").value("DEMO_REQUEST_TOO_LARGE"));
	}
}
