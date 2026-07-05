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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auction_shipments")
public class AuctionShipment extends BaseEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@Column(name = "shipment_date", nullable = false) private LocalDate shipmentDate;
	@Column(name = "auction_market", nullable = false) private String auctionMarket;
	@Column(nullable = false) private String status;
	@Column(columnDefinition = "text") private String memo;
	@OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true) private List<AuctionShipmentLot> lots = new ArrayList<>();

	protected AuctionShipment() { }
	public AuctionShipment(LocalDate shipmentDate, String auctionMarket) { this.shipmentDate = shipmentDate; this.auctionMarket = auctionMarket; this.status = "SHIPPED"; }
	public void addLot(AuctionShipmentLot lot) { lots.add(lot); lot.setShipment(this); }
	public Long getId() { return id; }
	public LocalDate getShipmentDate() { return shipmentDate; }
	public String getAuctionMarket() { return auctionMarket; }
	public String getStatus() { return status; }
	public String getMemo() { return memo; }
	public List<AuctionShipmentLot> getLots() { return lots; }
}
