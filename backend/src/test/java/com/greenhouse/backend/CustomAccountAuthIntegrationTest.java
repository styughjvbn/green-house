package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.enabled=true")
@Import(CustomAccountAuthIntegrationTest.AccountConfiguration.class)
class CustomAccountAuthIntegrationTest {

	@Autowired MockMvc mockMvc;
	@Autowired Map<String, UserDetailsService> accountProviders;

	@Test
	void anotherAccountProviderUsesTheExistingLoginAndSessionFlow() throws Exception {
		assertThat(accountProviders).hasSize(1).containsKey("testAccountProvider");
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"external-worker","password":"test-password"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value("external-worker"))
				.andExpect(jsonPath("$.data.role").value("WORKER"))
				.andExpect(result -> assertThat(result.getRequest().getSession(false)).isNotNull());
	}

	@TestConfiguration
	static class AccountConfiguration {
		@Bean
		UserDetailsService testAccountProvider(PasswordEncoder encoder) {
			var user = User.withUsername("external-worker").password(encoder.encode("test-password"))
					.roles("WORKER").build();
			return username -> {
				if (!username.equals(user.getUsername())) throw new UsernameNotFoundException(username);
				return user;
			};
		}
	}
}
