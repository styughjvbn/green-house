package com.greenhouse.backend.auth;

import com.greenhouse.backend.common.api.ErrorResponseWriter;
import com.greenhouse.backend.demo.DemoAuthenticationFilter;
import com.greenhouse.backend.demo.DemoProperties;
import com.greenhouse.backend.demo.DemoProtectionFilter;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, DemoProperties.class})
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			AuthProperties authProperties,
			DemoProperties demoProperties,
			ErrorResponseWriter errorResponseWriter,
			Clock clock
	) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

		if (demoProperties.enabled()) {
			var authenticationFilter = new DemoAuthenticationFilter(demoProperties);
			var protectionFilter = new DemoProtectionFilter(demoProperties, clock, errorResponseWriter);
			http
					.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
					.addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class)
					.addFilterAfter(protectionFilter, DemoAuthenticationFilter.class)
					.authorizeHttpRequests(authorize -> authorize
							.requestMatchers("/actuator/health", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
							.requestMatchers("/api/**").authenticated()
							.anyRequest().permitAll()
					);
			return http.build();
		}

		if (!authProperties.enabled()) {
			http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
			return http.build();
		}

		http
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "로그인이 필요합니다."))
						.accessDeniedHandler((request, response, exception) ->
								errorResponseWriter.write(response, HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "접근 권한이 없습니다."))
				)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/auth/login", "/api/auth/me", "/api/auth/context").permitAll()
						.requestMatchers("/actuator/health", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/api/work-types/**").hasRole(AuthRole.ADMIN.name())
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll()
				);

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

}
