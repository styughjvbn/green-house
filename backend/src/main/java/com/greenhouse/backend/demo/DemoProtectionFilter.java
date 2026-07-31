package com.greenhouse.backend.demo;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class DemoProtectionFilter extends OncePerRequestFilter {

	private final DemoProperties properties;
	private final Clock clock;
	private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, DailyMutationWindow> dailyMutationWindows = new ConcurrentHashMap<>();

	public DemoProtectionFilter(DemoProperties properties) {
		this(properties, Clock.systemUTC());
	}

	DemoProtectionFilter(DemoProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		if (request.getContentLengthLong() > properties.maxRequestBytes()) {
			writeError(response, 413, "DEMO_REQUEST_TOO_LARGE", "데모 환경의 요청 크기 제한을 초과했습니다.");
			return;
		}

		if (isBlockedOperation(request)) {
			writeError(response, 403, "DEMO_OPERATION_BLOCKED", "데모 환경에서 사용할 수 없는 기능입니다.");
			return;
		}

		long currentMinute = clock.millis() / 60_000;
		boolean mutation = isMutation(request.getMethod());
		RequestWindow window = windows.compute(request.getRemoteAddr(), (key, existing) -> {
			if (existing == null || existing.minute() != currentMinute) {
				return new RequestWindow(currentMinute, 1, mutation ? 1 : 0);
			}
			return new RequestWindow(
					currentMinute,
					existing.requests() + 1,
					existing.mutations() + (mutation ? 1 : 0)
			);
		});

		if (window.requests() > properties.requestLimitPerMinute()
				|| window.mutations() > properties.mutationLimitPerMinute()) {
			response.setHeader("Retry-After", "60");
			writeError(response, 429, "DEMO_RATE_LIMIT_EXCEEDED", "데모 환경의 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
			return;
		}

		if (mutation) {
			long currentDay = clock.millis() / 86_400_000;
			DailyMutationWindow dailyWindow = dailyMutationWindows.compute(request.getRemoteAddr(), (key, existing) -> {
				if (existing == null || existing.day() != currentDay) {
					return new DailyMutationWindow(currentDay, 1);
				}
				return new DailyMutationWindow(currentDay, existing.mutations() + 1);
			});
			if (dailyWindow.mutations() > properties.mutationLimitPerDay()) {
				response.setHeader("Retry-After", "3600");
				writeError(response, 429, "DEMO_DATA_LIMIT_EXCEEDED", "데모 환경의 일일 데이터 변경 한도를 초과했습니다.");
				return;
			}
		}

		if (windows.size() > 10_000) {
			windows.entrySet().removeIf(entry -> entry.getValue().minute() < currentMinute);
			long currentDay = clock.millis() / 86_400_000;
			dailyMutationWindows.entrySet().removeIf(entry -> entry.getValue().day() < currentDay);
		}

		filterChain.doFilter(request, response);
	}

	private boolean isBlockedOperation(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path.equals("/api/auth/login") || path.equals("/api/auth/logout")) {
			return true;
		}
		if (!isMutation(request.getMethod())) {
			return false;
		}
		return path.startsWith("/api/work-types")
				|| path.startsWith("/api/partner-settlement-settings");
	}

	private boolean isMutation(String method) {
		return switch (method) {
			case "POST", "PUT", "PATCH", "DELETE" -> true;
			default -> false;
		};
	}

	private void writeError(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("""
				{"error":{"code":"%s","message":"%s","details":[]}}
				""".formatted(code, message));
	}

	private record RequestWindow(long minute, int requests, int mutations) {
	}

	private record DailyMutationWindow(long day, int mutations) {
	}
}
