package com.greenhouse.backend.auth;

import com.greenhouse.backend.auth.dto.AuthenticatedUserResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	public AuthenticatedUserResponse toResponse(Authentication authentication) {
		String roleName = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(authority -> authority.startsWith("ROLE_"))
				.map(authority -> authority.substring("ROLE_".length()))
				.findFirst()
				.orElse(AuthRole.WORKER.name());

		return new AuthenticatedUserResponse(authentication.getName(), AuthRole.valueOf(roleName));
	}
}
