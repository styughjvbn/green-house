package com.greenhouse.backend.farm.domain.inbound;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.variety.Variety;
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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "inbound_records")
public class InboundRecord extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inbound_records_id_seq")
	@SequenceGenerator(name = "inbound_records_id_seq", sequenceName = "inbound_records_id_seq", allocationSize = 50)
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

	@Column(name = "estimated_quantity")
	private Integer estimatedQuantity;

	@Column(name = "temp_location")
	private String tempLocation;

	@Column(name = "potting_due_date")
	private LocalDate pottingDueDate;

	@OneToMany(mappedBy = "inboundRecord")
	private List<OrchidGroup> createdOrchidGroups = new ArrayList<>();

	@Column(length = 50)
	private String worker;

	@Column(columnDefinition = "text")
	private String memo;

	public InboundRecord(LocalDate inboundDate, InboundType inboundType, Variety variety, InboundStatus status,
			Integer estimatedQuantity, String tempLocation, LocalDate pottingDueDate, String worker, String memo) {
		this.inboundDate = inboundDate;
		this.inboundType = inboundType;
		this.variety = variety;
		this.status = status;
		this.estimatedQuantity = estimatedQuantity;
		this.tempLocation = tempLocation;
		this.pottingDueDate = pottingDueDate;
		this.worker = worker;
		this.memo = memo;
	}

	public void updateMetadata(LocalDate inboundDate, Integer estimatedQuantity, String tempLocation,
			LocalDate pottingDueDate, String worker, String memo) {
		if (status == InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록은 수정할 수 없습니다.");
		}
		this.inboundDate = inboundDate;
		this.estimatedQuantity = estimatedQuantity;
		this.tempLocation = tempLocation;
		this.pottingDueDate = pottingDueDate;
		this.worker = worker;
		this.memo = memo;
	}

	public void markPlaced() {
		this.status = InboundStatus.PLACED;
	}

	public void addCreatedOrchidGroup(OrchidGroup orchidGroup) {
		if (!createdOrchidGroups.contains(orchidGroup)) {
			createdOrchidGroups.add(orchidGroup);
		}
	}

	public boolean hasCreatedOrchidGroups() {
		return !createdOrchidGroups.isEmpty();
	}

	public void markPottingPending(InboundStatus status) {
		this.status = status;
	}

	public void markPottingPlanned() {
		if (inboundType != InboundType.FLASK_SEEDLING || status == InboundStatus.CANCELED || hasCreatedOrchidGroups()) {
			throw new IllegalStateException("포트 작업을 계획할 수 없는 입고 기록입니다.");
		}
		this.status = InboundStatus.POTTING_IN_PROGRESS;
	}

	public void closePottingPlan() {
		if (status != InboundStatus.POTTING_IN_PROGRESS) {
			return;
		}
		this.status = InboundStatus.POTTING_PENDING;
	}

	public void cancel(String memo) {
		requireCancellable();
		this.status = InboundStatus.CANCELED;
		if (memo != null && !memo.isBlank()) {
			this.memo = memo.trim();
		}
	}

	public void requireCancellable() {
		if (hasCreatedOrchidGroups()) {
			throw new IllegalArgumentException("난 묶음이 생성된 입고 기록은 취소할 수 없습니다.");
		}
	}

	public void requireDeletable() {
		if (status != InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록만 삭제할 수 있습니다.");
		}
		if (hasCreatedOrchidGroups()) {
			throw new IllegalArgumentException("난 묶음이 생성된 입고 기록은 삭제할 수 없습니다.");
		}
	}

	public void requirePottingAllowed() {
		if (inboundType != InboundType.FLASK_SEEDLING) {
			throw new IllegalArgumentException("유리병 모종 입고만 포트 작업을 등록할 수 있습니다.");
		}
		if (status == InboundStatus.CANCELED) {
			throw new IllegalArgumentException("취소된 입고 기록은 포트 작업을 등록할 수 없습니다.");
		}
		if (hasCreatedOrchidGroups()) {
			throw new IllegalArgumentException("이미 난 묶음이 생성된 입고 기록입니다.");
		}
	}

}
