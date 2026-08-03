package com.greenhouse.backend.audit.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditRequestContext {

	public AuditIdentity current() {
		var attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
			return new AuditIdentity(null, null, null, MDC.get(RequestIdFilter.MDC_KEY));
		}
		HttpServletRequest request = servletAttributes.getRequest();
		HttpSession session = request.getSession(false);
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String actorId = authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)
				? authentication.getName() : null;
		return new AuditIdentity(
				actorId,
				session == null ? null : session.getId(),
				normalize(request.getHeader("X-Client-Instance-Id"), 100),
				MDC.get(RequestIdFilter.MDC_KEY));
	}

	private String normalize(String value, int maxLength) {
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
	}

	public record AuditIdentity(String actorId, String sessionId, String clientInstanceId, String requestId) {
	}
}
