package com.greenhouse.backend.farm.domain.orchid;

import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.variety.Variety;
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

	@Enumerated(EnumType.STRING)
	@Column(name = "pot_size_code", nullable = false, length = 30)
	private PotSizeCode potSizeCode;

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
		applyPotSize(potSize);
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
		applyPotSize(potSize);
		this.ageYear = ageYear;
		this.status = status;
		this.placementType = placementType;
		this.trayCount = trayCount;
		this.splitPlacementAllowed = Boolean.TRUE.equals(splitPlacementAllowed);
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.memo = memo;
	}

	private void applyPotSize(String potSize) {
		this.potSizeCode = PotSizeCode.fromInput(potSize);
		this.potSize = this.potSizeCode.getDisplayValue();
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
		if (inboundRecord != null) {
			inboundRecord.addCreatedOrchidGroup(this);
		}
	}

	public int getAvailableQuantity() {
		return Math.max(0, quantity - reservedQuantity);
	}

	public boolean isVisibleInActiveViews() {
		return quantity != null && quantity > 0;
	}

	public void cancelCreation() {
		if ("생성 취소".equals(status)) {
			return;
		}
		if (reservedQuantity != 0) {
			throw new IllegalArgumentException("예약 수량이 있는 난 묶음은 생성을 취소할 수 없습니다.");
		}
		this.quantity = 0;
		this.status = "생성 취소";
	}

	public void applyRepot(Integer inputQuantity) {
		applyRepot(inputQuantity, null, null);
	}

	public void applyRepot(
			Integer inputQuantity,
			BigDecimal releasedStartPosition,
			BigDecimal releasedEndPosition) {
		validatePositiveQuantity(inputQuantity, "분갈이 투입 수량");
		if (getAvailableQuantity() < inputQuantity) {
			throw new IllegalArgumentException("난 묶음 가용 수량보다 많이 분갈이할 수 없습니다.");
		}
		boolean partial = inputQuantity < this.quantity;
		if ((releasedStartPosition == null) != (releasedEndPosition == null)) {
			throw new IllegalArgumentException("원본에서 비울 시작·끝 위치를 모두 입력해야 합니다.");
		}
		if (releasedStartPosition != null) {
			if (!partial) {
				throw new IllegalArgumentException("일부 작업할 때만 원본에서 비울 위치를 지정할 수 있습니다.");
			}
			if (startPosition == null || endPosition == null
					|| releasedStartPosition.compareTo(startPosition) <= 0
					|| releasedStartPosition.compareTo(endPosition) >= 0
					|| releasedEndPosition.compareTo(endPosition) != 0) {
				throw new IllegalArgumentException("원본 배치의 뒤쪽 연속 구간만 비울 수 있습니다.");
			}
			this.endPosition = releasedStartPosition;
		}
		this.quantity -= inputQuantity;
		if (this.quantity == 0) {
			this.status = "종료";
		}
	}

	public void discard(Integer discardQuantity) {
		validatePositiveQuantity(discardQuantity, "폐기 수량");
		if (getAvailableQuantity() < discardQuantity) {
			throw new IllegalArgumentException("난 묶음 가용 수량보다 많이 폐기할 수 없습니다.");
		}
		this.quantity -= discardQuantity;
		if (this.quantity == 0) {
			this.status = "폐기";
		}
	}

	public void correctQuantityAndStatus(Integer correctedQuantity, String correctedStatus) {
		if (correctedQuantity == null || correctedQuantity < 0) {
			throw new IllegalArgumentException("보정 수량은 0 이상이어야 합니다.");
		}
		if (correctedQuantity < reservedQuantity) {
			throw new IllegalArgumentException("보정 수량은 현재 예약 수량보다 작을 수 없습니다.");
		}
		if (correctedStatus == null || correctedStatus.isBlank()) {
			throw new IllegalArgumentException("보정 상태가 필요합니다.");
		}
		this.quantity = correctedQuantity;
		this.status = correctedStatus.trim();
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

	public void restoreOutbound(Integer restoreQuantity) {
		validatePositiveQuantity(restoreQuantity, "출고 복구 수량");
		this.quantity += restoreQuantity;
	}

	private void validatePositiveQuantity(Integer value, String label) {
		if (value == null || value < 1) {
			throw new IllegalArgumentException(label + "은 1 이상이어야 합니다.");
		}
	}
}
