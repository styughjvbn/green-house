package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_slips")
public class SalesSlip extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "slip_number", nullable = false, unique = true)
	private String slipNumber;

	@Column(name = "sale_date", nullable = false)
	private LocalDate saleDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "sales_type")
	private SalesType salesType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "auction_shipment_id", unique = true)
	private AuctionShipment auctionShipment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", nullable = false)
	private BusinessPartner partner;

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

	protected SalesSlip() {
	}

	public SalesSlip(
		String slipNumber,
		LocalDate saleDate,
		SalesType salesType,
		AuctionShipment auctionShipment,
		BusinessPartner partner,
		String paymentStatus,
		String salesStatus,
		String paymentMethod,
		String memo
	) {
		this.slipNumber = slipNumber;
		this.saleDate = saleDate;
		this.salesType = salesType;
		this.auctionShipment = auctionShipment;
		this.partner = partner;
		this.paymentStatus = paymentStatus;
		this.salesStatus = salesStatus;
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
		this.totalAmount = this.items.stream()
			.mapToInt(SalesSlipItem::getAmount)
			.sum();
		this.remainingAmount = Math.max(0L, this.totalAmount.longValue() - getPaidAmount());
	}

	public void updateExpectedPaymentDate(LocalDate expectedPaymentDate) {
		this.expectedPaymentDate = expectedPaymentDate;
	}

	public void recordPayment(Long amount) {
		if (amount <= 0) throw new IllegalArgumentException("입금액은 0원보다 커야 합니다.");
		if (amount > getRemainingAmount()) throw new IllegalArgumentException("입금액은 현재 잔액을 초과할 수 없습니다.");
		this.paidAmount = getPaidAmount() + amount;
		this.remainingAmount = Math.max(0L, totalAmount.longValue() - paidAmount);
		this.paymentStatus = remainingAmount == 0 ? "입금 완료" : "부분입금";
	}

	public Long getId() {
		return id;
	}

	public String getSlipNumber() {
		return slipNumber;
	}

	public LocalDate getSaleDate() {
		return saleDate;
	}

	public SalesType getSalesType() {
		return salesType == null ? SalesType.DIRECT : salesType;
	}

	public AuctionShipment getAuctionShipment() {
		return auctionShipment;
	}

	public BusinessPartner getPartner() {
		return partner;
	}

	public Integer getTotalAmount() {
		return totalAmount;
	}

	public LocalDate getExpectedPaymentDate() {
		return expectedPaymentDate == null ? saleDate : expectedPaymentDate;
	}

	public Long getPaidAmount() {
		return paidAmount == null ? 0L : paidAmount;
	}

	public Long getRemainingAmount() {
		return remainingAmount == null ? totalAmount.longValue() : remainingAmount;
	}

	public String getPaymentStatus() {
		return paymentStatus;
	}

	public String getSalesStatus() {
		return salesStatus;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public String getMemo() {
		return memo;
	}

	public List<SalesSlipItem> getItems() {
		return items;
	}
}
