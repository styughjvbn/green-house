package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sales_slip_items")
public class SalesSlipItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_slip_id", nullable = false)
	private SalesSlip salesSlip;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "orchid_group_id")
	private OrchidGroup orchidGroup;

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

	protected SalesSlipItem() {
	}

	public SalesSlipItem(
		OrchidGroup orchidGroup,
		String itemName,
		String genus,
		String spec,
		Integer quantity,
		Integer unitPrice,
		String memo
	) {
		this.orchidGroup = orchidGroup;
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

	public Long getId() {
		return id;
	}

	public OrchidGroup getOrchidGroup() {
		return orchidGroup;
	}

	public String getItemName() {
		return itemName;
	}

	public String getGenus() {
		return genus;
	}

	public String getSpec() {
		return spec;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public Integer getUnitPrice() {
		return unitPrice;
	}

	public Integer getAmount() {
		return amount;
	}

	public String getMemo() {
		return memo;
	}
}
