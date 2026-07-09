package com.greenhouse.backend.common.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] frontendOriginPatterns;

	public CorsConfig(@Value("${app.cors.frontend-origin-patterns:http://localhost:*}") String frontendOriginPatterns) {
		this.frontendOriginPatterns = parseOrigins(frontendOriginPatterns);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns(frontendOriginPatterns)
				.allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true)
				.maxAge(3600);
		registry.addMapping("/actuator/**")
				.allowedOriginPatterns(frontendOriginPatterns)
				.allowedMethods("GET", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true)
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
