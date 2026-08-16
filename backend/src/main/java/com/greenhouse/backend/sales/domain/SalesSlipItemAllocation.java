package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_slip_item_allocations")
public class SalesSlipItemAllocation extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_slip_item_allocations_id_seq")
	@SequenceGenerator(name = "sales_slip_item_allocations_id_seq", sequenceName = "sales_slip_item_allocations_id_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_slip_item_id", nullable = false)
	private SalesSlipItem salesSlipItem;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "orchid_group_id", nullable = false)
	private OrchidGroup orchidGroup;

	private Integer allocatedQuantity;

	@OneToMany(mappedBy = "allocation", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("snapshotType ASC")
	private List<SalesOrchidGroupSnapshot> snapshots = new ArrayList<>();

	public SalesSlipItemAllocation(OrchidGroup orchidGroup, Integer allocatedQuantity) {
		this.orchidGroup = orchidGroup;
		this.allocatedQuantity = allocatedQuantity;
	}

	void setSalesSlipItem(SalesSlipItem salesSlipItem) {
		this.salesSlipItem = salesSlipItem;
	}

	public SalesOrchidGroupSnapshot captureSnapshot(
			SalesOrchidSnapshotType snapshotType,
			LocalDateTime capturedAt) {
		return snapshots.stream()
				.filter(snapshot -> snapshot.getSnapshotType() == snapshotType)
				.findFirst()
				.orElseGet(() -> addSnapshot(new SalesOrchidGroupSnapshot(
						snapshotType,
						SalesOrchidSnapshotSource.LIVE,
						capturedAt,
						allocatedQuantity,
						orchidGroup)));
	}

	public SalesOrchidGroupSnapshot findSnapshot(SalesOrchidSnapshotType snapshotType) {
		return snapshots.stream()
				.filter(snapshot -> snapshot.getSnapshotType() == snapshotType)
				.findFirst()
				.orElse(null);
	}

	public SalesSlipItemAllocation copy() {
		SalesSlipItemAllocation copy = new SalesSlipItemAllocation(orchidGroup, allocatedQuantity);
		snapshots.stream().map(SalesOrchidGroupSnapshot::copy).forEach(copy::addSnapshot);
		return copy;
	}

	private SalesOrchidGroupSnapshot addSnapshot(SalesOrchidGroupSnapshot snapshot) {
		snapshot.setAllocation(this);
		snapshots.add(snapshot);
		return snapshot;
	}
}
