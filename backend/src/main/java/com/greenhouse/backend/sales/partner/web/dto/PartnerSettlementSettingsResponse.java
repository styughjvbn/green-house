package com.greenhouse.backend.sales.partner.web.dto;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.PartnerSettlementSettings;
import com.greenhouse.backend.sales.partner.domain.PaymentDayMode;
import com.greenhouse.backend.sales.partner.domain.SettlementCapabilities;
import com.greenhouse.backend.sales.partner.domain.SettlementUnit;
import io.swagger.v3.oas.annotations.media.Schema;
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
    String memo,
    @Schema(
            description = "현재 거래처의 정산 실행 지원 범위. 저장된 설정값과 독립적이며 권한 또는 특정 전표의 실행 가능 여부를 뜻하지 않는다.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            accessMode = Schema.AccessMode.READ_ONLY)
        SettlementCapabilities capabilities) {
  public static PartnerSettlementSettingsResponse from(
      PartnerSettlementSettings settings, PartnerType partnerType) {
    return new PartnerSettlementSettingsResponse(
        settings.getId(),
        settings.getPartnerId(),
        settings.getSettlementUnit(),
        settings.getPaymentDelayDays(),
        settings.getPaymentDayMode(),
        settings.isAutoMatchEnabled(),
        settings.isAutoSettleEnabled(),
        settings.getAmountTolerance(),
        settings.getDepositorAliases(),
        settings.isAllowPrepayment(),
        settings.isCreditAutoApplyEnabled(),
        settings.getRuleJson(),
        settings.getMemo(),
        SettlementCapabilities.forPartnerType(partnerType));
  }
}
