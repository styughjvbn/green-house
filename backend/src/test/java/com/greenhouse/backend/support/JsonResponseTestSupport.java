package com.greenhouse.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public final class JsonResponseTestSupport {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonResponseTestSupport() {}

  public static long requiredId(String response, String pointer) throws IOException {
    var id = MAPPER.readTree(response).at(pointer);
    assertThat(id.isIntegralNumber() && id.canConvertToLong())
        .as("Numeric ID at %s in response %s", pointer, response)
        .isTrue();
    assertThat(id.longValue()).as("Positive ID at %s", pointer).isPositive();
    return id.longValue();
  }
}
