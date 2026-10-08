package com.greenhouse.backend.sales.application.partner;

import com.greenhouse.backend.sales.domain.partner.PartnerSettlementSettings;
import com.greenhouse.backend.sales.partner.api.ExpectedPaymentDateApi;
import com.greenhouse.backend.sales.repository.partner.PartnerSettlementSettingsRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpectedPaymentDateCalculator implements ExpectedPaymentDateApi {

  private final PartnerSettlementSettingsRepository settingsRepository;

  @Override
  public LocalDate calculate(Long partnerId, LocalDate baseDate) {
    var settings = settingsRepository.findByPartnerId(partnerId).orElse(null);
    return calculate(baseDate, settings);
  }

  @Override
  public Map<PaymentDateTarget, LocalDate> calculateAll(Collection<PaymentDateTarget> targets) {
    if (targets.isEmpty()) {
      return Map.of();
    }
    var settingsByPartnerId =
        settingsRepository
            .findByPartnerIdIn(
                targets.stream().map(PaymentDateTarget::partnerId).collect(Collectors.toSet()))
            .stream()
            .collect(
                Collectors.toMap(PartnerSettlementSettings::getPartnerId, Function.identity()));
    return targets.stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                target ->
                    calculate(target.baseDate(), settingsByPartnerId.get(target.partnerId()))));
  }

  private LocalDate calculate(LocalDate baseDate, PartnerSettlementSettings settings) {
    return settings == null ? baseDate : settings.calculateExpectedPaymentDate(baseDate);
  }
}
