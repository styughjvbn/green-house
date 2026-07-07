package com.greenhouse.backend.settlement.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "partner_balance_summaries")
public class PartnerBalanceSummary extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", nullable = false, unique = true)
	private BusinessPartner partner;

	@Column(name = "credit_balance", nullable = false)
	private Long creditBalance;

	@Column(name = "unapplied_payment_amount", nullable = false)
	private Long unappliedPaymentAmount;

	@Column(name = "receivable_balance", nullable = false)
	private Long receivableBalance;

	@Getter(AccessLevel.NONE)
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "last_payment_event_id")
	private PartnerPaymentEvent lastPaymentEvent;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "summary_json", columnDefinition = "jsonb")
	private Map<String, Object> summaryJson;

	public PartnerBalanceSummary(BusinessPartner partner) {
		this.partner = partner;
		this.creditBalance = 0L;
		this.unappliedPaymentAmount = 0L;
		this.receivableBalance = 0L;
	}

	public void updateReceivableBalance(Long receivableBalance, PartnerPaymentEvent lastPaymentEvent) {
		this.receivableBalance = Math.max(0L, receivableBalance);
		if (lastPaymentEvent != null)
			this.lastPaymentEvent = lastPaymentEvent;
	}
}
