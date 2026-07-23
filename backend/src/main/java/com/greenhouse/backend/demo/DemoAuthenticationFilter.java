package com.greenhouse.backend.demo;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.greenhouse.backend.auth.AuthRole;

public class DemoAuthenticationFilter extends OncePerRequestFilter {

	private final DemoProperties properties;

	public DemoAuthenticationFilter(DemoProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		if (!properties.enabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		var authentication = UsernamePasswordAuthenticationToken.authenticated(
				properties.username(),
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + AuthRole.DEMO.name()))
		);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		try {
			filterChain.doFilter(request, response);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
