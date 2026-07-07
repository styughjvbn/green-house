package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.PaymentDayMode;
import com.greenhouse.backend.settlement.domain.SettlementUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record PartnerSettlementSettingsRequest(
		@NotNull SettlementUnit settlementUnit,
		@NotNull @Min(0) Integer paymentDelayDays,
		@NotNull PaymentDayMode paymentDayMode,
		boolean autoMatchEnabled,
		boolean autoSettleEnabled,
		@NotNull @Min(0) Long amountTolerance,
		@NotNull List<@Size(max = 100) String> depositorAliases,
		boolean allowPrepayment,
		boolean creditAutoApplyEnabled,
		Map<String, Object> ruleJson,
		@Size(max = 1000) String memo) {
}
