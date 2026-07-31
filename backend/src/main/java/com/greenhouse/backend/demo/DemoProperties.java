package com.greenhouse.backend.demo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(
		boolean enabled,
		@NotBlank String username,
		@Min(1) int requestLimitPerMinute,
		@Min(1) int mutationLimitPerMinute,
		@Min(1) int mutationLimitPerDay,
		@Min(1) long maxRequestBytes
) {
}
