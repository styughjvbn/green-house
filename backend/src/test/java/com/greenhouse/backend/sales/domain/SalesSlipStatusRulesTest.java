package com.greenhouse.backend.sales.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SalesSlipStatusRulesTest {

	@Test
	void rejectsCanceledOrWrongTypeStatusAtCreation() {
		assertThatThrownBy(() -> slip(SalesType.DIRECT, SalesSlip.STATUS_CANCELED))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> slip(SalesType.DIRECT, SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allowsOnlyTypeSpecificCompletionFromDraft() {
		SalesSlip slip = slip(SalesType.AUCTION, SalesSlip.STATUS_DRAFT);

		assertThatThrownBy(() -> slip.updateSalesStatus(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private SalesSlip slip(SalesType salesType, String status) {
		return new SalesSlip(
				"STATUS-" + salesType + "-" + status,
				LocalDate.of(2026, 8, 1),
				salesType,
				null,
				null,
				"미입금",
				status,
				null,
				null);
	}
}
