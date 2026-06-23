package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
	name = "physical_beds",
	uniqueConstraints = @UniqueConstraint(name = "uk_physical_beds_house_number", columnNames = {"house_id", "number"})
)
public class PhysicalBed extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "house_id", nullable = false)
	private House house;

	@Column(nullable = false)
	private Integer number;

	@Column(name = "display_order", nullable = false)
	private Integer displayOrder;

	@Column(name = "length_cm")
	private Integer lengthCm;

	@Column(name = "width_cm")
	private Integer widthCm;

	@Column(name = "wire_count")
	private Integer wireCount;

	@Column(name = "support_interval_cm")
	private Integer supportIntervalCm;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "physicalBed", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<BedZone> bedZones = new ArrayList<>();

	protected PhysicalBed() {
	}

	public PhysicalBed(Integer number, Integer displayOrder) {
		this.number = number;
		this.displayOrder = displayOrder;
	}

	void setHouse(House house) {
		this.house = house;
	}

	public void addBedZone(BedZone bedZone) {
		this.bedZones.add(bedZone);
		bedZone.setPhysicalBed(this);
	}

	public Long getId() {
		return id;
	}

	public House getHouse() {
		return house;
	}

	public Integer getNumber() {
		return number;
	}

	public Integer getDisplayOrder() {
		return displayOrder;
	}

	public Integer getLengthCm() {
		return lengthCm;
	}

	public Integer getWidthCm() {
		return widthCm;
	}

	public Integer getWireCount() {
		return wireCount;
	}

	public Integer getSupportIntervalCm() {
		return supportIntervalCm;
	}

	public String getMemo() {
		return memo;
	}

	public List<BedZone> getBedZones() {
		return bedZones;
	}
}
