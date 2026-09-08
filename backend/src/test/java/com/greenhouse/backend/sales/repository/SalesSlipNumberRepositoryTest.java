package com.greenhouse.backend.sales.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesSlipNumberRepositoryTest {

	@Autowired
	SalesSlipNumberRepository repository;

	@Test
	void incrementsEachSaleDateIndependently() {
		LocalDate firstDate = LocalDate.of(2030, 1, 2);
		LocalDate secondDate = LocalDate.of(2030, 1, 3);

		assertThat(repository.nextDailySequence(firstDate)).isEqualTo(1L);
		assertThat(repository.nextDailySequence(firstDate)).isEqualTo(2L);
		assertThat(repository.nextDailySequence(secondDate)).isEqualTo(1L);
	}

}
