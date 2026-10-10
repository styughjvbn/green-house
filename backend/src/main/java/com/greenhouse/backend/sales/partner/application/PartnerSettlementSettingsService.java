package com.greenhouse.backend.sales.partner.application;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.PartnerPrintTemplate;
import com.greenhouse.backend.sales.partner.domain.PartnerSettlementSettings;
import com.greenhouse.backend.sales.partner.repository.PartnerSettlementSettingsRepository;
import com.greenhouse.backend.sales.partner.web.dto.PartnerSettlementSettingsRequest;
import com.greenhouse.backend.sales.partner.web.dto.PartnerSettlementSettingsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PartnerSettlementSettingsService {

  private final PartnerSettlementSettingsRepository settingsRepository;

  private final BusinessPartnerLock partnerLock;

  private final PartnerSettingsAuditSupport auditSupport;

  public PartnerSettlementSettingsResponse getOrCreate(Long partnerId) {
    var context = findOrCreate(partnerId);
    return PartnerSettlementSettingsResponse.from(context.settings(), context.partnerType());
  }

  public PartnerSettlementSettingsResponse update(
      Long partnerId, PartnerSettlementSettingsRequest request) {
    var context = findOrCreate(partnerId);
    var settings = context.settings();
    var before = auditSupport.settingsSnapshot(settings);
    settings.update(
        request.settlementUnit(),
        request.paymentDelayDays(),
        request.paymentDayMode(),
        request.autoMatchEnabled(),
        request.autoSettleEnabled(),
        request.amountTolerance(),
        request.depositorAliases().stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList(),
        request.allowPrepayment(),
        request.creditAutoApplyEnabled(),
        request.ruleJson(),
        normalize(request.memo()));
    var printTemplate =
        request.printTemplate() == null ? settings.getPrintTemplate() : request.printTemplate();
    if (context.partnerType() != PartnerType.AUCTION_HOUSE
        && printTemplate != PartnerPrintTemplate.STANDARD_A5) {
      throw new IllegalArgumentException("경매장 전용 인쇄 양식은 경매장 거래처에만 설정할 수 있습니다.");
    }
    settings.updatePrintSettings(
        printTemplate, normalize(request.shipperCode()), normalize(request.shipperName()));
    var saved = settingsRepository.save(settings);
    auditSupport.recordSettingsUpdate(saved, before, auditSupport.settingsSnapshot(saved));
    return PartnerSettlementSettingsResponse.from(saved, context.partnerType());
  }

  private SettingsContext findOrCreate(Long partnerId) {
    var partner = partnerLock.lockAll(List.of(partnerId)).getFirst();
    var settings =
        settingsRepository
            .findByPartnerId(partnerId)
            .orElseGet(
                () ->
                    settingsRepository.save(
                        new PartnerSettlementSettings(partner.id(), partner.partnerType())));
    return new SettingsContext(settings, partner.partnerType());
  }

  private record SettingsContext(PartnerSettlementSettings settings, PartnerType partnerType) {}

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
