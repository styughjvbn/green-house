package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A stable view of the allocation lines participating in one sales use case.
 * Creation and outbound snapshots are captured from these same allocation lines.
 */
public record SalesSlipAllocationBatch(
		SalesSlip salesSlip,
		List<Line> lines) {

	public SalesSlipAllocationBatch {
		Objects.requireNonNull(salesSlip, "salesSlip");
		lines = List.copyOf(lines);
	}

	public static SalesSlipAllocationBatch from(SalesSlip salesSlip) {
		List<Line> lines = salesSlip.getItems().stream()
				.flatMap(item -> item.getAllocations().stream()
						.map(allocation -> new Line(item, allocation)))
				.toList();
		return new SalesSlipAllocationBatch(salesSlip, lines);
	}

	public List<Long> orchidGroupIds() {
		return lines.stream()
				.map(line -> line.orchidGroup().getId())
				.distinct()
				.sorted()
				.toList();
	}

	public void captureSnapshot(SalesOrchidSnapshotType snapshotType, LocalDateTime capturedAt) {
		lines.forEach(line -> line.allocation().captureSnapshot(snapshotType, capturedAt));
	}

	public record Line(
			SalesSlipItem item,
			SalesSlipItemAllocation allocation) {

		public Line {
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(allocation, "allocation");
		}

		public OrchidGroup orchidGroup() {
			return allocation.getOrchidGroup();
		}

		public Integer allocatedQuantity() {
			return allocation.getAllocatedQuantity();
		}
	}
}
