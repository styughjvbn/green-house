package com.greenhouse.backend.sales.domain;

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
@Table(name = "sales_slips")
public class SalesSlip extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "slip_number", nullable = false, unique = true)
	private String slipNumber;

	@Column(name = "sale_date", nullable = false)
	private LocalDate saleDate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@Column(name = "total_amount", nullable = false)
	private Integer totalAmount;

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

	public Long getId() {
		return id;
	}
}
