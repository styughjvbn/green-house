package com.greenhouse.backend.settlement.domain;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auction_settlement_lines")
public class AuctionSettlementLine extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auction_settlement_lines_id_seq")
	@SequenceGenerator(name = "auction_settlement_lines_id_seq", sequenceName = "auction_settlement_lines_id_seq", allocationSize = 50)
	private Long id;

	@Getter(AccessLevel.NONE)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "settlement_id", nullable = false)
	private AuctionSettlement settlement;

	@Column(name = "auction_result_line_id", nullable = false, unique = true)
	private Long auctionResultLineId;

	@Column(name = "auction_shipment_lot_id", nullable = false)
	private Long auctionShipmentLotId;

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

	public AuctionSettlementLine(Long resultLineId, Long shipmentLotId, Integer quantity, Integer unitPrice, Long amount) {
		this.auctionResultLineId = resultLineId;
		this.auctionShipmentLotId = shipmentLotId;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.amount = amount;
		this.status = AuctionSettlementLineStatus.UNPAID;
	}

	void setSettlement(AuctionSettlement settlement) {
		this.settlement = settlement;
	}
}
