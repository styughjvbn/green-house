package com.greenhouse.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecurityConfigConditionTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(
					SecurityConfig.class,
					AuthController.class,
					SessionCookieRefreshFilter.class);

	@Test
	void doesNotLoadServletSecurityInANonWebApplication() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(SecurityConfig.class);
			assertThat(context).doesNotHaveBean(AuthController.class);
			assertThat(context).doesNotHaveBean(SessionCookieRefreshFilter.class);
		});
	}
}
