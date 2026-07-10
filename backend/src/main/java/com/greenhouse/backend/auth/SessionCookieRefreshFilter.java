package com.greenhouse.backend.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionCookieRefreshFilter extends OncePerRequestFilter {

	private final AuthProperties authProperties;

	public SessionCookieRefreshFilter(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		filterChain.doFilter(request, response);

		if (!authProperties.enabled()) {
			return;
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return;
		}

		HttpSession session = request.getSession(false);
		if (session == null) {
			return;
		}

		int maxAgeSeconds = Math.toIntExact(authProperties.sessionTimeout().toSeconds());
		session.setMaxInactiveInterval(maxAgeSeconds);
		response.addHeader(
				"Set-Cookie",
				"JSESSIONID=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax"
						.formatted(session.getId(), maxAgeSeconds)
		);
	}
}
