package com.greenhouse.backend.work.application.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkCorrectionResultDetailsTest {
  @Test
  void keepsAbsentLegacyFieldsAndNumericStringReferences() throws Exception {
    var absent = WorkCorrectionResultDetails.from(Map.of());
    assertThat(absent.adjustments()).isEmpty();
    assertThat(absent.quantityBalances()).isEmpty();
    assertThat(absent.beforeWorkDate()).isNull();
    var mapper = JsonMapper.builder().findAndAddModules().build();
    var payload =
        mapper.readValue(
            """
        {"beforeWorkDate":"2026-08-20","afterWorkDate":"2026-08-21",
         "adjustments":[{"orchidGroupId":"17","beforeQuantity":3,"afterQuantity":2,
           "beforeStatus":"관리","afterStatus":"정상"}],
         "quantityBalances":[{"before":{"executionId":5,"sourceInputQuantities":{"17":3},
           "resultQuantities":{"18":2},"inputQuantity":3,"resultQuantity":2,"lossQuantity":1,
           "increaseQuantity":0,"inputEditable":false,"increaseAllowed":false,"lossEditable":true},
           "after":{"executionId":5,"sourceInputQuantities":{"17":3},"resultQuantities":{"18":3},
           "inputQuantity":3,"resultQuantity":3,"lossQuantity":0,"increaseQuantity":0,
           "inputEditable":false,"increaseAllowed":false,"lossEditable":true}}]}
        """,
            new TypeReference<Map<String, Object>>() {});
    var result = WorkCorrectionResultDetails.from(payload);
    assertThat(result.beforeWorkDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    assertThat(result.afterWorkDate()).isEqualTo(LocalDate.of(2026, 8, 21));
    assertThat(result.adjustments())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.orchidGroupId()).isEqualTo(17L);
              assertThat(row.beforeQuantity()).isEqualTo(3);
              assertThat(row.afterStatus()).isEqualTo("정상");
            });
    assertThat(result.quantityBalances())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.before().sourceInputQuantities()).containsEntry(17L, 3);
              assertThat(row.after().resultQuantities()).containsEntry(18L, 3);
            });
  }

  @Test
  void explicitNullAndMalformedFieldsAreNotSilentlyTreatedAsAbsent() {
    var payload = new HashMap<String, Object>();
    payload.put("adjustments", null);
    assertThatThrownBy(() -> WorkCorrectionResultDetails.from(payload))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> WorkCorrectionResultDetails.from(Map.of("beforeWorkDate", "invalid")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> WorkCorrectionResultDetails.from(Map.of("quantityBalances", "invalid")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
