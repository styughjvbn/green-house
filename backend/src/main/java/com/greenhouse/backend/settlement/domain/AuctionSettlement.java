package com.greenhouse.backend.settlement.domain;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.partner.domain.BusinessPartner;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
	name = "auction_settlements",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_auction_settlement_house_date",
		columnNames = {"auction_house_id", "auction_date"})
)
public class AuctionSettlement extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auction_house_id", nullable = false)
	private BusinessPartner auctionHouse;

	@Column(name = "auction_date", nullable = false)
	private LocalDate auctionDate;

	@Column(name = "result_received_at")
	private LocalDateTime resultReceivedAt;

	@Column(name = "expected_payment_date")
	private LocalDate expectedPaymentDate;

	@Column(name = "gross_amount", nullable = false)
	private Long grossAmount;

	@Column(name = "fee_amount", nullable = false)
	private Long feeAmount;

	@Column(name = "deduction_amount", nullable = false)
	private Long deductionAmount;

	@Column(name = "expected_deposit_amount", nullable = false)
	private Long expectedDepositAmount;

	@Column(name = "paid_amount", nullable = false)
	private Long paidAmount;

	@Column(name = "remaining_amount", nullable = false)
	private Long remainingAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuctionSettlementStatus status;

	@Column(name = "payment_meta_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String paymentMetaJson;

	@Column(columnDefinition = "text")
	private String memo;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	@Column(name = "confirmed_by")
	private String confirmedBy;

	@OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AuctionSettlementLine> lines = new ArrayList<>();

	protected AuctionSettlement() { }

	public AuctionSettlement(BusinessPartner auctionHouse, LocalDate auctionDate) {
		this.auctionHouse = auctionHouse;
		this.auctionDate = auctionDate;
		this.expectedPaymentDate = auctionDate;
		this.grossAmount = 0L;
		this.feeAmount = 0L;
		this.deductionAmount = 0L;
		this.expectedDepositAmount = 0L;
		this.paidAmount = 0L;
		this.remainingAmount = 0L;
		this.status = AuctionSettlementStatus.CREATED;
	}

	public void synchronizeLines(List<AuctionResultLine> resultLines) {
		Set<Long> resultIds = new HashSet<>(resultLines.stream().map(AuctionResultLine::getId).toList());
		lines.removeIf(line -> !resultIds.contains(line.getAuctionResultLine().getId()));
		Set<Long> existingIds = new HashSet<>(lines.stream()
			.map(line -> line.getAuctionResultLine().getId())
			.toList());
		resultLines.stream()
			.filter(line -> !existingIds.contains(line.getId()))
			.map(AuctionSettlementLine::new)
			.forEach(this::addLine);

		grossAmount = lines.stream().mapToLong(AuctionSettlementLine::getAmount).sum();
		expectedDepositAmount = Math.max(0L, grossAmount - feeAmount - deductionAmount);
		remainingAmount = Math.max(0L, expectedDepositAmount - paidAmount);
		resultReceivedAt = LocalDateTime.now();
		if (paidAmount > 0 && remainingAmount > 0) status = AuctionSettlementStatus.PARTIALLY_PAID;
		else if (paidAmount > 0 && remainingAmount == 0) status = AuctionSettlementStatus.PAID;
		else status = lines.isEmpty() ? AuctionSettlementStatus.CREATED : AuctionSettlementStatus.PAYMENT_WAITING;
	}

	private void addLine(AuctionSettlementLine line) {
		line.setSettlement(this);
		lines.add(line);
	}

	public Long getId() { return id; }
	public BusinessPartner getAuctionHouse() { return auctionHouse; }
	public LocalDate getAuctionDate() { return auctionDate; }
	public LocalDateTime getResultReceivedAt() { return resultReceivedAt; }
	public LocalDate getExpectedPaymentDate() { return expectedPaymentDate; }
	public Long getGrossAmount() { return grossAmount; }
	public Long getFeeAmount() { return feeAmount; }
	public Long getDeductionAmount() { return deductionAmount; }
	public Long getExpectedDepositAmount() { return expectedDepositAmount; }
	public Long getPaidAmount() { return paidAmount; }
	public Long getRemainingAmount() { return remainingAmount; }
	public AuctionSettlementStatus getStatus() { return status; }
	public String getMemo() { return memo; }
	public LocalDateTime getConfirmedAt() { return confirmedAt; }
	public String getConfirmedBy() { return confirmedBy; }
	public List<AuctionSettlementLine> getLines() { return lines; }
}
