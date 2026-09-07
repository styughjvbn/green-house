package com.greenhouse.backend.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SalesPaymentCategoryTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
			"미입금|UNPAID", "정산 대기|UNPAID", "입금 완료|PAID", "부분입금|PARTIAL",
			"부분 입금 완료|PARTIAL", "처리 완료|PAID", "미완료|PAID", "PAID|PAID",
			"PARTIALLY_PAID|UNPAID", "UNPAID|UNPAID", "paid|UNPAID", " PAID |UNPAID", "임의 상태|UNPAID", "''|UNPAID"
	})
	void preservesTheLegacyLabelGroupsIncludingTheirKnownLimitations(String storedStatus, SalesPaymentCategory expected) {
		assertThat(SalesPaymentCategory.fromStoredStatus(storedStatus)).isEqualTo(expected);
	}
}
