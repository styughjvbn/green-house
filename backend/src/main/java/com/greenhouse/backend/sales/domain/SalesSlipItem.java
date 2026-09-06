package com.greenhouse.backend.sales.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_slip_items")
public class SalesSlipItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_slip_items_id_seq")
	@SequenceGenerator(name = "sales_slip_items_id_seq", sequenceName = "sales_slip_items_id_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_slip_id", nullable = false)
	private SalesSlip salesSlip;

	@Column(name = "auction_shipment_lot_id", unique = true)
	private Long auctionShipmentLotId;

	@OneToMany(mappedBy = "salesSlipItem", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SalesSlipItemAllocation> allocations = new ArrayList<>();

	@Column(name = "item_name", nullable = false)
	private String itemName;

	private String genus;

	private String spec;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "unit_price", nullable = false)
	private Integer unitPrice;

	@Column(nullable = false)
	private Integer amount;

	@Column(columnDefinition = "text")
	private String memo;

	public SalesSlipItem(
			Long auctionShipmentLotId,
			String itemName,
			String genus,
			String spec,
			Integer quantity,
			Integer unitPrice,
			String memo) {
		this.auctionShipmentLotId = auctionShipmentLotId;
		this.itemName = itemName;
		this.genus = genus;
		this.spec = spec;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.amount = quantity * unitPrice;
		this.memo = memo;
	}

	void setSalesSlip(SalesSlip salesSlip) {
		this.salesSlip = salesSlip;
	}

	public void addAllocation(SalesSlipItemAllocation allocation) {
		allocation.setSalesSlipItem(this);
		this.allocations.add(allocation);
	}

	public void updateDetails(
			String itemName,
			String genus,
			String spec,
			Integer quantity,
			Integer unitPrice,
			String memo) {
		this.itemName = itemName;
		this.genus = genus;
		this.spec = spec;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.amount = quantity * unitPrice;
		this.memo = memo;
	}

	public void replaceAllocations(List<SalesSlipItemAllocation> allocations) {
		this.allocations.clear();
		allocations.forEach(this::addAllocation);
	}

	public void assignAuctionShipmentLot(Long auctionShipmentLotId) {
		this.auctionShipmentLotId = auctionShipmentLotId;
	}

	public void clearAuctionShipmentLot() {
		this.auctionShipmentLotId = null;
	}
}
