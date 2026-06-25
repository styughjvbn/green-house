package com.greenhouse.backend.common.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] frontendOrigins;

	public CorsConfig(@Value("${app.cors.frontend-origins:http://localhost:3000}") String frontendOrigins) {
		this.frontendOrigins = parseOrigins(frontendOrigins);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOrigins(frontendOrigins)
			.allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.allowCredentials(false)
			.maxAge(3600);
		registry.addMapping("/actuator/**")
			.allowedOrigins(frontendOrigins)
			.allowedMethods("GET", "OPTIONS")
			.allowedHeaders("*")
			.allowCredentials(false)
			.maxAge(3600);
	}

	private String[] parseOrigins(String origins) {
		List<String> parsedOrigins = Arrays.stream(origins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isBlank())
			.toList();

		return parsedOrigins.toArray(String[]::new);
	}
}
