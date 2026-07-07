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
@Table(name = "auction_shipment_lots")
public class AuctionShipmentLot extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

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

	public void confirmReturn(Integer quantity, LocalDate returnDate, String worker, String memo) {
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
