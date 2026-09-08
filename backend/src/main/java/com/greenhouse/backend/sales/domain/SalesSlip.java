package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_slips")
public class SalesSlip extends BaseEntity {

	public static final String STATUS_DRAFT = "작성중";

	public static final String STATUS_DIRECT_OUTBOUND_COMPLETED = "출고 완료";

	public static final String STATUS_AUCTION_SHIPMENT_COMPLETED = "출하 완료";

	public static final String STATUS_CANCELED = "취소";

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_slips_id_seq")
	@SequenceGenerator(name = "sales_slips_id_seq", sequenceName = "sales_slips_id_seq", allocationSize = 50)
	private Long id;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "slip_number", nullable = false, unique = true)
	private String slipNumber;

	@Column(name = "sale_date", nullable = false)
	private LocalDate saleDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "sales_type")
	private SalesType salesType;

	@Column(name = "auction_shipment_id", unique = true)
	private Long auctionShipmentId;

	@Column(name = "partner_id", nullable = false)
	private Long partnerId;

	@Column(name = "total_amount", nullable = false)
	private Integer totalAmount;

	@Column(name = "expected_payment_date")
	private LocalDate expectedPaymentDate;

	@Column(name = "paid_amount")
	private Long paidAmount;

	@Column(name = "remaining_amount")
	private Long remainingAmount;

	@Column(name = "payment_status", nullable = false)
	private String paymentStatus;

	@Column(name = "sales_status", nullable = false)
	private String salesStatus;

	@Column(name = "payment_method")
	private String paymentMethod;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "salesSlip", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SalesSlipItem> items = new ArrayList<>();

	public SalesSlip(String slipNumber, LocalDate saleDate, SalesType salesType, Long auctionShipmentId, Long partnerId,
			String paymentStatus, String salesStatus, String paymentMethod, String memo) {
		this.slipNumber = slipNumber;
		this.saleDate = saleDate;
		this.salesType = salesType;
		this.auctionShipmentId = auctionShipmentId;
		this.partnerId = partnerId;
		this.paymentStatus = paymentStatus;
		this.salesStatus = validateInitialSalesStatus(salesType, salesStatus);
		this.paymentMethod = paymentMethod;
		this.memo = memo;
		this.totalAmount = 0;
		this.expectedPaymentDate = saleDate;
		this.paidAmount = 0L;
		this.remainingAmount = 0L;
	}

	public void addItem(SalesSlipItem item) {
		item.setSalesSlip(this);
		this.items.add(item);
		recalculateAmounts();
	}

	public void replaceItems(List<SalesSlipItem> items) {
		this.items.clear();
		items.forEach(this::addItem);
		recalculateAmounts();
	}

	public void refreshAmounts() {
		recalculateAmounts();
	}

	public void updateDraftInfo(LocalDate saleDate, Long partnerId, String paymentStatus, String paymentMethod,
			String memo) {
		this.saleDate = saleDate;
		this.partnerId = partnerId;
		this.paymentStatus = paymentStatus;
		this.paymentMethod = paymentMethod;
		this.memo = memo;
	}

	public void updateExpectedPaymentDate(LocalDate expectedPaymentDate) {
		this.expectedPaymentDate = expectedPaymentDate;
	}

	public void recordPayment(Long amount) {
		validatePaymentTarget();
		if (amount <= 0) {
			throw new IllegalArgumentException("입금액은 0보다 커야 합니다.");
		}
		if (amount > getRemainingAmount()) {
			throw new IllegalArgumentException("입금액이 현재 잔액을 초과할 수 없습니다.");
		}
		this.paidAmount = getPaidAmount() + amount;
		this.remainingAmount = Math.max(0L, totalAmount.longValue() - paidAmount);
		this.paymentStatus = remainingAmount == 0 ? "입금 완료" : "부분입금";
	}

	public boolean canEdit(boolean hasPaymentEvent) {
		return editRejectionReason(hasPaymentEvent) == null;
	}

	public void requireEditable(boolean hasPaymentEvent) {
		String reason = editRejectionReason(hasPaymentEvent);
		if (reason != null)
			throw new IllegalArgumentException(reason);
	}

	private String editRejectionReason(boolean hasPaymentEvent) {
		if (salesType != SalesType.DIRECT)
			return "경매 판매 전표 수정은 아직 지원하지 않습니다.";
		if (!STATUS_DRAFT.equals(salesStatus))
			return "작성중 상태 전표만 수정할 수 있습니다.";
		if (hasPaymentEvent || (paidAmount != null && paidAmount > 0))
			return "입금 이력이 있는 전표는 수정할 수 없습니다.";
		return null;
	}

	public boolean canComplete() {
		return STATUS_DRAFT.equals(salesStatus);
	}

	public boolean canConfirmPayment() {
		return paymentTargetRejectionReason() == null && remainingAmount != null && remainingAmount > 0;
	}

	// 잔액 검사는 새 입금에만 적용한다. 완납 후에도 기존 입금의 재요청은 확인할 수 있다.
	public void validatePaymentTarget() {
		String reason = paymentTargetRejectionReason();
		if (reason != null) {
			throw new IllegalArgumentException(reason);
		}
	}

	private String paymentTargetRejectionReason() {
		if (salesType != SalesType.DIRECT) {
			return "경매 판매전표는 경매장 정산에서 입금을 확인해야 합니다.";
		}
		if (isCanceled()) {
			return "취소된 전표는 입금을 확인할 수 없습니다.";
		}
		return null;
	}

	public void updateSalesStatus(String salesStatus) {
		String nextStatus = normalizeStatus(salesStatus);
		if (nextStatus.equals(this.salesStatus)) {
			return;
		}
		if (isCanceled()) {
			throw new IllegalArgumentException("취소된 전표는 상태를 변경할 수 없습니다.");
		}
		if (isOutboundCompleted() && !STATUS_CANCELED.equals(nextStatus)) {
			throw new IllegalArgumentException("출고 완료된 전표는 판매 상태를 변경할 수 없습니다.");
		}
		if (!STATUS_CANCELED.equals(nextStatus) && !completionStatus(salesType).equals(nextStatus)) {
			throw new IllegalArgumentException("판매 유형에 맞지 않는 판매 상태입니다.");
		}
		this.salesStatus = nextStatus;
	}

	public boolean isCanceled() {
		return STATUS_CANCELED.equals(salesStatus);
	}

	public void assignAuctionShipment(Long auctionShipmentId) {
		this.auctionShipmentId = auctionShipmentId;
	}

	public void clearAuctionShipment() {
		this.auctionShipmentId = null;
	}

	public boolean isOutboundCompleted() {
		return STATUS_DIRECT_OUTBOUND_COMPLETED.equals(salesStatus)
				|| STATUS_AUCTION_SHIPMENT_COMPLETED.equals(salesStatus);
	}

	private void recalculateAmounts() {
		this.totalAmount = this.items.stream().mapToInt(SalesSlipItem::getAmount).sum();
		this.remainingAmount = Math.max(0L, this.totalAmount.longValue() - getPaidAmount());
	}

	private String validateInitialSalesStatus(SalesType salesType, String salesStatus) {
		String normalized = normalizeStatus(salesStatus);
		if (!STATUS_DRAFT.equals(normalized) && !completionStatus(salesType).equals(normalized)) {
			throw new IllegalArgumentException("판매 전표는 작성중 또는 판매 유형에 맞는 완료 상태로만 생성할 수 있습니다.");
		}
		return normalized;
	}

	private String completionStatus(SalesType salesType) {
		if (salesType == null) {
			throw new IllegalArgumentException("판매 유형이 필요합니다.");
		}
		return salesType == SalesType.DIRECT ? STATUS_DIRECT_OUTBOUND_COMPLETED : STATUS_AUCTION_SHIPMENT_COMPLETED;
	}

	private String normalizeStatus(String salesStatus) {
		if (salesStatus == null || salesStatus.isBlank()) {
			throw new IllegalArgumentException("판매 상태가 필요합니다.");
		}
		return salesStatus.trim();
	}

}
