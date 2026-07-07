package com.greenhouse.backend.auction.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auction_shipments")
public class AuctionShipment extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "shipment_date", nullable = false)
	private LocalDate shipmentDate;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auction_house_id", nullable = false)
	private BusinessPartner auctionHouse;
	@Column(nullable = false)
	private String status;
	@Column(columnDefinition = "text")
	private String memo;
	@OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AuctionShipmentLot> lots = new ArrayList<>();

	public AuctionShipment(LocalDate shipmentDate, BusinessPartner auctionHouse) {
		if (auctionHouse == null || auctionHouse.getPartnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매 출하는 경매장 유형 거래처가 필요합니다.");
		}
		this.shipmentDate = shipmentDate;
		this.auctionHouse = auctionHouse;
		this.status = "SHIPPED";
	}

	public void addLot(AuctionShipmentLot lot) {
		lots.add(lot);
		lot.setShipment(this);
	}

	public String getAuctionMarket() {
		return auctionHouse.getName();
	}
}
