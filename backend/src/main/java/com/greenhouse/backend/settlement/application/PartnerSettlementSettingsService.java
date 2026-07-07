package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import com.greenhouse.backend.settlement.dto.PartnerSettlementSettingsRequest;
import com.greenhouse.backend.settlement.dto.PartnerSettlementSettingsResponse;
import com.greenhouse.backend.settlement.repository.PartnerSettlementSettingsRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PartnerSettlementSettingsService {
	private final PartnerSettlementSettingsRepository settingsRepository;
	private final BusinessPartnerReader partnerReader;

	public PartnerSettlementSettingsResponse getOrCreate(Long partnerId) {
		return PartnerSettlementSettingsResponse.from(findOrCreate(partnerId));
	}

	public PartnerSettlementSettingsResponse update(Long partnerId, PartnerSettlementSettingsRequest request) {
		var settings = findOrCreate(partnerId);
		settings.update(
				request.settlementUnit(), request.paymentDelayDays(), request.paymentDayMode(),
				request.autoMatchEnabled(), request.autoSettleEnabled(), request.amountTolerance(),
				request.depositorAliases().stream().map(String::trim).filter(value -> !value.isEmpty()).distinct()
						.toList(),
				request.allowPrepayment(), request.creditAutoApplyEnabled(), request.ruleJson(),
				normalize(request.memo()));
		return PartnerSettlementSettingsResponse.from(settingsRepository.save(settings));
	}

	private PartnerSettlementSettings findOrCreate(Long partnerId) {
		return settingsRepository.findByPartnerId(partnerId).orElseGet(() -> {
			var partner = partnerReader.get(partnerId);
			return settingsRepository.save(new PartnerSettlementSettings(partner));
		});
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
