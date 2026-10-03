package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkRequestFingerprintTest {

  private final WorkRequestFingerprint fingerprint = new WorkRequestFingerprint();

  @Test
  void objectKeyOrderAndNumericRepresentationDoNotChangeTheRequest() {
    var first = new LinkedHashMap<String, Object>();
    first.put("amount", 12L);
    first.put("nested", Map.of("position", new BigDecimal("6.00")));
    var second = new LinkedHashMap<String, Object>();
    second.put("nested", Map.of("position", 6));
    second.put("amount", 12);
    assertThat(fingerprint.calculate(first)).isEqualTo(fingerprint.calculate(second));
  }

  @Test
  void meaningfulArrayOrderAndFreeFormTextArePreserved() {
    assertThat(fingerprint.calculate(List.of(1, 2)))
        .isNotEqualTo(fingerprint.calculate(List.of(2, 1)));
    assertThat(fingerprint.calculate(Map.of("memo", " a ")))
        .isNotEqualTo(fingerprint.calculate(Map.of("memo", "a")));
    assertThat(fingerprint.calculate(Map.of("details", Map.of("idempotencyKey", "a"))))
        .isNotEqualTo(fingerprint.calculate(Map.of("details", Map.of("idempotencyKey", "b"))));
  }
}
