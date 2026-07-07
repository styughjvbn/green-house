package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "bed_zone_segments")
public class BedZoneSegment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bed_zone_id", nullable = false)
	private BedZone bedZone;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "segment_type", nullable = false, length = 20)
	private BedZoneSegmentType segmentType;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "start_position", precision = 6, scale = 2)
	private BigDecimal startPosition;

	@Column(name = "end_position", precision = 6, scale = 2)
	private BigDecimal endPosition;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("capacityMode ASC")
	private List<BedZoneSegmentCapacity> capacities = new ArrayList<>();

	public BedZoneSegment(String name, BedZoneSegmentType segmentType, Integer sortOrder, BigDecimal startPosition,
			BigDecimal endPosition, String memo) {
		this.name = name;
		this.segmentType = segmentType;
		this.sortOrder = sortOrder;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.memo = memo;
	}

	void setBedZone(BedZone bedZone) {
		this.bedZone = bedZone;
	}

	public void update(String name, BedZoneSegmentType segmentType, Integer sortOrder, BigDecimal startPosition,
			BigDecimal endPosition, String memo) {
		this.name = name;
		this.segmentType = segmentType;
		this.sortOrder = sortOrder;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.memo = memo;
	}

	public void replaceCapacities(List<BedZoneSegmentCapacity> nextCapacities) {
		capacities.clear();
		nextCapacities.forEach(this::addCapacity);
	}

	public void addCapacity(BedZoneSegmentCapacity capacity) {
		capacities.add(capacity);
		capacity.setSegment(this);
	}
}
