package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import jakarta.persistence.Entity;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_slip_item_allocations")
public class SalesSlipItemAllocation extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_slip_item_id", nullable = false)
	private SalesSlipItem salesSlipItem;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "orchid_group_id", nullable = false)
	private OrchidGroup orchidGroup;

	private Integer allocatedQuantity;

	public SalesSlipItemAllocation(OrchidGroup orchidGroup, Integer allocatedQuantity) {
		this.orchidGroup = orchidGroup;
		this.allocatedQuantity = allocatedQuantity;
	}

	void setSalesSlipItem(SalesSlipItem salesSlipItem) {
		this.salesSlipItem = salesSlipItem;
	}
}
