package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.direct.application.SalesPaymentService;
import com.greenhouse.backend.sales.document.domain.*;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.partner.domain.*;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.application.*;
import com.greenhouse.backend.sales.payment.domain.*;
import com.greenhouse.backend.sales.payment.repository.PartnerPaymentEventRepository;
import com.greenhouse.backend.sales.payment.web.dto.*;
import com.greenhouse.backend.support.DirectSaleFixtures;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentAllocationIntegrationTest {
  static final LocalDate DATE = LocalDate.of(2026, 10, 8);
  @Autowired UnassignedReceiptService receipts;
  @Autowired SalesPaymentService manual;
  @Autowired PaymentAllocationService allocations;
  @Autowired PaymentReceiptReader reader;
  @Autowired PartnerPaymentEventRepository events;
  @Autowired SalesSlipRepository documents;
  @Autowired BusinessPartnerRepository partners;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  Long partnerId, first, second;

  @BeforeEach
  void seed() {
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner("배분 테스트", PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    first = document("first");
    second = document("second");
  }

  @Test
  void splitsOneReceiptAcrossDocumentsWithoutReceivingCashAgain() throws Exception {
    long receipt = receive(1500, "cash");
    var request = command("split", line(receipt, first, 1000), line(receipt, second, 500));
    var result = allocations.allocate(partnerId, request);
    assertThat(allocations.allocate(partnerId, request)).isEqualTo(result);
    assertThat(events.count()).isEqualTo(3);
    money(first, 1000, 0);
    money(second, 500, 500);
    assertThat(reader.get(partnerId, receipt).availableAmount()).isZero();
    assertThat(reader.get(partnerId, receipt).reviewRequired()).isFalse();
    assertThat(events.findById(receipt).orElseThrow().getAmount()).isEqualTo(1500);
  }

  @Test
  void combinesSeveralReceiptsForOneDocument() throws Exception {
    long a = receive(600, "cash-a"), b = receive(400, "cash-b");
    allocations.allocate(partnerId, command("combine", line(a, first, 600), line(b, first, 400)));
    money(first, 1000, 0);
    assertThat(events.count()).isEqualTo(4);
  }

  @Test
  void correctionCancelsOriginalAndReallocatesMoneyAtomically() throws Exception {
    long receipt = receive(700, "cash");
    var original = allocations.allocate(partnerId, command("original", line(receipt, first, 700)));
    var request =
        new PaymentAllocationCorrectionRequest(
            DATE,
            original.allocationIds(),
            List.of(line(receipt, first, 200), line(receipt, second, 500)),
            "대상 오선택",
            "correct",
            null);
    var result = allocations.correct(partnerId, request);
    assertThat(allocations.correct(partnerId, request)).isEqualTo(result);
    money(first, 200, 800);
    money(second, 500, 500);
    assertThat(reader.get(partnerId, receipt).reviewRequired()).isFalse();
    assertThat(events.findById(original.allocationIds().getFirst()).orElseThrow().getStatus())
        .isEqualTo(PaymentEventStatus.CANCELLED);
    assertThat(events.findById(result.cancellationIds().getFirst()).orElseThrow().getMemo())
        .isEqualTo("대상 오선택");
  }

  @Test
  void legacyManualAllocationCanBeCanceledAndOriginalPaymentKeyStillReplays() throws Exception {
    var original = new ManualPaymentCommand(400L, DATE, "legacy", null, null, null, null);
    manual.confirmPayment(first, original);
    var allocation =
        events.findAll().stream()
            .filter(event -> event.getEventType() == PaymentEventType.MANUAL_MATCH_CONFIRMED)
            .findFirst()
            .orElseThrow();
    long receiptId = allocation.getParentEvent().getId();
    allocations.correct(
        partnerId,
        new PaymentAllocationCorrectionRequest(
            DATE, List.of(allocation.getId()), List.of(), "연결 오입력", "cancel", null));
    money(first, 0, 1000);
    assertThat(reader.get(partnerId, receiptId).availableAmount()).isEqualTo(400);
    assertThat(reader.get(partnerId, receiptId).reviewRequired()).isFalse();
    assertThat(manual.confirmPayment(first, original).paidAmount()).isZero();
    assertThat(events.count()).isEqualTo(3);
    mvc.perform(get("/api/business-partners/{id}/balance-summary", partnerId))
        .andExpect(jsonPath("$.data.unappliedPaymentAmount").value(400));
  }

  @Test
  void rejectsChangedRequestAndOverallocationsWithStableErrors() throws Exception {
    long receipt = receive(1000, "cash");
    allocations.allocate(partnerId, command("same", line(receipt, first, 200)));
    mvc.perform(
            post("/api/business-partners/{id}/payment-allocations", partnerId)
                .contentType("application/json")
                .content(json(command("same", line(receipt, first, 100)))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void rejectsUnsupportedTargetsBeforeWriting() throws Exception {
    long receipt = receive(1000, "cash");
    mvc.perform(
            post("/api/business-partners/{id}/payment-allocations", partnerId)
                .contentType("application/json")
                .content(
                    json(
                        command(
                            "invalid",
                            new PaymentAllocationLine(
                                receipt, PaymentTargetType.NONE, first, 200L)))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void onlyValidAllocationsAreCancelableAndTargetsUseServerCapabilities() {
    long receipt = receive(700, "cash");
    allocations.allocate(partnerId, command("split", line(receipt, first, 700)));
    assertThat(reader.allocations(partnerId, receipt, 0, 10).content()).hasSize(1);
    assertThat(
            reader
                .allocations(partnerId, receipt, 0, 10)
                .content()
                .getFirst()
                .cancellationAllowed())
        .isTrue();
    assertThat(reader.options(partnerId, PaymentTargetType.SALES_SLIP, "", 0, 10).content())
        .hasSize(2);
  }

  @Test
  void aReceiptCanBeCanceledAfterAllocationCorrectionWithoutInvalidatingTargetHistory()
      throws Exception {
    long receipt = receive(500, "cash");
    var assigned = allocations.allocate(partnerId, command("assign", line(receipt, first, 500)));
    allocations.correct(
        partnerId,
        new PaymentAllocationCorrectionRequest(
            DATE, assigned.allocationIds(), List.of(), "배분 취소", "unlink", null));
    receipts.cancel(
        partnerId, receipt, new CancelUnassignedReceiptRequest(DATE, "입금 오입력", "cancel-receipt"));
    money(first, 0, 1000);
  }

  private long document(String name) {
    var slip =
        new SalesSlip(
            "allocation-" + name + "-" + UUID.randomUUID(),
            DATE,
            SalesType.DIRECT,
            null,
            partnerId,
            "미입금",
            "작성중",
            "계좌이체",
            null);
    slip.addItem(new SalesSlipItem(null, "난", null, "A", 1, 1000, null));
    DirectSaleFixtures.refreshProjection(slip);
    documents.saveAndFlush(slip);
    DirectSaleFixtures.copyTerms(jdbc, slip.getId());
    return slip.getId();
  }

  private long receive(long amount, String key) {
    return receipts
        .receive(partnerId, new ManualPaymentCommand(amount, DATE, key, null, null, null, null))
        .id();
  }

  private PaymentAllocationLine line(long receipt, long target, long amount) {
    return new PaymentAllocationLine(receipt, PaymentTargetType.SALES_SLIP, target, amount);
  }

  private PaymentAllocationRequest command(String key, PaymentAllocationLine... lines) {
    return new PaymentAllocationRequest(DATE, List.of(lines), key, null);
  }

  private String json(Object request) {
    return JsonMapper.builder().build().writeValueAsString(request);
  }

  private void money(long id, long paid, long remaining) throws Exception {
    mvc.perform(get("/api/sales-slips/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paidAmount").value(paid))
        .andExpect(jsonPath("$.data.remainingAmount").value(remaining))
        .andExpect(jsonPath("$.data.financialReviewRequired").value(false));
  }
}
