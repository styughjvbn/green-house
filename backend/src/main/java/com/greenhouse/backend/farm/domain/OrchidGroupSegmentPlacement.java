package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
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
import java.time.LocalDate;

@Entity
@Table(name = "orchid_group_segment_placements")
public class OrchidGroupSegmentPlacement extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "orchid_group_id", nullable = false)
	private OrchidGroup orchidGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bed_zone_segment_id", nullable = false)
	private BedZoneSegment segment;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "tray_count")
	private Integer trayCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "placement_mode", nullable = false, length = 20)
	private PlacementCapacityMode placementMode;

	@Column(name = "reorganize_due_date")
	private LocalDate reorganizeDueDate;

	@Column(columnDefinition = "text")
	private String memo;

	protected OrchidGroupSegmentPlacement() {
	}

	public OrchidGroupSegmentPlacement(BedZoneSegment segment, Integer quantity, Integer trayCount, PlacementCapacityMode placementMode, LocalDate reorganizeDueDate, String memo) {
		this.segment = segment;
		this.quantity = quantity;
		this.trayCount = trayCount;
		this.placementMode = placementMode;
		this.reorganizeDueDate = reorganizeDueDate;
		this.memo = memo;
	}

	void setOrchidGroup(OrchidGroup orchidGroup) { this.orchidGroup = orchidGroup; }
	public Long getId() { return id; }
	public OrchidGroup getOrchidGroup() { return orchidGroup; }
	public BedZoneSegment getSegment() { return segment; }
	public Integer getQuantity() { return quantity; }
	public Integer getTrayCount() { return trayCount; }
	public PlacementCapacityMode getPlacementMode() { return placementMode; }
	public LocalDate getReorganizeDueDate() { return reorganizeDueDate; }
	public String getMemo() { return memo; }
}
