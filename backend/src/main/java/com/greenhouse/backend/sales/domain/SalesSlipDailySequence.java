package com.greenhouse.backend.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sales_slip_daily_sequences")
public class SalesSlipDailySequence {

	@Id
	@Column(name = "sale_date", nullable = false)
	private LocalDate saleDate;

	@Column(name = "last_value", nullable = false)
	private Long lastValue;
}
