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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "bed_zone_segment_capacities")
public class BedZoneSegmentCapacity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bed_zone_segment_id", nullable = false)
	private BedZoneSegment segment;

	@Column(name = "placement_type", nullable = false, length = 100)
	private String placementType;

	@Column(name = "pot_size", length = 50)
	private String potSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "capacity_mode", nullable = false, length = 20)
	private PlacementCapacityMode capacityMode;

	@Column(name = "capacity_value", nullable = false)
	private Integer capacityValue;

	@Column(name = "is_allowed", nullable = false)
	private Boolean allowed;

	@Column(columnDefinition = "text")
	private String memo;

	public BedZoneSegmentCapacity(String placementType, String potSize, PlacementCapacityMode capacityMode,
			Integer capacityValue, Boolean allowed, String memo) {
		this.placementType = placementType;
		this.potSize = potSize;
		this.capacityMode = capacityMode;
		this.capacityValue = capacityValue;
		this.allowed = allowed;
		this.memo = memo;
	}

	void setSegment(BedZoneSegment segment) {
		this.segment = segment;
	}
}
