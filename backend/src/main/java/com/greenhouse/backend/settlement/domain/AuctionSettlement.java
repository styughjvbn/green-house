package com.greenhouse.backend.settlement.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auction_settlements", uniqueConstraints = @UniqueConstraint(name = "uk_auction_settlement_house_date", columnNames = {
		"auction_house_id", "auction_date" }))
public class AuctionSettlement extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auction_settlements_id_seq")
	@SequenceGenerator(name = "auction_settlements_id_seq", sequenceName = "auction_settlements_id_seq", allocationSize = 50)
	private Long id;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "auction_house_id", nullable = false)
	private Long auctionHouseId;

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

	public AuctionSettlement(Long auctionHouseId, LocalDate auctionDate) {
		this.auctionHouseId = auctionHouseId;
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

	public void synchronizeLines(List<AuctionSettlementLine> resultLines, LocalDateTime receivedAt) {
		Set<Long> resultIds = new HashSet<>(resultLines.stream().map(AuctionSettlementLine::getAuctionResultLineId).toList());
		lines.removeIf(line -> !resultIds.contains(line.getAuctionResultLineId()));
		Set<Long> existingIds = new HashSet<>(lines.stream()
				.map(AuctionSettlementLine::getAuctionResultLineId)
				.toList());
		for (var line : resultLines) {
			if (existingIds.add(line.getAuctionResultLineId())) {
				addLine(line);
			}
		}

		grossAmount = lines.stream().mapToLong(AuctionSettlementLine::getAmount).sum();
		expectedDepositAmount = Math.max(0L, grossAmount - feeAmount - deductionAmount);
		remainingAmount = Math.max(0L, expectedDepositAmount - paidAmount);
		resultReceivedAt = receivedAt;
		if (paidAmount > 0 && remainingAmount > 0)
			status = AuctionSettlementStatus.PARTIALLY_PAID;
		else if (paidAmount > 0 && remainingAmount == 0)
			status = AuctionSettlementStatus.PAID;
		else
			status = lines.isEmpty() ? AuctionSettlementStatus.CREATED : AuctionSettlementStatus.PAYMENT_WAITING;
	}

	public void updateExpectedPaymentDate(LocalDate expectedPaymentDate) {
		this.expectedPaymentDate = expectedPaymentDate;
	}

	public void recordPayment(Long amount, String worker, LocalDateTime confirmedAt) {
		if (amount <= 0)
			throw new IllegalArgumentException("입금액은 0원보다 커야 합니다.");
		if (amount > remainingAmount)
			throw new IllegalArgumentException("입금액은 현재 잔액을 초과할 수 없습니다.");
		this.paidAmount += amount;
		this.remainingAmount = Math.max(0L, expectedDepositAmount - paidAmount);
		this.status = remainingAmount == 0 ? AuctionSettlementStatus.PAID : AuctionSettlementStatus.PARTIALLY_PAID;
		this.confirmedAt = confirmedAt;
		this.confirmedBy = worker;
	}

	private void addLine(AuctionSettlementLine line) {
		line.setSettlement(this);
		lines.add(line);
	}
}
