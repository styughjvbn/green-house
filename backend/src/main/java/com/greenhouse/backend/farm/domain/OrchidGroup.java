package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orchid_groups")
public class OrchidGroup extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bed_zone_id", nullable = false)
	private BedZone bedZone;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "variety_id")
	private Variety variety;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inbound_record_id")
	private InboundRecord inboundRecord;

	private String genus;

	@Column(name = "variety_name", nullable = false)
	private String varietyName;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "reserved_quantity", nullable = false)
	private Integer reservedQuantity;

	@Column(name = "pot_size")
	private String potSize;

	@Column(name = "age_year")
	private Integer ageYear;

	@Column(nullable = false)
	private String status;

	@Column(name = "placement_type")
	private String placementType;

	@Column(name = "tray_count")
	private Integer trayCount;

	@Column(name = "split_placement_allowed")
	private Boolean splitPlacementAllowed;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(columnDefinition = "text")
	private String memo;

	@Column(name = "start_position", precision = 6, scale = 2)
	private BigDecimal startPosition;

	@Column(name = "end_position", precision = 6, scale = 2)
	private BigDecimal endPosition;

	public OrchidGroup(
			BedZone bedZone,
			String genus,
			String varietyName,
			Integer quantity,
			String potSize,
			Integer ageYear,
			String status,
			Integer sortOrder,
			BigDecimal startPosition,
			BigDecimal endPosition) {
		this.bedZone = bedZone;
		this.genus = genus;
		this.varietyName = varietyName;
		this.quantity = quantity;
		this.reservedQuantity = 0;
		this.potSize = potSize;
		this.ageYear = ageYear;
		this.status = status;
		this.sortOrder = sortOrder;
		this.splitPlacementAllowed = false;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
	}

	public void updateDetails(
			String genus,
			String varietyName,
			Integer quantity,
			String potSize,
			Integer ageYear,
			String status,
			String placementType,
			Integer trayCount,
			Boolean splitPlacementAllowed,
			BigDecimal startPosition,
			BigDecimal endPosition,
			String memo) {
		this.genus = genus;
		this.varietyName = varietyName;
		this.quantity = quantity;
		this.potSize = potSize;
		this.ageYear = ageYear;
		this.status = status;
		this.placementType = placementType;
		this.trayCount = trayCount;
		this.splitPlacementAllowed = Boolean.TRUE.equals(splitPlacementAllowed);
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.memo = memo;
	}

	public void moveTo(BedZone bedZone, Integer sortOrder, BigDecimal startPosition, BigDecimal endPosition) {
		this.bedZone = bedZone;
		this.sortOrder = sortOrder;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
	}

	public void assignVariety(Variety variety) {
		this.variety = variety;
		if (variety != null) {
			this.genus = variety.getGenus();
			this.varietyName = variety.getName();
		}
	}

	public void assignInboundRecord(InboundRecord inboundRecord) {
		this.inboundRecord = inboundRecord;
	}

	public int getAvailableQuantity() {
		return Math.max(0, quantity - reservedQuantity);
	}

	public void reserve(Integer reserveQuantity) {
		validatePositiveQuantity(reserveQuantity, "예약 수량");
		if (getAvailableQuantity() < reserveQuantity) {
			throw new IllegalArgumentException("난 묶음 가용 수량이 부족합니다.");
		}
		this.reservedQuantity += reserveQuantity;
	}

	public void releaseReserved(Integer releaseQuantity) {
		validatePositiveQuantity(releaseQuantity, "예약 해제 수량");
		if (reservedQuantity < releaseQuantity) {
			throw new IllegalArgumentException("예약 해제 수량이 현재 예약 수량보다 많습니다.");
		}
		this.reservedQuantity -= releaseQuantity;
	}

	public void outboundReserved(Integer outboundQuantity) {
		validatePositiveQuantity(outboundQuantity, "출고 수량");
		if (reservedQuantity < outboundQuantity) {
			throw new IllegalArgumentException("예약 수량보다 많은 출고를 처리할 수 없습니다.");
		}
		if (quantity < outboundQuantity) {
			throw new IllegalArgumentException("난 묶음 수량이 부족합니다.");
		}
		this.reservedQuantity -= outboundQuantity;
		this.quantity -= outboundQuantity;
	}

	private void validatePositiveQuantity(Integer value, String label) {
		if (value == null || value < 1) {
			throw new IllegalArgumentException(label + "은 1 이상이어야 합니다.");
		}
	}
}
