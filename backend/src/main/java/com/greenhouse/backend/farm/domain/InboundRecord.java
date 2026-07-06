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
@Table(name = "inbound_records")
public class InboundRecord extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "inbound_date", nullable = false)
	private LocalDate inboundDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "inbound_type", nullable = false, length = 50)
	private InboundType inboundType;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "variety_id", nullable = false)
	private Variety variety;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private InboundStatus status;

	@Column(name = "bottle_count")
	private Integer bottleCount;

	@Column(name = "estimated_quantity")
	private Integer estimatedQuantity;

	@Column(name = "actual_quantity")
	private Integer actualQuantity;

	@Column(name = "temp_location")
	private String tempLocation;

	@Column(name = "potting_due_date")
	private LocalDate pottingDueDate;

	@Column(name = "potting_date")
	private LocalDate pottingDate;

	@Column(name = "pot_size")
	private String potSize;

	@Column(name = "age_year")
	private Integer ageYear;

	@Column(name = "growth_stage")
	private String growthStage;

	@Column(name = "placement_type")
	private String placementType;

	@Column(name = "tray_count")
	private Integer trayCount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bed_zone_id")
	private BedZone bedZone;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_orchid_group_id")
	private OrchidGroup createdOrchidGroup;

	@Column(length = 50)
	private String worker;

	@Column(columnDefinition = "text")
	private String memo;

	protected InboundRecord() {
	}

	public InboundRecord(
		LocalDate inboundDate,
		InboundType inboundType,
		Variety variety,
		InboundStatus status,
		Integer bottleCount,
		Integer estimatedQuantity,
		Integer actualQuantity,
		String tempLocation,
		LocalDate pottingDueDate,
		String potSize,
		Integer ageYear,
		String growthStage,
		String placementType,
		Integer trayCount,
		BedZone bedZone,
		String worker,
		String memo
	) {
		this.inboundDate = inboundDate;
		this.inboundType = inboundType;
		this.variety = variety;
		this.status = status;
		this.bottleCount = bottleCount;
		this.estimatedQuantity = estimatedQuantity;
		this.actualQuantity = actualQuantity;
		this.tempLocation = tempLocation;
		this.pottingDueDate = pottingDueDate;
		this.potSize = potSize;
		this.ageYear = ageYear;
		this.growthStage = growthStage;
		this.placementType = placementType;
		this.trayCount = trayCount;
		this.bedZone = bedZone;
		this.worker = worker;
		this.memo = memo;
	}

	public void updateMetadata(
		LocalDate inboundDate,
		Integer bottleCount,
		Integer estimatedQuantity,
		Integer actualQuantity,
		String tempLocation,
		LocalDate pottingDueDate,
		String potSize,
		Integer ageYear,
		String growthStage,
		String placementType,
		Integer trayCount,
		String worker,
		String memo
	) {
		this.inboundDate = inboundDate;
		this.bottleCount = bottleCount;
		this.estimatedQuantity = estimatedQuantity;
		this.actualQuantity = actualQuantity;
		this.tempLocation = tempLocation;
		this.pottingDueDate = pottingDueDate;
		this.potSize = potSize;
		this.ageYear = ageYear;
		this.growthStage = growthStage;
		this.placementType = placementType;
		this.trayCount = trayCount;
		this.worker = worker;
		this.memo = memo;
	}

	public void place(BedZone bedZone, OrchidGroup createdOrchidGroup, LocalDate pottingDate, Integer actualQuantity) {
		this.bedZone = bedZone;
		this.createdOrchidGroup = createdOrchidGroup;
		this.pottingDate = pottingDate;
		this.actualQuantity = actualQuantity;
		this.status = InboundStatus.PLACED;
	}

	public void markPottingPending(InboundStatus status) {
		this.status = status;
	}

	public void cancel(String memo) {
		this.status = InboundStatus.CANCELED;
		if (memo != null && !memo.isBlank()) {
			this.memo = memo.trim();
		}
	}

	public Long getId() {
		return id;
	}

	public LocalDate getInboundDate() {
		return inboundDate;
	}

	public InboundType getInboundType() {
		return inboundType;
	}

	public Variety getVariety() {
		return variety;
	}

	public InboundStatus getStatus() {
		return status;
	}

	public Integer getBottleCount() {
		return bottleCount;
	}

	public Integer getEstimatedQuantity() {
		return estimatedQuantity;
	}

	public Integer getActualQuantity() {
		return actualQuantity;
	}

	public String getTempLocation() {
		return tempLocation;
	}

	public LocalDate getPottingDueDate() {
		return pottingDueDate;
	}

	public LocalDate getPottingDate() {
		return pottingDate;
	}

	public String getPotSize() {
		return potSize;
	}

	public Integer getAgeYear() {
		return ageYear;
	}

	public String getGrowthStage() {
		return growthStage;
	}

	public String getPlacementType() {
		return placementType;
	}

	public Integer getTrayCount() {
		return trayCount;
	}

	public BedZone getBedZone() {
		return bedZone;
	}

	public OrchidGroup getCreatedOrchidGroup() {
		return createdOrchidGroup;
	}

	public String getWorker() {
		return worker;
	}

	public String getMemo() {
		return memo;
	}
}
