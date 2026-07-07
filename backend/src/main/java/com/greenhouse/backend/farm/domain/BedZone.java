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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "bed_zones")
public class BedZone extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "physical_bed_id", nullable = false)
	private PhysicalBed physicalBed;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BedZoneSide side;

	@Enumerated(EnumType.STRING)
	@Column(name = "zone_type", nullable = false)
	private BedZoneType zoneType;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "is_active", nullable = false)
	private Boolean active;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "bedZone")
	@OrderBy("sortOrder ASC")
	private List<OrchidGroup> orchidGroups = new ArrayList<>();

	@OneToMany(mappedBy = "bedZone", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
	@OrderBy("capacityMode ASC")
	private Set<BedZoneCapacity> capacities = new LinkedHashSet<>();

	public BedZone(String name, BedZoneSide side, Integer sortOrder) {
		this.name = name;
		this.side = side;
		this.zoneType = BedZoneType.DEFAULT;
		this.sortOrder = sortOrder;
		this.active = true;
	}

	void setPhysicalBed(PhysicalBed physicalBed) {
		this.physicalBed = physicalBed;
	}

	public void replaceCapacities(List<BedZoneCapacity> nextCapacities) {
		capacities.clear();
		nextCapacities.forEach(this::addCapacity);
	}

	public void addCapacity(BedZoneCapacity capacity) {
		capacities.add(capacity);
		capacity.setBedZone(this);
	}
}
