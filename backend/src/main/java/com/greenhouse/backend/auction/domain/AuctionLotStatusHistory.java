package com.greenhouse.backend.auction.domain;

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

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auction_lot_status_history")
public class AuctionLotStatusHistory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Getter(AccessLevel.NONE)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shipment_lot_id", nullable = false)
	private AuctionShipmentLot shipmentLot;
	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status")
	private AuctionLotStatus previousStatus;
	@Enumerated(EnumType.STRING)
	@Column(name = "new_status", nullable = false)
	private AuctionLotStatus newStatus;
	@Column(name = "changed_at", nullable = false)
	private LocalDateTime changedAt;
	@Column(nullable = false)
	private String reason;
	private String worker;
	@Column(columnDefinition = "text")
	private String memo;

	public AuctionLotStatusHistory(AuctionShipmentLot lot, AuctionLotStatus previous, AuctionLotStatus next,
			String reason, String worker, String memo) {
		shipmentLot = lot;
		previousStatus = previous;
		newStatus = next;
		changedAt = LocalDateTime.now();
		this.reason = reason;
		this.worker = worker;
		this.memo = memo;
	}
}
