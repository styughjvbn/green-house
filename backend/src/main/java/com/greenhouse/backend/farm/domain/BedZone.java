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
import java.util.ArrayList;
import java.util.List;

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

	protected BedZone() {
	}

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

	public Long getId() {
		return id;
	}

	public PhysicalBed getPhysicalBed() {
		return physicalBed;
	}

	public String getName() {
		return name;
	}

	public BedZoneSide getSide() {
		return side;
	}

	public BedZoneType getZoneType() {
		return zoneType;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public Boolean getActive() {
		return active;
	}

	public String getMemo() {
		return memo;
	}

	public List<OrchidGroup> getOrchidGroups() {
		return orchidGroups;
	}
}
