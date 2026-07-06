package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class SalesSlipNumberGenerator {
	private final SalesSlipRepository salesSlipRepository;

	public SalesSlipNumberGenerator(SalesSlipRepository salesSlipRepository) {
		this.salesSlipRepository = salesSlipRepository;
	}

	public String generate(LocalDate saleDate, SalesType salesType) {
		long sequence = salesSlipRepository.countBySaleDate(saleDate) + 1;
		String prefix = salesType == SalesType.AUCTION ? "A" : "S";
		return prefix + saleDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%03d", sequence);
	}
}
