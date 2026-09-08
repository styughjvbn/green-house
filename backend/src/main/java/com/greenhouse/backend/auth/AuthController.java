package com.greenhouse.backend.auth;

import com.greenhouse.backend.auth.dto.ApplicationContextResponse;
import com.greenhouse.backend.auth.dto.AuthenticatedUserResponse;
import com.greenhouse.backend.auth.dto.LoginRequest;
import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.config.TimeConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final Clock clock;

	@GetMapping("/context")
	public ApiResponse<ApplicationContextResponse> context() {
		return ApiResponse
			.ok(new ApplicationContextResponse(TimeConfig.farmToday(clock), TimeConfig.FARM_TIME_ZONE.getId()));
	}

	@PostMapping("/login")
	public ApiResponse<AuthenticatedUserResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest, HttpServletResponse response) {
		return ApiResponse.ok(authService.login(request.username(), request.password(), servletRequest, response));
	}

	@GetMapping("/me")
	public ApiResponse<AuthenticatedUserResponse> me(Authentication authentication) {
		return ApiResponse.ok(authService.currentUser(authentication));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(request, response);
		return ApiResponse.ok(null);
	}

}
