package com.greenhouse.backend.common.api;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

	public static ErrorResponse of(String code, String message, List<String> details) {
		return new ErrorResponse(new ErrorBody(code, message, details));
	}

	public record ErrorBody(String code, String message, List<String> details) {
	}
}
