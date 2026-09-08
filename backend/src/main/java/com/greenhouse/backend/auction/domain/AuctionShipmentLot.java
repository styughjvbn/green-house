package com.greenhouse.backend.auction.domain;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auction_shipment_lots")
public class AuctionShipmentLot extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auction_shipment_lots_id_seq")
	@SequenceGenerator(name = "auction_shipment_lots_id_seq", sequenceName = "auction_shipment_lots_id_seq", allocationSize = 50)
	private Long id;

	@Version
	@Column(nullable = false)
	private Long version;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shipment_id", nullable = false)
	private AuctionShipment shipment;

	@Column(name = "item_name", nullable = false)
	private String itemName;

	@Column(name = "variety_name", nullable = false)
	private String varietyName;

	@Column(name = "shipment_grade")
	private String shipmentGrade;

	@Column
	private Integer boxes;

	@Column(name = "shipped_quantity", nullable = false)
	private Integer shippedQuantity;

	@Column(name = "sold_quantity", nullable = false)
	private Integer soldQuantity;

	@Column(name = "waiting_quantity", nullable = false)
	private Integer waitingQuantity;

	@Column(name = "returned_quantity", nullable = false)
	private Integer returnedQuantity;

	@Column(name = "return_confirmed_date")
	private LocalDate returnConfirmedDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "current_status", nullable = false)
	private AuctionLotStatus currentStatus;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "shipmentLot", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("auctionDate ASC, attemptNo ASC")
	private List<AuctionAttempt> attempts = new ArrayList<>();

	@OneToMany(mappedBy = "shipmentLot", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("changedAt ASC")
	private List<AuctionLotStatusHistory> statusHistory = new ArrayList<>();

	public AuctionShipmentLot(String itemName, String varietyName, String grade, Integer boxes, Integer quantity) {
		this.itemName = itemName;
		this.varietyName = varietyName;
		this.shipmentGrade = grade;
		this.boxes = boxes;
		this.shippedQuantity = quantity;
		this.soldQuantity = 0;
		this.waitingQuantity = quantity;
		this.returnedQuantity = 0;
		this.currentStatus = AuctionLotStatus.WAITING;
	}

	void setShipment(AuctionShipment shipment) {
		this.shipment = shipment;
	}

	public void addAttempt(AuctionAttempt attempt) {
		attempts.add(attempt);
		attempt.setShipmentLot(this);
	}

	public void applyResult(Integer sold, Integer returned, boolean failed, boolean returnInferred) {
		soldQuantity += sold;
		returnedQuantity += returned;
		waitingQuantity = Math.max(0, shippedQuantity - soldQuantity - returnedQuantity);
		AuctionLotStatus next;
		if (soldQuantity + returnedQuantity > shippedQuantity) {
			next = AuctionLotStatus.QUANTITY_MISMATCH;
		} else if (returnInferred) {
			next = AuctionLotStatus.RETURN_INFERRED;
		} else if (returnedQuantity > 0 && waitingQuantity == 0) {
			next = AuctionLotStatus.RETURNED;
		} else if (soldQuantity == shippedQuantity) {
			next = AuctionLotStatus.SOLD;
		} else if (soldQuantity > 0) {
			next = AuctionLotStatus.PARTIALLY_SOLD;
		} else if (failed) {
			next = AuctionLotStatus.REAUCTION_WAITING;
		} else {
			next = AuctionLotStatus.IN_PROGRESS;
		}
		changeStatus(next, "경매 결과 반영", null, null);
	}

	public void recordResult(LocalDate auctionDate, Integer requestedAttemptNo, AuctionAttemptStatus attemptStatus,
			List<AuctionResultLineInput> resultLines, String requestedFailedReason, String requestedMemo) {
		if (getWaitingQuantity() <= 0)
			throw new IllegalArgumentException("대기 수량이 없는 lot에는 경매 결과를 추가할 수 없습니다.");
		int waitingQuantity = getWaitingQuantity();
		int attemptNo = resolveAttemptNo(requestedAttemptNo);
		validateAttemptNo(auctionDate, attemptNo);

		String failedReason = normalize(requestedFailedReason);
		String memo = normalize(requestedMemo);
		var attempt = new AuctionAttempt(
				auctionDate,
				attemptNo,
				attemptStatus,
				failedReason,
				memo);

		switch (attemptStatus) {
			case SOLD -> {
				int soldQuantity = addSoldLines(attempt, resultLines, getShipmentGrade());
				if (soldQuantity != waitingQuantity)
					throw new IllegalArgumentException("낙찰 상태에서는 남은 대기 수량 전체를 입력해야 합니다.");
				attempt.recalculateStatus();
				addAttempt(attempt);
				applyResult(soldQuantity, 0, false, false);
			}
			case PARTIALLY_SOLD -> {
				int soldQuantity = addSoldLines(attempt, resultLines, getShipmentGrade());
				if (soldQuantity >= waitingQuantity)
					throw new IllegalArgumentException("부분 낙찰은 대기 수량보다 적어야 합니다.");
				attempt.addResultLine(new AuctionResultLine(
						auctionDate,
						getShipmentGrade(),
						waitingQuantity - soldQuantity,
						0,
						0,
						failedReason == null ? "잔량 유찰" : failedReason,
						AuctionInspectionStatus.NORMAL));
				attempt.recalculateStatus();
				addAttempt(attempt);
				applyResult(soldQuantity, 0, false, false);
			}
			case FAILED -> {
				attempt.addResultLine(new AuctionResultLine(
						auctionDate,
						getShipmentGrade(),
						waitingQuantity,
						0,
						0,
						failedReason == null ? "유찰" : failedReason,
						AuctionInspectionStatus.NORMAL));
				attempt.recalculateStatus();
				addAttempt(attempt);
				applyResult(0, 0, true, false);
			}
			case RETURN_INFERRED -> {
				attempt.addResultLine(new AuctionResultLine(
						auctionDate,
						getShipmentGrade(),
						waitingQuantity,
						0,
						0,
						failedReason == null ? "반환 추정" : failedReason,
						AuctionInspectionStatus.RETURN_INFERRED));
				attempt.recalculateStatus();
				addAttempt(attempt);
				applyResult(0, waitingQuantity, false, true);
			}
			default -> throw new IllegalArgumentException("지원하지 않는 경매 결과 상태입니다.");
		}

	}

	private int resolveAttemptNo(Integer attemptNo) {
		if (attemptNo != null)
			return attemptNo;
		return getAttempts().stream().map(AuctionAttempt::getAttemptNo).max(Integer::compareTo).orElse(0) + 1;
	}

	private void validateAttemptNo(LocalDate auctionDate,
			int attemptNo) {
		boolean duplicate = getAttempts().stream()
				.anyMatch(attempt -> Objects.equals(attempt.getAttemptNo(), attemptNo)
						&& Objects.equals(attempt.getAuctionDate(), auctionDate));
		if (duplicate)
			throw new IllegalArgumentException("같은 경매일과 차수의 결과가 이미 등록되어 있습니다.");
	}

	private int addSoldLines(AuctionAttempt attempt, List<AuctionResultLineInput> lines, String defaultGrade) {
		if (lines == null || lines.isEmpty())
			throw new IllegalArgumentException("낙찰 결과 행을 1개 이상 입력해야 합니다.");
		int soldQuantity = 0;
		for (var line : lines) {
			if (line.unitPrice() < 1)
				throw new IllegalArgumentException("낙찰 결과 단가는 1원 이상이어야 합니다.");
			attempt.addResultLine(new AuctionResultLine(
					attempt.getAuctionDate(),
					normalize(line.auctionGrade()) == null ? defaultGrade : normalize(line.auctionGrade()),
					line.quantity(),
					line.unitPrice(),
					soldAmount(line.quantity(), line.unitPrice()),
					normalize(line.note()),
					line.inspectionStatus() == null ? AuctionInspectionStatus.NORMAL : line.inspectionStatus()));
			soldQuantity += line.quantity();
		}
		return soldQuantity;
	}

	private int soldAmount(int quantity, int unitPrice) {
		try {
			return Math.multiplyExact(quantity, unitPrice);
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("낙찰 결과 금액은 2,147,483,647원 이하여야 합니다.");
		}
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public void requireReturnConfirmable() {
		if (!List.of(AuctionLotStatus.REAUCTION_WAITING, AuctionLotStatus.RETURN_INFERRED,
				AuctionLotStatus.PARTIALLY_RETURNED).contains(getCurrentStatus()))
			throw new IllegalArgumentException("재경매대기, 반환추정 또는 부분반환 상태에서만 반환을 확인할 수 있습니다.");
		if (getReturnConfirmableQuantity() <= 0)
			throw new IllegalArgumentException("확인할 반환 수량이 없습니다.");
	}

	public void confirmReturn(Integer quantity, LocalDate returnDate, String worker, String memo) {
		requireReturnConfirmable();
		if (quantity == null || quantity < 1) {
			throw new IllegalArgumentException("반환 확인 수량은 1 이상이어야 합니다.");
		}
		if (returnDate == null) {
			throw new IllegalArgumentException("반환 확인 날짜는 필수입니다.");
		}
		int confirmableQuantity = getReturnConfirmableQuantity();
		if (quantity > confirmableQuantity) {
			throw new IllegalArgumentException("반환 확인 수량이 확인 가능한 수량보다 많습니다.");
		}
		if (currentStatus == AuctionLotStatus.RETURN_INFERRED && returnedQuantity > 0) {
			int unconfirmedQuantity = returnedQuantity - quantity;
			returnedQuantity = quantity;
			waitingQuantity += unconfirmedQuantity;
		} else {
			returnedQuantity += quantity;
			waitingQuantity -= quantity;
		}
		returnConfirmedDate = returnDate;
		AuctionLotStatus next = waitingQuantity == 0 ? AuctionLotStatus.RETURNED : AuctionLotStatus.PARTIALLY_RETURNED;
		changeStatus(next, next == AuctionLotStatus.RETURNED ? "반환 완료" : "부분반환 확인", worker, memo);
	}

	public Integer getReturnConfirmableQuantity() {
		if (currentStatus == AuctionLotStatus.RETURN_INFERRED && returnedQuantity > 0) {
			return returnedQuantity;
		}
		return waitingQuantity;
	}

	public void adjustQuantities(Integer sold, Integer waiting, Integer returned, String worker, String memo) {
		if (sold + waiting + returned != shippedQuantity) {
			throw new IllegalArgumentException("판매/대기/반환 수량 합계가 출하 수량과 일치해야 합니다.");
		}
		soldQuantity = sold;
		waitingQuantity = waiting;
		returnedQuantity = returned;
		AuctionLotStatus next = waiting > 0
				? (sold > 0 ? AuctionLotStatus.PARTIALLY_SOLD : AuctionLotStatus.REAUCTION_WAITING)
				: returned > 0 ? AuctionLotStatus.RETURNED : AuctionLotStatus.SOLD;
		changeStatus(next, "수량 보정", worker, memo);
	}

	public void changeStatus(AuctionLotStatus next, String reason, String worker, String memo) {
		if (currentStatus == next) {
			return;
		}
		var history = new AuctionLotStatusHistory(this, currentStatus, next, reason, worker, memo);
		statusHistory.add(history);
		currentStatus = next;
	}
}
