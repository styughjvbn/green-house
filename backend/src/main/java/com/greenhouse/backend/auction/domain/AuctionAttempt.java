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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "auction_attempts")
public class AuctionAttempt extends BaseEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_lot_id", nullable = false) private AuctionShipmentLot shipmentLot;
	@Column(name = "auction_date", nullable = false) private LocalDate auctionDate;
	@Column(name = "attempt_no", nullable = false) private Integer attemptNo;
	@Enumerated(EnumType.STRING) @Column(name = "attempt_status", nullable = false) private AuctionAttemptStatus attemptStatus;
	@Column(name = "failed_reason") private String failedReason;
	@Column(columnDefinition = "text") private String memo;
	@OneToMany(mappedBy = "auctionAttempt", cascade = CascadeType.ALL, orphanRemoval = true) private List<AuctionResultLine> resultLines = new ArrayList<>();
	protected AuctionAttempt() { }
	public AuctionAttempt(LocalDate date, Integer no, AuctionAttemptStatus status, String failedReason, String memo) { auctionDate = date; attemptNo = no; attemptStatus = status; this.failedReason = failedReason; this.memo = memo; }
	void setShipmentLot(AuctionShipmentLot lot) { shipmentLot = lot; }
	public void addResultLine(AuctionResultLine line) { resultLines.add(line); line.setAuctionAttempt(this); }
	public void updateStatus(AuctionAttemptStatus status) { attemptStatus = status; }
	public void recalculateStatus() {
		boolean hasSold = resultLines.stream().anyMatch(line -> line.getAmount() > 0);
		boolean hasFailed = resultLines.stream().anyMatch(line -> line.getAmount() == 0);
		boolean hasReturn = resultLines.stream()
			.anyMatch(line -> line.getInspectionStatus() == AuctionInspectionStatus.RETURN_INFERRED);
		if (hasReturn) attemptStatus = AuctionAttemptStatus.RETURN_INFERRED;
		else if (hasSold && hasFailed) attemptStatus = AuctionAttemptStatus.PARTIALLY_SOLD;
		else if (hasSold) attemptStatus = AuctionAttemptStatus.SOLD;
		else attemptStatus = AuctionAttemptStatus.FAILED;
	}
	public Long getId() { return id; }
	public AuctionShipmentLot getShipmentLot() { return shipmentLot; }
	public LocalDate getAuctionDate() { return auctionDate; }
	public Integer getAttemptNo() { return attemptNo; }
	public AuctionAttemptStatus getAttemptStatus() { return attemptStatus; }
	public String getFailedReason() { return failedReason; }
	public String getMemo() { return memo; }
	public List<AuctionResultLine> getResultLines() { return resultLines; }
}
