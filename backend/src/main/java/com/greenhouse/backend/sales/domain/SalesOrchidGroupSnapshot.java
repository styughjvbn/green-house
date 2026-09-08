package com.greenhouse.backend.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_orchid_group_snapshots",
		uniqueConstraints = @UniqueConstraint(name = "uk_sales_orchid_snapshot_allocation_type",
				columnNames = { "sales_slip_item_allocation_id", "snapshot_type" }))
public class SalesOrchidGroupSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_orchid_group_snapshots_id_seq")
	@SequenceGenerator(name = "sales_orchid_group_snapshots_id_seq",
			sequenceName = "sales_orchid_group_snapshots_id_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_slip_item_allocation_id", nullable = false)
	private SalesSlipItemAllocation allocation;

	@Enumerated(EnumType.STRING)
	@Column(name = "snapshot_type", nullable = false, length = 20)
	private SalesOrchidSnapshotType snapshotType;

	@Enumerated(EnumType.STRING)
	@Column(name = "capture_source", nullable = false, length = 30)
	private SalesOrchidSnapshotSource captureSource;

	@Column(name = "captured_at", nullable = false)
	private LocalDateTime capturedAt;

	@Column(name = "orchid_group_id", nullable = false)
	private Long orchidGroupId;

	@Column(name = "variety_id")
	private Long varietyId;

	@Column(name = "variety_name", nullable = false, length = 150)
	private String varietyName;

	@Column(length = 100)
	private String genus;

	@Column(name = "age_year")
	private Integer ageYear;

	@Column(name = "pot_size_code", length = 30)
	private String potSizeCode;

	@Column(name = "pot_size", length = 50)
	private String potSize;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "reserved_quantity", nullable = false)
	private Integer reservedQuantity;

	@Column(nullable = false, length = 50)
	private String status;

	@Column(name = "allocated_quantity", nullable = false)
	private Integer allocatedQuantity;

	@Column(name = "house_id", nullable = false)
	private Long houseId;

	@Column(name = "house_number", nullable = false)
	private Integer houseNumber;

	@Column(name = "physical_bed_id", nullable = false)
	private Long physicalBedId;

	@Column(name = "physical_bed_number", nullable = false)
	private Integer physicalBedNumber;

	@Column(name = "bed_zone_id", nullable = false)
	private Long bedZoneId;

	@Column(name = "bed_zone_name", nullable = false, length = 100)
	private String bedZoneName;

	@Column(name = "start_position", precision = 6, scale = 2)
	private BigDecimal startPosition;

	@Column(name = "end_position", precision = 6, scale = 2)
	private BigDecimal endPosition;

	@Builder(toBuilder = true)
	private SalesOrchidGroupSnapshot(SalesOrchidSnapshotType snapshotType, SalesOrchidSnapshotSource captureSource,
			LocalDateTime capturedAt, Long orchidGroupId, Long varietyId, String varietyName, String genus,
			Integer ageYear, String potSizeCode, String potSize, Integer quantity, Integer reservedQuantity,
			String status, Integer allocatedQuantity, Long houseId, Integer houseNumber, Long physicalBedId,
			Integer physicalBedNumber, Long bedZoneId, String bedZoneName, BigDecimal startPosition,
			BigDecimal endPosition) {
		this.snapshotType = snapshotType;
		this.captureSource = captureSource;
		this.capturedAt = capturedAt;
		this.orchidGroupId = orchidGroupId;
		this.varietyId = varietyId;
		this.varietyName = varietyName;
		this.genus = genus;
		this.ageYear = ageYear;
		this.potSizeCode = potSizeCode;
		this.potSize = potSize;
		this.quantity = quantity;
		this.reservedQuantity = reservedQuantity;
		this.status = status;
		this.allocatedQuantity = allocatedQuantity;
		this.houseId = houseId;
		this.houseNumber = houseNumber;
		this.physicalBedId = physicalBedId;
		this.physicalBedNumber = physicalBedNumber;
		this.bedZoneId = bedZoneId;
		this.bedZoneName = bedZoneName;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
	}

	void setAllocation(SalesSlipItemAllocation allocation) {
		this.allocation = allocation;
	}

	SalesOrchidGroupSnapshot copy() {
		return toBuilder().build();
	}

}
