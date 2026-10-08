package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.payment.PaymentAllocationReader;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class PaymentAllocationPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private PaymentAllocationReader reader;
  @Autowired private PartnerPaymentEventRepository events;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private PlatformTransactionManager transactionManager;
  private Long partnerId;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE partner_payment_events CONTINUE IDENTITY CASCADE");
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "배분 대사 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
  }

  @ParameterizedTest
  @EnumSource(
      value = PaymentTargetType.class,
      names = {"SALES_SLIP", "AUCTION_SETTLEMENT"})
  void countsConfirmedLinksRatherThanReceivedPlusMatchedMoneyAndDoesNotMutateFacts(
      PaymentTargetType type) {
    var first = received(type, 100L, 400L);
    var second = received(type, 100L, 600L);
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(first));
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(second));
    var before = jdbc.queryForList("select * from partner_payment_events order by id");
    var sums = reader.findAll(type, List.of(100L, 101L));
    assertThat(sums.get(100L).amount()).isEqualByComparingTo("1000");
    assertThat(sums.get(100L).partnerId()).isEqualTo(partnerId);
    assertThat(sums.get(100L).reviewRequired()).isFalse();
    assertThat(sums.get(101L).amount()).isZero();
    assertThat(sums.get(101L).reviewRequired()).isFalse();
    assertThat(jdbc.queryForList("select * from partner_payment_events order by id"))
        .isEqualTo(before);
    assertThat(reader.findAll(type, List.of())).isEmpty();
  }

  @Test
  void flagsDuplicateLinksButDoesNotAllocateTheSameMoneyTwice() {
    var cash = received(PaymentTargetType.SALES_SLIP, 100L, 400L);
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(cash));
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(cash));
    var sum = reader.findAll(PaymentTargetType.SALES_SLIP, List.of(100L)).get(100L);
    assertThat(sum.amount()).isEqualByComparingTo("400");
    assertThat(sum.reviewRequired()).isTrue();
    assertThat(events.count()).isEqualTo(3);
  }

  @Test
  void leavesUnmatchedAndCancelledAndUnknownFactsForReview() {
    var unmatched = received(PaymentTargetType.SALES_SLIP, 100L, 100L);
    var cancelled = received(PaymentTargetType.SALES_SLIP, 101L, 200L);
    var link = events.saveAndFlush(PartnerPaymentEvent.manualMatch(cancelled));
    jdbc.update(
        "update partner_payment_events set status = 'CANCELLED' where id = ?", link.getId());
    var unknown = received(PaymentTargetType.SALES_SLIP, 102L, 300L);
    jdbc.update(
        "update partner_payment_events set event_type = 'ADJUSTMENT' where id = ?",
        unknown.getId());
    var before = jdbc.queryForList("select * from partner_payment_events order by id");
    var sums = reader.findAll(PaymentTargetType.SALES_SLIP, List.of(100L, 101L, 102L));
    assertThat(sums.values())
        .allSatisfy(
            sum -> {
              assertThat(sum.amount()).isZero();
              assertThat(sum.reviewRequired()).isTrue();
            });
    assertThat(jdbc.queryForList("select * from partner_payment_events order by id"))
        .isEqualTo(before);
    assertThat(unmatched.getId()).isNotNull();
  }

  private PartnerPaymentEvent received(PaymentTargetType type, Long target, Long amount) {
    return events.saveAndFlush(
        PartnerPaymentEvent.received(
            partnerId,
            LocalDate.of(2026, 10, 7),
            amount,
            type,
            target,
            "계좌이체",
            null,
            UUID.randomUUID().toString(),
            null,
            "테스트"));
  }

  @Test
  void readsTheNewAllocationInTheWritingTransactionAndRollsBackTogether() {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          var cash =
                              events.save(
                                  PartnerPaymentEvent.received(
                                      partnerId,
                                      LocalDate.of(2026, 10, 7),
                                      400L,
                                      PaymentTargetType.SALES_SLIP,
                                      100L,
                                      null,
                                      null,
                                      "same-transaction",
                                      null,
                                      "테스트"));
                          events.save(PartnerPaymentEvent.manualMatch(cash));
                          var sum =
                              reader.findAll(PaymentTargetType.SALES_SLIP, List.of(100L)).get(100L);
                          assertThat(sum.amount()).isEqualByComparingTo("400");
                          assertThat(sum.reviewRequired()).isFalse();
                          throw new IllegalStateException("rollback-allocation");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("rollback-allocation");
    assertThat(events.count()).isZero();
    assertThat(reader.findAll(PaymentTargetType.SALES_SLIP, List.of(100L)).get(100L).amount())
        .isZero();
  }

  @Test
  void pageSizeDoesNotCreatePerTargetQueriesOrEntityLoads() {
    var cash = received(PaymentTargetType.SALES_SLIP, 100L, 400L);
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(cash));
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    for (int size : new int[] {1, 100}) {
      statistics.clear();
      var result =
          reader.findAll(
              PaymentTargetType.SALES_SLIP, LongStream.range(100, 100 + size).boxed().toList());
      assertThat(result).hasSize(size);
      assertThat(result.get(100L).amount()).isEqualByComparingTo("400");
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
      assertThat(statistics.getEntityLoadCount()).isZero();
    }
  }
}
