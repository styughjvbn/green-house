package com.greenhouse.backend.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Evaluated after application beans so a supplied account provider replaces the default.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AuthProperties.class)
public class DefaultAccountsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(UserDetailsService.class)
	UserDetailsService userDetailsService(AuthProperties properties, PasswordEncoder passwordEncoder) {
		return new InMemoryUserDetailsManager(
				User.withUsername(properties.adminUsername())
					.password(passwordEncoder.encode(properties.adminPassword()))
					.roles(AuthRole.ADMIN.name())
					.build(),
				User.withUsername(properties.workerUsername())
					.password(passwordEncoder.encode(properties.workerPassword()))
					.roles(AuthRole.WORKER.name())
					.build());
	}

}
