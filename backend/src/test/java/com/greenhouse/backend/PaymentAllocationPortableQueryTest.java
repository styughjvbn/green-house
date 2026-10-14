package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.application.PaymentAllocationReader;
import com.greenhouse.backend.sales.payment.domain.PartnerPaymentEvent;
import com.greenhouse.backend.sales.payment.repository.PartnerPaymentEventRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentAllocationPortableQueryTest {
  @Autowired PaymentAllocationReader reader;
  @Autowired BusinessPartnerRepository partners;
  @Autowired PartnerPaymentEventRepository events;

  @Test
  void h2UsesTheSameDeduplicationAndOwnerValidationAsPostgres() {
    var owner =
        partners.saveAndFlush(
            new BusinessPartner("대상 소유자", PartnerType.WHOLESALE, null, null, null, null));
    var other =
        partners.saveAndFlush(
            new BusinessPartner("다른 거래처", PartnerType.WHOLESALE, null, null, null, null));
    var cash =
        events.save(
            PartnerPaymentEvent.received(
                owner.getId(),
                LocalDate.of(2026, 10, 7),
                400L,
                PaymentTargetType.SALES_SLIP,
                9L,
                null,
                null,
                null,
                null,
                "수납자"));
    events.save(PartnerPaymentEvent.manualMatch(cash));
    events.save(PartnerPaymentEvent.manualMatch(cash));
    var wrong =
        events.save(
            PartnerPaymentEvent.received(
                other.getId(),
                LocalDate.of(2026, 10, 7),
                300L,
                PaymentTargetType.SALES_SLIP,
                10L,
                null,
                null,
                null,
                null,
                "수납자"));
    events.save(PartnerPaymentEvent.manualMatch(wrong));
    // No explicit flush: native queries must see participating JPA writes in the same transaction.
    var result =
        reader.findAll(
            PaymentTargetType.SALES_SLIP,
            Map.of(9L, owner.getId(), 10L, owner.getId(), 11L, owner.getId()));
    assertThat(result.get(9L).amount()).isEqualByComparingTo("400");
    assertThat(result.get(9L).reviewRequired()).isTrue();
    assertThat(result.get(10L).amount()).isZero();
    assertThat(result.get(10L).reviewRequired()).isTrue();
    assertThat(result.get(11L).amount()).isZero();
    assertThat(result.get(11L).reviewRequired()).isFalse();
  }

  @Test
  void batchesBoundTargetValuesAtFiveHundredAndKeepsUnpaidTargets() {
    var owner =
        partners.saveAndFlush(
            new BusinessPartner("배분 대상 소유자", PartnerType.WHOLESALE, null, null, null, null));
    var targets =
        LongStream.rangeClosed(1000, 1500)
            .boxed()
            .collect(Collectors.toMap(id -> id, id -> owner.getId()));
    var result = reader.findAll(PaymentTargetType.SALES_SLIP, targets);
    assertThat(result).hasSize(501);
    assertThat(result.values())
        .allSatisfy(
            value -> {
              assertThat(value.amount()).isZero();
              assertThat(value.reviewRequired()).isFalse();
            });
  }
}
