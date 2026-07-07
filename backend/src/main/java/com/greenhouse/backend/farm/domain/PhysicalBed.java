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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "physical_beds", uniqueConstraints = @UniqueConstraint(name = "uk_physical_beds_house_number", columnNames = {
		"house_id", "number" }))
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

	@Column(name = "position_unit_count", precision = 6, scale = 2)
	private BigDecimal positionUnitCount;

	@Column(name = "position_unit_label", length = 50)
	private String positionUnitLabel;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "physicalBed", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<BedZone> bedZones = new ArrayList<>();

	public PhysicalBed(Integer number, Integer displayOrder) {
		this.number = number;
		this.displayOrder = displayOrder;
	}

	public void updatePositionUnits(BigDecimal positionUnitCount, String positionUnitLabel) {
		this.positionUnitCount = positionUnitCount;
		this.positionUnitLabel = positionUnitLabel;
	}

	void setHouse(House house) {
		this.house = house;
	}

	public void addBedZone(BedZone bedZone) {
		this.bedZones.add(bedZone);
		bedZone.setPhysicalBed(this);
	}
}
