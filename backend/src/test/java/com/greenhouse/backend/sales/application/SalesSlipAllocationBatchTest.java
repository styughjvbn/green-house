package com.greenhouse.backend.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SalesSlipAllocationBatchTest {

	@Test
	void keepsItemAllocationPairsAndSortsDistinctGroupIds() {
		SalesSlipItem item = item();
		item.addAllocation(new SalesSlipItemAllocation(2L, 2));
		item.addAllocation(new SalesSlipItemAllocation(1L, 1));
		SalesSlip salesSlip = slip();
		salesSlip.addItem(item);

		SalesSlipAllocationBatch batch = SalesSlipAllocationBatch.from(salesSlip);

		assertThat(batch.orchidGroupIds()).containsExactly(1L, 2L);
		assertThat(batch.lines()).extracting(SalesSlipAllocationBatch.Line::item).containsExactly(item, item);
		assertThat(batch.lines()).extracting(SalesSlipAllocationBatch.Line::allocatedQuantity).containsExactly(2, 1);
	}

	@Test
	void copiesSnapshotsWithoutRecapturingCurrentFarmState() {
		var state = mock(OrchidGroupState.class);
		when(state.id()).thenReturn(1L);
		when(state.quantity()).thenReturn(20);
		SalesSlipItemAllocation original = new SalesSlipItemAllocation(1L, 4);
		var capturedAt = LocalDateTime.of(2026, 8, 12, 1, 2, 3);
		var snapshot = SalesSlipAllocationBatch.captureSnapshot(original, SalesOrchidSnapshotType.CREATION, capturedAt,
				state);
		when(state.quantity()).thenReturn(16);

		SalesSlipItemAllocation copied = original.copy();

		assertThat(copied).isNotSameAs(original);
		assertThat(copied.getOrchidGroupId()).isEqualTo(1L);
		assertThat(copied.getAllocatedQuantity()).isEqualTo(4);
		var copy = copied.findSnapshot(SalesOrchidSnapshotType.CREATION);
		assertThat(copy).isNotSameAs(snapshot);
		assertThat(copy.getQuantity()).isEqualTo(20);
		assertThat(copy.getCapturedAt()).isEqualTo(capturedAt);
		assertThat(copy.getCaptureSource()).isEqualTo(snapshot.getCaptureSource());
		assertThat(SalesSlipAllocationBatch.captureSnapshot(copied, SalesOrchidSnapshotType.CREATION,
				capturedAt.plusDays(1), state))
			.isSameAs(copy);
	}

	private SalesSlipItem item() {
		return new SalesSlipItem(null, "호접란", "팔레놉시스", "특품", 3, 1_000, null);
	}

	private SalesSlip slip() {
		return new SalesSlip("SNAPSHOT-SEAM", LocalDate.of(2026, 8, 11), SalesType.DIRECT, null, null, "미입금",
				SalesSlip.STATUS_DRAFT, null, null);
	}

}
