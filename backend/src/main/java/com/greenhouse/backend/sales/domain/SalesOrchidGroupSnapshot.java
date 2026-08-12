package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "sales_orchid_group_snapshots",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_sales_orchid_snapshot_allocation_type",
				columnNames = { "sales_slip_item_allocation_id", "snapshot_type" }))
public class SalesOrchidGroupSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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

	SalesOrchidGroupSnapshot(
			SalesOrchidSnapshotType snapshotType,
			SalesOrchidSnapshotSource captureSource,
			LocalDateTime capturedAt,
			Integer allocatedQuantity,
			OrchidGroup group) {
		var zone = group.getBedZone();
		var bed = zone.getPhysicalBed();
		var house = bed.getHouse();
		this.snapshotType = snapshotType;
		this.captureSource = captureSource;
		this.capturedAt = capturedAt;
		this.orchidGroupId = group.getId();
		this.varietyId = group.getVariety() == null ? null : group.getVariety().getId();
		this.varietyName = group.getVarietyName();
		this.genus = group.getGenus();
		this.ageYear = group.getAgeYear();
		this.potSizeCode = group.getPotSizeCode() == null ? null : group.getPotSizeCode().name();
		this.potSize = group.getPotSize();
		this.quantity = group.getQuantity();
		this.reservedQuantity = group.getReservedQuantity();
		this.status = group.getStatus();
		this.allocatedQuantity = allocatedQuantity;
		this.houseId = house.getId();
		this.houseNumber = house.getNumber();
		this.physicalBedId = bed.getId();
		this.physicalBedNumber = bed.getNumber();
		this.bedZoneId = zone.getId();
		this.bedZoneName = zone.getName();
		this.startPosition = group.getStartPosition();
		this.endPosition = group.getEndPosition();
	}

	private SalesOrchidGroupSnapshot(SalesOrchidGroupSnapshot source) {
		this.snapshotType = source.snapshotType;
		this.captureSource = source.captureSource;
		this.capturedAt = source.capturedAt;
		this.orchidGroupId = source.orchidGroupId;
		this.varietyId = source.varietyId;
		this.varietyName = source.varietyName;
		this.genus = source.genus;
		this.ageYear = source.ageYear;
		this.potSizeCode = source.potSizeCode;
		this.potSize = source.potSize;
		this.quantity = source.quantity;
		this.reservedQuantity = source.reservedQuantity;
		this.status = source.status;
		this.allocatedQuantity = source.allocatedQuantity;
		this.houseId = source.houseId;
		this.houseNumber = source.houseNumber;
		this.physicalBedId = source.physicalBedId;
		this.physicalBedNumber = source.physicalBedNumber;
		this.bedZoneId = source.bedZoneId;
		this.bedZoneName = source.bedZoneName;
		this.startPosition = source.startPosition;
		this.endPosition = source.endPosition;
	}

	void setAllocation(SalesSlipItemAllocation allocation) {
		this.allocation = allocation;
	}

	SalesOrchidGroupSnapshot copy() {
		return new SalesOrchidGroupSnapshot(this);
	}
}
