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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

	@OneToMany(mappedBy = "inboundRecord")
	private List<OrchidGroup> createdOrchidGroups = new ArrayList<>();

	@Column(length = 50)
	private String worker;

	@Column(columnDefinition = "text")
	private String memo;

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
			String memo) {
		this.inboundDate = inboundDate;
		this.inboundType = inboundType;
		this.variety = variety;
		this.status = status;
		this.bottleCount = bottleCount;
		this.estimatedQuantity = estimatedQuantity;
		this.actualQuantity = actualQuantity;
		this.tempLocation = tempLocation;
		this.pottingDueDate = pottingDueDate;
		this.potSize = PotSizeCode.fromInput(potSize).getDisplayValue();
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
			String memo) {
		this.inboundDate = inboundDate;
		this.bottleCount = bottleCount;
		this.estimatedQuantity = estimatedQuantity;
		this.actualQuantity = actualQuantity;
		this.tempLocation = tempLocation;
		this.pottingDueDate = pottingDueDate;
		this.potSize = PotSizeCode.fromInput(potSize).getDisplayValue();
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

	public void addCreatedOrchidGroup(OrchidGroup orchidGroup) {
		if (!createdOrchidGroups.contains(orchidGroup)) {
			createdOrchidGroups.add(orchidGroup);
		}
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
}
