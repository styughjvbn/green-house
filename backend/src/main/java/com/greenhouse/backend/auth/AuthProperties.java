package com.greenhouse.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
		boolean enabled,
		Duration sessionTimeout,
		String adminUsername,
		String adminPassword,
		String workerUsername,
		String workerPassword
) {
}
