package com.greenhouse.backend.auth;

import com.greenhouse.backend.auth.dto.ApplicationContextResponse;
import com.greenhouse.backend.auth.dto.AuthenticatedUserResponse;
import com.greenhouse.backend.auth.dto.LoginRequest;
import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.config.TimeConfig;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import java.time.Clock;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final AuthService authService;
	private final AuthProperties authProperties;
	private final Clock clock;

	public AuthController(
			AuthenticationManager authenticationManager,
			AuthService authService,
			AuthProperties authProperties,
			Clock clock) {
		this.authenticationManager = authenticationManager;
		this.authService = authService;
		this.authProperties = authProperties;
		this.clock = clock;
	}

	@GetMapping("/context")
	public ApiResponse<ApplicationContextResponse> context() {
		return ApiResponse.ok(new ApplicationContextResponse(
				TimeConfig.farmToday(clock),
				TimeConfig.FARM_TIME_ZONE.getId()));
	}

	@PostMapping("/login")
	public ApiResponse<AuthenticatedUserResponse> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse response
	) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.username(), request.password())
		);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		HttpSession session = servletRequest.getSession(true);
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		int maxAgeSeconds = Math.toIntExact(authProperties.sessionTimeout().toSeconds());
		session.setMaxInactiveInterval(maxAgeSeconds);
		response.addHeader(
				"Set-Cookie",
				"JSESSIONID=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax"
						.formatted(session.getId(), maxAgeSeconds)
		);

		return ApiResponse.ok(authService.toResponse(authentication));
	}

	@GetMapping("/me")
	public ApiResponse<AuthenticatedUserResponse> me(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return ApiResponse.ok(null);
		}
		return ApiResponse.ok(authService.toResponse(authentication));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		response.addHeader("Set-Cookie", "JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
		return ApiResponse.ok(null);
	}
}
