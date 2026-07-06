package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.settlement.domain.PaymentDayMode;
import com.greenhouse.backend.settlement.repository.PartnerSettlementSettingsRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ExpectedPaymentDateCalculator {
	private final PartnerSettlementSettingsRepository settingsRepository;

	public ExpectedPaymentDateCalculator(PartnerSettlementSettingsRepository settingsRepository) {
		this.settingsRepository = settingsRepository;
	}

	public LocalDate calculate(BusinessPartner partner, LocalDate baseDate) {
		var settings = settingsRepository.findByPartnerId(partner.getId()).orElse(null);
		if (settings == null || settings.getPaymentDelayDays() == 0) return baseDate;
		if (settings.getPaymentDayMode() == PaymentDayMode.CALENDAR_DAY) {
			return baseDate.plusDays(settings.getPaymentDelayDays());
		}

		LocalDate result = baseDate;
		int remainingDays = settings.getPaymentDelayDays();
		while (remainingDays > 0) {
			result = result.plusDays(1);
			switch (result.getDayOfWeek()) {
				case SATURDAY, SUNDAY -> { }
				default -> remainingDays--;
			}
		}
		return result;
	}
}
