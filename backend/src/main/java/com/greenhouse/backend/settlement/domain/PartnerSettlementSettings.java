package com.greenhouse.backend.settlement.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "partner_settlement_settings")
public class PartnerSettlementSettings extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", nullable = false, unique = true)
	private BusinessPartner partner;

	@Enumerated(EnumType.STRING)
	@Column(name = "settlement_unit", nullable = false)
	private SettlementUnit settlementUnit;

	@Column(name = "payment_delay_days", nullable = false)
	private Integer paymentDelayDays;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_day_mode", nullable = false)
	private PaymentDayMode paymentDayMode;

	@Column(name = "auto_match_enabled", nullable = false)
	private boolean autoMatchEnabled;

	@Column(name = "auto_settle_enabled", nullable = false)
	private boolean autoSettleEnabled;

	@Column(name = "amount_tolerance", nullable = false)
	private Long amountTolerance;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "depositor_aliases", columnDefinition = "jsonb")
	private List<String> depositorAliases;

	@Column(name = "allow_prepayment", nullable = false)
	private boolean allowPrepayment;

	@Column(name = "credit_auto_apply_enabled", nullable = false)
	private boolean creditAutoApplyEnabled;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "rule_json", columnDefinition = "jsonb")
	private Map<String, Object> ruleJson;

	@Column(columnDefinition = "text")
	private String memo;

	public PartnerSettlementSettings(BusinessPartner partner) {
		this.partner = partner;
		this.settlementUnit = partner.getPartnerType() == PartnerType.AUCTION_HOUSE
				? SettlementUnit.AUCTION_DATE
				: SettlementUnit.SALES_SLIP;
		this.paymentDelayDays = 0;
		this.paymentDayMode = PaymentDayMode.CALENDAR_DAY;
		this.autoMatchEnabled = false;
		this.autoSettleEnabled = false;
		this.amountTolerance = 0L;
		this.depositorAliases = new ArrayList<>();
		this.allowPrepayment = false;
		this.creditAutoApplyEnabled = false;
	}

	public void update(
			SettlementUnit settlementUnit,
			Integer paymentDelayDays,
			PaymentDayMode paymentDayMode,
			boolean autoMatchEnabled,
			boolean autoSettleEnabled,
			Long amountTolerance,
			List<String> depositorAliases,
			boolean allowPrepayment,
			boolean creditAutoApplyEnabled,
			Map<String, Object> ruleJson,
			String memo) {
		this.settlementUnit = settlementUnit;
		this.paymentDelayDays = paymentDelayDays;
		this.paymentDayMode = paymentDayMode;
		this.autoMatchEnabled = autoMatchEnabled;
		this.autoSettleEnabled = autoSettleEnabled;
		this.amountTolerance = amountTolerance;
		this.depositorAliases = new ArrayList<>(depositorAliases);
		this.allowPrepayment = allowPrepayment;
		this.creditAutoApplyEnabled = creditAutoApplyEnabled;
		this.ruleJson = ruleJson;
		this.memo = memo;
	}
}
