package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.domain.SalesOrchidGroupSnapshot;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotSource;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A stable view of the allocation lines participating in one sales use case. Creation and
 * outbound snapshots are captured from these same allocation lines.
 */
public record SalesSlipAllocationBatch(SalesSlip salesSlip, List<Line> lines) {

	public SalesSlipAllocationBatch {
		Objects.requireNonNull(salesSlip, "salesSlip");
		lines = List.copyOf(lines);
	}

	public static SalesSlipAllocationBatch from(SalesSlip salesSlip) {
		List<Line> lines = salesSlip.getItems()
			.stream()
			.flatMap(item -> item.getAllocations().stream().map(allocation -> new Line(item, allocation)))
			.toList();
		return new SalesSlipAllocationBatch(salesSlip, lines);
	}

	public List<Long> orchidGroupIds() {
		return lines.stream().map(line -> line.orchidGroupId()).distinct().sorted().toList();
	}

	public void captureSnapshot(SalesOrchidSnapshotType snapshotType, LocalDateTime capturedAt,
			Map<Long, OrchidGroupState> states) {
		lines.forEach(
				line -> captureSnapshot(line.allocation(), snapshotType, capturedAt, states.get(line.orchidGroupId())));
	}

	static SalesOrchidGroupSnapshot captureSnapshot(SalesSlipItemAllocation allocation, SalesOrchidSnapshotType type,
			LocalDateTime capturedAt, OrchidGroupState state) {
		return allocation.captureSnapshot(SalesOrchidGroupSnapshot.builder()
			.snapshotType(type)
			.captureSource(SalesOrchidSnapshotSource.LIVE)
			.capturedAt(capturedAt)
			.orchidGroupId(state.id())
			.allocatedQuantity(allocation.getAllocatedQuantity())
			.varietyId(state.varietyId())
			.varietyName(state.varietyName())
			.genus(state.genus())
			.ageYear(state.ageYear())
			.potSizeCode(state.potSizeCode())
			.potSize(state.potSize())
			.quantity(state.quantity())
			.reservedQuantity(state.reservedQuantity())
			.status(state.status())
			.houseId(state.houseId())
			.houseNumber(state.houseNumber())
			.physicalBedId(state.physicalBedId())
			.physicalBedNumber(state.physicalBedNumber())
			.bedZoneId(state.bedZoneId())
			.bedZoneName(state.bedZoneName())
			.startPosition(state.startPosition())
			.endPosition(state.endPosition())
			.build());
	}

	public record Line(SalesSlipItem item, SalesSlipItemAllocation allocation) {

		public Line {
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(allocation, "allocation");
		}

		public Long orchidGroupId() {
			return allocation.getOrchidGroupId();
		}

		public Integer allocatedQuantity() {
			return allocation.getAllocatedQuantity();
		}
	}
}
