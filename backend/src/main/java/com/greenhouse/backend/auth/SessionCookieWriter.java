package com.greenhouse.backend.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class SessionCookieWriter {

	private final AuthProperties properties;

	public void refresh(HttpSession session, HttpServletResponse response) {
		int maxAgeSeconds = Math.toIntExact(properties.sessionTimeout().toSeconds());
		session.setMaxInactiveInterval(maxAgeSeconds);
		write(response, session.getId(), maxAgeSeconds);
	}

	public void expire(HttpServletResponse response) {
		write(response, "", 0);
	}

	private void write(HttpServletResponse response, String sessionId, int maxAgeSeconds) {
		response.addHeader(HttpHeaders.SET_COOKIE,
				"JSESSIONID=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax".formatted(sessionId, maxAgeSeconds));
	}
}
