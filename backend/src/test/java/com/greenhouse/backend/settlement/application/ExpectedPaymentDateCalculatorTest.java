package com.greenhouse.backend.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.settlement.application.ExpectedPaymentDateCalculator.PaymentDateTarget;
import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import com.greenhouse.backend.settlement.domain.PaymentDayMode;
import com.greenhouse.backend.settlement.domain.SettlementUnit;
import com.greenhouse.backend.settlement.repository.PartnerSettlementSettingsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpectedPaymentDateCalculatorTest {

	private static final LocalDate FRIDAY = LocalDate.of(2026, 7, 3);

	@Mock PartnerSettlementSettingsRepository settingsRepository;
	private ExpectedPaymentDateCalculator calculator;

	@BeforeEach
	void setUp() {
		calculator = new ExpectedPaymentDateCalculator(settingsRepository);
	}

	@Test
	void usesTheBaseDateWhenSettingsDoNotExist() {
		when(settingsRepository.findByPartnerId(1L)).thenReturn(Optional.empty());
		assertThat(calculator.calculate(1L, FRIDAY)).isEqualTo(FRIDAY);
	}

	@Test
	void calendarDaysIncludeWeekends() {
		when(settingsRepository.findByPartnerId(1L))
				.thenReturn(Optional.of(settings(1L, 2, PaymentDayMode.CALENDAR_DAY)));
		assertThat(calculator.calculate(1L, FRIDAY)).isEqualTo(LocalDate.of(2026, 7, 5));
	}

	@Test
	void businessDaysSkipWeekendsAcrossSeveralWeeks() {
		when(settingsRepository.findByPartnerId(1L))
				.thenReturn(Optional.of(settings(1L, 6, PaymentDayMode.BUSINESS_DAY)));
		assertThat(calculator.calculate(1L, FRIDAY)).isEqualTo(LocalDate.of(2026, 7, 13));
	}

	@Test
	void zeroDelayKeepsTheBaseDateEvenOnAWeekend() {
		when(settingsRepository.findByPartnerId(1L))
				.thenReturn(Optional.of(settings(1L, 0, PaymentDayMode.BUSINESS_DAY)));
		assertThat(calculator.calculate(1L, FRIDAY.plusDays(1))).isEqualTo(FRIDAY.plusDays(1));
	}

	@Test
	void loadsSettingsOnceForAllPartnersAndDates() {
		var friday = new PaymentDateTarget(1L, FRIDAY);
		var saturday = new PaymentDateTarget(1L, FRIDAY.plusDays(1));
		var withoutSettings = new PaymentDateTarget(2L, FRIDAY);
		when(settingsRepository.findByPartnerIdIn(Set.of(1L, 2L)))
				.thenReturn(List.of(settings(1L, 2, PaymentDayMode.BUSINESS_DAY)));

		assertThat(calculator.calculateAll(List.of(friday, saturday, withoutSettings)))
				.containsEntry(friday, LocalDate.of(2026, 7, 7))
				.containsEntry(saturday, LocalDate.of(2026, 7, 7))
				.containsEntry(withoutSettings, FRIDAY).hasSize(3);
		verify(settingsRepository).findByPartnerIdIn(Set.of(1L, 2L));
		verifyNoMoreInteractions(settingsRepository);
	}

	@Test
	void doesNotQueryForEmptyTargets() {
		assertThat(calculator.calculateAll(List.of())).isEmpty();
		verifyNoInteractions(settingsRepository);
	}

	private PartnerSettlementSettings settings(Long partnerId, int delay, PaymentDayMode mode) {
		var settings = new PartnerSettlementSettings(partnerId, PartnerType.WHOLESALE);
		settings.update(SettlementUnit.SALES_SLIP, delay, mode, false, false, 0L,
				List.of(), false, false, null, null);
		return settings;
	}
}
