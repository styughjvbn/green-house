package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.settlement.application.PaymentService;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class SettlementPartnerQueryTest {

	@Autowired PaymentService paymentService;
	@Autowired EntityManager entityManager;
	@Autowired EntityManagerFactory entityManagerFactory;

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void loadsEventPartnerNamesInOneBatchWithoutChangingOrderOrParentLinks(int partnerCount) {
		for (int index = 0; index < partnerCount; index++) {
			var partner = new BusinessPartner("기존 이름", PartnerType.WHOLESALE, null, null, null, null);
			entityManager.persist(partner);
			var received = PartnerPaymentEvent.received(partner.getId(), LocalDate.of(2026, 9, 6).plusDays(index),
					1_000L, PaymentTargetType.SALES_SLIP, 99881L, "계좌이체", "입금자", null, "메모", "작성자");
			entityManager.persist(received);
			entityManager.persist(PartnerPaymentEvent.manualMatch(received));
			partner.update("변경 이름 " + index, PartnerType.WHOLESALE, null, null, null, null);
		}
		var statistics = flushAndResetStatistics();

		var events = paymentService.getEvents(null, PaymentTargetType.SALES_SLIP, 99881L);

		assertThat(events).hasSize(partnerCount * 2);
		for (int index = 0; index < partnerCount; index++) {
			var match = events.get(index * 2);
			var received = events.get(index * 2 + 1);
			assertThat(match.partnerName()).isEqualTo("변경 이름 " + (partnerCount - index - 1));
			assertThat(match.eventType()).isEqualTo(PaymentEventType.MANUAL_MATCH_CONFIRMED);
			assertThat(match.parentEventId()).isEqualTo(received.id());
			assertThat(received.partnerId()).isEqualTo(match.partnerId());
			assertThat(received.partnerName()).isEqualTo(match.partnerName());
		}
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
	}

	private Statistics flushAndResetStatistics() {
		entityManager.flush();
		entityManager.clear();
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		return statistics;
	}
}
