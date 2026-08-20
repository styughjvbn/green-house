package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_inventory_movements")
public class SalesInventoryMovement extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_inventory_movements_id_seq")
	@SequenceGenerator(name = "sales_inventory_movements_id_seq", sequenceName = "sales_inventory_movements_id_seq", allocationSize = 50)
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

	@Column(name = "mutation_id")
	private Long mutationId;

	@Column(name = "correlation_id")
	private UUID correlationId;

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

	public void linkMutation(Long mutationId, UUID correlationId) {
		if (mutationId == null || correlationId == null) {
			throw new IllegalArgumentException("판매 재고 이동에 연결할 Mutation 정보가 필요합니다.");
		}
		if (this.mutationId != null && !this.mutationId.equals(mutationId)) {
			throw new IllegalStateException("판매 재고 이동은 다른 Mutation으로 변경할 수 없습니다.");
		}
		this.mutationId = mutationId;
		this.correlationId = correlationId;
	}
}
