package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.farm.domain.OrchidGroup;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_inventory_movements")
public class SalesInventoryMovement extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "orchid_group_id", nullable = false)
	private OrchidGroup orchidGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_slip_id", nullable = false)
	private SalesSlip salesSlip;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_slip_item_id", nullable = false)
	private SalesSlipItem salesSlipItem;

	@Enumerated(EnumType.STRING)
	@Column(name = "change_type", nullable = false)
	private SalesInventoryMovementType changeType;

	@Column(name = "quantity_delta", nullable = false)
	private Integer quantityDelta;

	@Column(columnDefinition = "text")
	private String memo;

	public SalesInventoryMovement(
			OrchidGroup orchidGroup,
			SalesSlip salesSlip,
			SalesSlipItem salesSlipItem,
			SalesInventoryMovementType changeType,
			Integer quantityDelta,
			String memo) {
		this.orchidGroup = orchidGroup;
		this.salesSlip = salesSlip;
		this.salesSlipItem = salesSlipItem;
		this.changeType = changeType;
		this.quantityDelta = quantityDelta;
		this.memo = memo;
	}
}
