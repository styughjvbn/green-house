package com.greenhouse.backend.sales.dto.partner;

import com.greenhouse.backend.sales.domain.partner.PaymentDayMode;
import com.greenhouse.backend.sales.domain.partner.SettlementUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record PartnerSettlementSettingsRequest(
    @Schema(
            description =
                "저장할 선호 정산 단위. MONTHLY_BATCH 저장은 월간 실행을 활성화하지 않으며 실제 지원 범위는 응답 capabilities를 확인한다.")
        @NotNull
        SettlementUnit settlementUnit,
    @NotNull @Min(0) Integer paymentDelayDays,
    @NotNull PaymentDayMode paymentDayMode,
    @Schema(description = "자동 매칭의 보관용 선호값. 현재 자동 매칭 실행은 제공하지 않는다.") boolean autoMatchEnabled,
    @Schema(description = "자동 정산의 보관용 선호값. 현재 자동 정산 실행은 제공하지 않는다.") boolean autoSettleEnabled,
    @NotNull @Min(0) Long amountTolerance,
    @NotNull List<@Size(max = 100) String> depositorAliases,
    @Schema(description = "선입금의 보관용 선호값. 현재 예치금 처리는 제공하지 않는다.") boolean allowPrepayment,
    @Schema(description = "선입금 자동 차감의 보관용 선호값. 현재 실행은 제공하지 않는다.") boolean creditAutoApplyEnabled,
    @Schema(description = "보관용 규칙 JSON. 현재 결과 수신/파싱 또는 정산 실행에 적용하지 않는다.")
        Map<String, Object> ruleJson,
    @Size(max = 1000) String memo) {}
