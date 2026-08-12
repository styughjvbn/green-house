package com.greenhouse.backend.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SalesSlipAllocationBatchTest {

	@Test
	void keepsItemAllocationPairsAndSortsDistinctGroupIds() {
		OrchidGroup firstGroup = group(2L);
		OrchidGroup secondGroup = group(1L);
		SalesSlipItem item = item();
		item.addAllocation(new SalesSlipItemAllocation(firstGroup, 2));
		item.addAllocation(new SalesSlipItemAllocation(secondGroup, 1));
		SalesSlip salesSlip = slip();
		salesSlip.addItem(item);

		SalesSlipAllocationBatch batch = SalesSlipAllocationBatch.from(salesSlip);

		assertThat(batch.orchidGroupIds()).containsExactly(1L, 2L);
		assertThat(batch.lines()).extracting(SalesSlipAllocationBatch.Line::item)
				.containsExactly(item, item);
		assertThat(batch.lines()).extracting(SalesSlipAllocationBatch.Line::allocatedQuantity)
				.containsExactly(2, 1);
	}

	@Test
	void copiesAllocationsThroughTheSameCreationSeam() {
		OrchidGroup group = group(1L);
		SalesSlipItemAllocation original = new SalesSlipItemAllocation(group, 4);
		SalesSlipAllocationFactory factory = new SalesSlipAllocationFactory(
				mock(OrchidGroupReader.class),
				Clock.fixed(Instant.parse("2026-08-12T01:02:03Z"), ZoneOffset.UTC));

		SalesSlipItemAllocation copied = factory.copyAllocation(original);

		assertThat(copied).isNotSameAs(original);
		assertThat(copied.getOrchidGroup()).isSameAs(group);
		assertThat(copied.getAllocatedQuantity()).isEqualTo(4);
	}

	private OrchidGroup group(Long id) {
		OrchidGroup group = mock(OrchidGroup.class);
		when(group.getId()).thenReturn(id);
		return group;
	}

	private SalesSlipItem item() {
		return new SalesSlipItem(null, "호접란", "팔레놉시스", "특품", 3, 1_000, null);
	}

	private SalesSlip slip() {
		return new SalesSlip(
				"SNAPSHOT-SEAM",
				LocalDate.of(2026, 8, 11),
				SalesType.DIRECT,
				null,
				null,
				"미입금",
				SalesSlip.STATUS_DRAFT,
				null,
				null);
	}
}
