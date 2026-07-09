package com.greenhouse.backend.auth.dto;

import com.greenhouse.backend.auth.AuthRole;

public record AuthenticatedUserResponse(
		String username,
		AuthRole role
) {
}
