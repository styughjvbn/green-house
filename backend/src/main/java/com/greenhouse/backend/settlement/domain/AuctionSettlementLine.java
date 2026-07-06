package com.greenhouse.backend.settlement.domain;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auction_settlement_lines")
public class AuctionSettlementLine extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "settlement_id", nullable = false)
	private AuctionSettlement settlement;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auction_result_line_id", nullable = false, unique = true)
	private AuctionResultLine auctionResultLine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auction_shipment_lot_id", nullable = false)
	private AuctionShipmentLot auctionShipmentLot;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "unit_price", nullable = false)
	private Integer unitPrice;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuctionSettlementLineStatus status;

	@Column(name = "line_meta_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String lineMetaJson;

	protected AuctionSettlementLine() { }

	public AuctionSettlementLine(AuctionResultLine resultLine) {
		this.auctionResultLine = resultLine;
		this.auctionShipmentLot = resultLine.getAuctionAttempt().getShipmentLot();
		this.quantity = resultLine.getQuantity();
		this.unitPrice = resultLine.getUnitPrice();
		this.amount = resultLine.getAmount().longValue();
		this.status = AuctionSettlementLineStatus.UNPAID;
	}

	void setSettlement(AuctionSettlement settlement) { this.settlement = settlement; }

	public Long getId() { return id; }
	public AuctionResultLine getAuctionResultLine() { return auctionResultLine; }
	public AuctionShipmentLot getAuctionShipmentLot() { return auctionShipmentLot; }
	public Integer getQuantity() { return quantity; }
	public Integer getUnitPrice() { return unitPrice; }
	public Long getAmount() { return amount; }
	public AuctionSettlementLineStatus getStatus() { return status; }
}
