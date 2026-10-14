package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.payment.repository.PartnerPaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnassignedReceiptTests {
  @Autowired MockMvc mvc;
  @Autowired BusinessPartnerRepository partners;
  @Autowired PartnerPaymentEventRepository events;
  Long partnerId;

  @BeforeEach
  void seed() {
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner("미배분 수납", PartnerType.WHOLESALE, null, null, null, null))
            .getId();
  }

  @Test
  void receivesCashOnceWithoutCreatingAMatchOrChangingReceivable() throws Exception {
    long id = receive("receipt", 100);
    assertThat(receive("receipt", 100)).isEqualTo(id);
    assertThat(events.count()).isEqualTo(1);
    mvc.perform(get("/api/business-partners/{id}/balance-summary", partnerId))
        .andExpect(jsonPath("$.data.unappliedPaymentAmount").value(100))
        .andExpect(jsonPath("$.data.creditBalance").value(0))
        .andExpect(jsonPath("$.data.receivableBalance").value(0));
    mvc.perform(
            get("/api/partner-payment-events/page")
                .param("partnerId", partnerId.toString())
                .param("targetType", "NONE")
                .param("eventType", "PAYMENT_RECEIVED"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].unassignedCancellationAllowed").value(true));
  }

  @Test
  void rejectsChangedReceiptKeyAndMissingReasonBeforeAnyWrite() throws Exception {
    receive("receipt", 100);
    mvc.perform(
            post("/api/business-partners/{id}/payment-receipts", partnerId)
                .contentType("application/json")
                .content(payload("receipt", 200)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    mvc.perform(
            post("/api/business-partners/{id}/payment-receipts/1/cancel", partnerId)
                .contentType("application/json")
                .content(
                    """
            {"correctionDate":"2026-10-08","reason":" ","idempotencyKey":"cancel"}
            """))
        .andExpect(status().isBadRequest());
    assertThat(events.count()).isEqualTo(1);
  }

  @Test
  void cancelsInputPreservingCashEvidenceAndReplaysBothCommands() throws Exception {
    long id = receive("receipt", 100);
    String correction =
        """
        {"correctionDate":"2026-10-08","reason":"금액 오입력","idempotencyKey":"cancel"}
        """;
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post("/api/business-partners/{id}/payment-receipts/{receipt}/cancel", partnerId, id)
                  .contentType("application/json")
                  .content(correction))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.eventType").value("ADJUSTMENT"))
          .andExpect(jsonPath("$.data.parentEventId").value(id))
          .andExpect(jsonPath("$.data.memo").value("금액 오입력"));
    }
    assertThat(receive("receipt", 100)).isEqualTo(id);
    assertThat(events.count()).isEqualTo(2);
    var original = events.findById(id).orElseThrow();
    assertThat(original.getAmount()).isEqualTo(100);
    assertThat(original.getCreatedBy()).isEqualTo("관리자");
    assertThat(original.getEventDate()).hasToString("2026-10-08");
    assertThat(original.isUnassignedCancellationAllowed()).isFalse();
    mvc.perform(get("/api/business-partners/{id}/balance-summary", partnerId))
        .andExpect(jsonPath("$.data.unappliedPaymentAmount").value(0));
    mvc.perform(
            post("/api/business-partners/{id}/payment-receipts/{receipt}/cancel", partnerId, id)
                .contentType("application/json")
                .content(correction.replace("cancel", "new-cancel")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_RECEIPT_CANCELLATION_BLOCKED"));
  }

  @Test
  void preventsCrossPartnerCancellation() throws Exception {
    long id = receive("receipt", 100);
    long other =
        partners
            .saveAndFlush(
                new BusinessPartner("다른 거래처", PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    mvc.perform(
            post("/api/business-partners/{id}/payment-receipts/{receipt}/cancel", other, id)
                .contentType("application/json")
                .content(
                    """
          {"correctionDate":"2026-10-08","reason":"오입력","idempotencyKey":"cancel"}
          """))
        .andExpect(status().isNotFound());
    assertThat(events.findById(id).orElseThrow().isUnassignedCancellationAllowed()).isTrue();
  }

  @Test
  void overflowRollsBackTheNewReceiptWithoutCorruptingTheExistingBalance() throws Exception {
    receive("max", Long.MAX_VALUE);
    mvc.perform(
            post("/api/business-partners/{id}/payment-receipts", partnerId)
                .contentType("application/json")
                .content(payload("overflow", 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_BALANCE_LIMIT_EXCEEDED"));
  }

  private long receive(String key, long amount) throws Exception {
    var result =
        mvc.perform(
                post("/api/business-partners/{id}/payment-receipts", partnerId)
                    .contentType("application/json")
                    .content(payload(key, amount)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetType").value("NONE"))
            .andReturn();
    return JsonMapper.builder()
        .build()
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }

  private String payload(String key, long amount) {
    return """
        {"amount":%d,"paymentDate":"2026-10-08","idempotencyKey":"%s","memo":"수동 기록"}
        """
        .formatted(amount, key);
  }
}
