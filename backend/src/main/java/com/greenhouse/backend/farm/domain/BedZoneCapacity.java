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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "bed_zone_capacities")
public class BedZoneCapacity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bed_zone_id", nullable = false)
	private BedZone bedZone;

	@Column(name = "placement_type", nullable = false, length = 100)
	private String placementType;

	@Column(name = "pot_size", length = 50)
	private String potSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "capacity_mode", nullable = false, length = 20)
	private PlacementCapacityMode capacityMode;

	@Column(name = "unit_span", precision = 6, scale = 2, nullable = false)
	private BigDecimal unitSpan;

	@Column(name = "capacity_value", nullable = false)
	private Integer capacityValue;

	@Column(name = "is_allowed", nullable = false)
	private Boolean allowed;

	@Column(columnDefinition = "text")
	private String memo;

	public BedZoneCapacity(
			String placementType,
			String potSize,
			PlacementCapacityMode capacityMode,
			BigDecimal unitSpan,
			Integer capacityValue,
			Boolean allowed,
			String memo) {
		this.placementType = placementType;
		this.potSize = PotSizeCode.fromInput(potSize).getDisplayValue();
		this.capacityMode = capacityMode;
		this.unitSpan = unitSpan;
		this.capacityValue = capacityValue;
		this.allowed = allowed;
		this.memo = memo;
	}

	void setBedZone(BedZone bedZone) {
		this.bedZone = bedZone;
	}
}
