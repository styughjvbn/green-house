package com.greenhouse.backend.demo;

import com.greenhouse.backend.common.api.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.web.filter.OncePerRequestFilter;

public class DemoProtectionFilter extends OncePerRequestFilter {

	private final DemoProperties properties;

	private final DemoRequestLimiter limiter;

	private final ErrorResponseWriter errorResponseWriter;

	public DemoProtectionFilter(DemoProperties properties, Clock clock, ErrorResponseWriter errorResponseWriter) {
		this.properties = properties;
		this.limiter = new DemoRequestLimiter(properties, clock);
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > properties.maxRequestBytes()) {
			errorResponseWriter.write(response, 413, "DEMO_REQUEST_TOO_LARGE", "데모 환경의 요청 크기 제한을 초과했습니다.");
			return;
		}
		if (DemoRequestPolicy.blocks(request.getMethod(), request.getRequestURI())) {
			errorResponseWriter.write(response, 403, "DEMO_OPERATION_BLOCKED", "데모 환경에서 사용할 수 없는 기능입니다.");
			return;
		}

		DemoRequestLimit limit = limiter.record(request.getRemoteAddr(),
				DemoRequestPolicy.isMutation(request.getMethod()));
		switch (limit) {
			case MINUTE_EXCEEDED -> {
				response.setHeader("Retry-After", "60");
				errorResponseWriter.write(response, 429, "DEMO_RATE_LIMIT_EXCEEDED",
						"데모 환경의 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
			}
			case DAY_EXCEEDED -> {
				response.setHeader("Retry-After", "3600");
				errorResponseWriter.write(response, 429, "DEMO_DATA_LIMIT_EXCEEDED", "데모 환경의 일일 데이터 변경 한도를 초과했습니다.");
			}
			case ALLOWED -> filterChain.doFilter(request, response);
		}
	}

}
