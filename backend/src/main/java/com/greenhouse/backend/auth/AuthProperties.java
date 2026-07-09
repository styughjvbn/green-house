package com.greenhouse.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
		boolean enabled,
		String adminUsername,
		String adminPassword,
		String workerUsername,
		String workerPassword
) {
}
