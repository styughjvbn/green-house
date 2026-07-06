package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import com.greenhouse.backend.settlement.domain.PaymentDayMode;
import com.greenhouse.backend.settlement.domain.SettlementUnit;
import java.util.List;
import java.util.Map;

public record PartnerSettlementSettingsResponse(
	Long id,
	Long partnerId,
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
	String memo
) {
	public static PartnerSettlementSettingsResponse from(PartnerSettlementSettings settings) {
		return new PartnerSettlementSettingsResponse(
			settings.getId(), settings.getPartner().getId(), settings.getSettlementUnit(),
			settings.getPaymentDelayDays(), settings.getPaymentDayMode(), settings.isAutoMatchEnabled(),
			settings.isAutoSettleEnabled(), settings.getAmountTolerance(), settings.getDepositorAliases(),
			settings.isAllowPrepayment(), settings.isCreditAutoApplyEnabled(), settings.getRuleJson(),
			settings.getMemo());
	}
}
