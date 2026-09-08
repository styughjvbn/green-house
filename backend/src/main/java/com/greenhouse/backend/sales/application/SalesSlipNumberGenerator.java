package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesSlipNumberRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipNumberGenerator {

	private final SalesSlipNumberRepository numberRepository;

	public String generate(LocalDate saleDate, SalesType salesType) {
		long sequence = numberRepository.nextDailySequence(saleDate);
		String prefix = salesType == SalesType.AUCTION ? "A" : "S";
		return prefix + saleDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%03d", sequence);
	}

}
