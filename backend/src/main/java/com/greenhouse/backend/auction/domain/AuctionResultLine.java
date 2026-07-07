package com.greenhouse.backend.auction.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auction_result_lines")
public class AuctionResultLine extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auction_attempt_id", nullable = false)
	private AuctionAttempt auctionAttempt;
	@Column(name = "auction_date", nullable = false)
	private LocalDate auctionDate;
	@Column(name = "auction_grade")
	private String auctionGrade;
	@Column(nullable = false)
	private Integer quantity;
	@Column(name = "unit_price", nullable = false)
	private Integer unitPrice;
	@Column(nullable = false)
	private Integer amount;
	@Column(columnDefinition = "text")
	private String note;
	@Enumerated(EnumType.STRING)
	@Column(name = "inspection_status", nullable = false)
	private AuctionInspectionStatus inspectionStatus;

	public AuctionResultLine(LocalDate date, String grade, Integer quantity, Integer unitPrice, Integer amount,
			String note, AuctionInspectionStatus inspectionStatus) {
		auctionDate = date;
		auctionGrade = grade;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.amount = amount;
		this.note = note;
		this.inspectionStatus = inspectionStatus;
	}

	void setAuctionAttempt(AuctionAttempt attempt) {
		auctionAttempt = attempt;
	}
}
