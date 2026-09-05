package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.settlement.domain.PaymentDayMode;
import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import com.greenhouse.backend.settlement.repository.PartnerSettlementSettingsRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpectedPaymentDateCalculator {
	private final PartnerSettlementSettingsRepository settingsRepository;

	public LocalDate calculate(Long partnerId, LocalDate baseDate) {
		var settings = settingsRepository.findByPartnerId(partnerId).orElse(null);
		return calculate(baseDate, settings);
	}

	public Map<PaymentDateTarget, LocalDate> calculateAll(Collection<PaymentDateTarget> targets) {
		if (targets.isEmpty()) {
			return Map.of();
		}
		var settingsByPartnerId = settingsRepository.findByPartnerIdIn(
				targets.stream().map(PaymentDateTarget::partnerId).collect(Collectors.toSet()))
				.stream()
				.collect(Collectors.toMap(PartnerSettlementSettings::getPartnerId, Function.identity()));
		return targets.stream().collect(Collectors.toMap(
				Function.identity(),
				target -> calculate(target.baseDate(), settingsByPartnerId.get(target.partnerId()))));
	}

	private LocalDate calculate(
			LocalDate baseDate,
			PartnerSettlementSettings settings) {
		if (settings == null || settings.getPaymentDelayDays() == 0)
			return baseDate;
		if (settings.getPaymentDayMode() == PaymentDayMode.CALENDAR_DAY) {
			return baseDate.plusDays(settings.getPaymentDelayDays());
		}

		LocalDate result = baseDate;
		int remainingDays = settings.getPaymentDelayDays();
		while (remainingDays > 0) {
			result = result.plusDays(1);
			switch (result.getDayOfWeek()) {
				case SATURDAY, SUNDAY -> {
				}
				default -> remainingDays--;
			}
		}
		return result;
	}

	public record PaymentDateTarget(Long partnerId, LocalDate baseDate) {
	}
}
