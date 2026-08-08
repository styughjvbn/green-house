package com.greenhouse.backend.audit.application;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
	public static final String MDC_KEY = "requestId";
	private static final String HEADER = "X-Request-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = validRequestId(request.getHeader(HEADER));
		if (requestId == null) requestId = UUID.randomUUID().toString();
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		long startedAt = System.nanoTime();
		try {
			chain.doFilter(request, response);
		} finally {
			long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
			log.info("event=HTTP_REQUEST_COMPLETED method={} path={} status={} durationMs={} requestId={}",
					request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs, requestId);
			MDC.remove(MDC_KEY);
		}
	}

	private String validRequestId(String value) {
		if (value == null || value.isBlank() || value.length() > 100) return null;
		return value.matches("[A-Za-z0-9._:-]+") ? value : null;
	}
}
