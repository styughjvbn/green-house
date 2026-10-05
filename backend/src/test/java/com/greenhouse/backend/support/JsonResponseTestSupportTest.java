package com.greenhouse.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonResponseTestSupportTest {
  @Test
  void readsTheRequestedIdRegardlessOfWhitespaceFieldOrderAndNestedIds() throws Exception {
    assertThat(
            JsonResponseTestSupport.requiredId(
                """
        { "id": 99, "data": {"targets": [{"id": 30}], "title": "작업", "id": 42} }
        """,
                "/data/id"))
        .isEqualTo(42);
    assertThat(
            JsonResponseTestSupport.requiredId(
                """
        {"data":{"id":42,"targets":[{"id":30}]}}
        """,
                "/data/targets/0/id"))
        .isEqualTo(30);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"data\":{\"id\":null}}",
        "{\"data\":{\"id\":\"42\"}}",
        "{\"data\":{\"id\":0}}",
        "{\"data\":{\"id\":1.5}}",
        "{\"data\":{\"id\":9223372036854775808}}"
      })
  void refusesMissingOrInvalidIdsInsteadOfSilentlyUsingZero(String response) {
    assertThatThrownBy(() -> JsonResponseTestSupport.requiredId(response, "/data/id"))
        .isInstanceOf(AssertionError.class);
  }
}
