package com.greenhouse.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ErrorResponseWriterTest {

	@Test
	void preservesTheMvcEnvelopeAndEscapesMessageText() throws Exception {
		var mapper = JsonMapper.builder().build();
		var response = new MockHttpServletResponse();
		String message = "입력 \"이름\"에 줄바꿈\n과 역슬래시 \\가 있습니다.";

		new ErrorResponseWriter(mapper).write(response, 403, "FORBIDDEN", message);

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(mapper.readTree(response.getContentAsString()))
			.isEqualTo(mapper.valueToTree(ErrorResponse.of("FORBIDDEN", message, java.util.List.of())));
	}

}
