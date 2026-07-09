package com.greenhouse.backend.auth;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			AuthProperties authProperties
	) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

		if (!authProperties.enabled()) {
			http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
			return http.build();
		}

		http
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."))
						.accessDeniedHandler((request, response, exception) ->
								writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."))
				)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/auth/login", "/api/auth/me").permitAll()
						.requestMatchers("/actuator/health", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/api/work-types/**").hasRole(AuthRole.ADMIN.name())
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll()
				);

		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService(AuthProperties authProperties, PasswordEncoder passwordEncoder) {
		return new InMemoryUserDetailsManager(
				User.withUsername(authProperties.adminUsername())
						.password(passwordEncoder.encode(authProperties.adminPassword()))
						.roles(AuthRole.ADMIN.name())
						.build(),
				User.withUsername(authProperties.workerUsername())
						.password(passwordEncoder.encode(authProperties.workerPassword()))
						.roles(AuthRole.WORKER.name())
						.build()
		);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

	private void writeError(
			HttpServletResponse response,
			HttpStatus status,
			String code,
			String message
	) throws IOException {
		response.setStatus(status.value());
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("""
				{"error":{"code":"%s","message":"%s","details":[]}}
				""".formatted(code, message));
	}
}
