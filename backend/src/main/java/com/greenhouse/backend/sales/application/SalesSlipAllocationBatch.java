package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import java.util.List;
import java.util.Objects;

/**
 * A stable view of the allocation lines participating in one sales use case.
 * Future creation and outbound snapshots can be captured from the same lines.
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
