package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orchid_groups")
public class OrchidGroup extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bed_zone_id", nullable = false)
	private BedZone bedZone;

	private String genus;

	@Column(name = "variety_name", nullable = false)
	private String varietyName;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "pot_size")
	private String potSize;

	@Column(name = "age_year")
	private Integer ageYear;

	@Column(nullable = false)
	private String status;

	@Column(name = "placement_type")
	private String placementType;

	@Column(name = "tray_count")
	private Integer trayCount;

	@Column(name = "split_placement_allowed")
	private Boolean splitPlacementAllowed;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "orchidGroup", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<OrchidGroupSegmentPlacement> segmentPlacements = new ArrayList<>();

	protected OrchidGroup() {
	}

	public OrchidGroup(
		BedZone bedZone,
		String genus,
		String varietyName,
		Integer quantity,
		String potSize,
		Integer ageYear,
		String status,
		Integer sortOrder
	) {
		this.bedZone = bedZone;
		this.genus = genus;
		this.varietyName = varietyName;
		this.quantity = quantity;
		this.potSize = potSize;
		this.ageYear = ageYear;
		this.status = status;
		this.sortOrder = sortOrder;
		this.splitPlacementAllowed = false;
	}

	public void updateDetails(
		String genus,
		String varietyName,
		Integer quantity,
		String potSize,
		Integer ageYear,
		String status,
		String placementType,
		Integer trayCount,
		Boolean splitPlacementAllowed,
		String memo
	) {
		this.genus = genus;
		this.varietyName = varietyName;
		this.quantity = quantity;
		this.potSize = potSize;
		this.ageYear = ageYear;
		this.status = status;
		this.placementType = placementType;
		this.trayCount = trayCount;
		this.splitPlacementAllowed = Boolean.TRUE.equals(splitPlacementAllowed);
		this.memo = memo;
	}

	public void moveTo(BedZone bedZone, Integer sortOrder) {
		this.bedZone = bedZone;
		this.sortOrder = sortOrder;
	}

	public Long getId() {
		return id;
	}

	public BedZone getBedZone() {
		return bedZone;
	}

	public String getGenus() {
		return genus;
	}

	public String getVarietyName() {
		return varietyName;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public String getPotSize() {
		return potSize;
	}

	public Integer getAgeYear() {
		return ageYear;
	}

	public String getStatus() {
		return status;
	}

	public String getPlacementType() {
		return placementType;
	}

	public Integer getTrayCount() {
		return trayCount;
	}

	public Boolean getSplitPlacementAllowed() { return Boolean.TRUE.equals(splitPlacementAllowed); }

	public void replaceSegmentPlacements(List<OrchidGroupSegmentPlacement> placements) {
		segmentPlacements.clear();
		placements.forEach(placement -> {
			segmentPlacements.add(placement);
			placement.setOrchidGroup(this);
		});
	}

	public List<OrchidGroupSegmentPlacement> getSegmentPlacements() { return segmentPlacements; }

	public Integer getSortOrder() {
		return sortOrder;
	}

	public String getMemo() {
		return memo;
	}
}
