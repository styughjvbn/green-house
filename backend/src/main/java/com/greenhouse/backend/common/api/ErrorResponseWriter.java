package com.greenhouse.backend.common.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the same error envelope as MVC advice for failures raised before a controller.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

	private final ObjectMapper objectMapper;

	public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(code, message, List.of())));
	}

}
