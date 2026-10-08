package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import com.greenhouse.backend.farm.structure.domain.House;
import com.greenhouse.backend.farm.structure.domain.PhysicalBed;
import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.document.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.domain.SalesSlipItem;
import com.greenhouse.backend.sales.document.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.document.repository.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.payment.repository.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.support.DirectSaleFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SalesSlipAuditIntegrationTest extends AbstractBackendIntegrationTest {
  @Autowired JdbcTemplate jdbc;

  @Autowired AuditEventRepository auditEventRepository;

  @Autowired BusinessPartnerRepository partnerRepository;

  @Autowired SalesSlipRepository salesSlipRepository;

  @Autowired SalesInventoryMovementRepository inventoryMovementRepository;

  @Autowired PartnerBalanceSummaryRepository balanceSummaryRepository;

  @Test
  void recordsDirectSlipEditAndCancellation() throws Exception {
    House house = new House(9920, "판매 감사동");
    PhysicalBed bed = new PhysicalBed(1, 1);
    BedZone zone = new BedZone("왼쪽", BedZoneSide.LEFT, 1);
    bed.addBedZone(zone);
    house.addPhysicalBed(bed);
    houseRepository.saveAndFlush(house);
    Variety variety =
        varietyRepository.saveAndFlush(
            new Variety(
                "SALE-AUDIT-" + System.nanoTime(),
                "판매감사속",
                "판매감사품종",
                null,
                "4인치",
                true,
                true,
                null,
                null));
    OrchidGroup group =
        new OrchidGroup(
            zone,
            variety.getGenus(),
            variety.getName(),
            20,
            "4인치",
            2,
            "정상",
            1,
            BigDecimal.ONE,
            BigDecimal.TWO);
    group.assignVariety(variety);
    group.reserve(2);
    saveOrchidGroup(group);
    BusinessPartner partner =
        partnerRepository.saveAndFlush(
            new BusinessPartner("판매 감사 거래처", PartnerType.WHOLESALE, null, null, null, null));
    BusinessPartner nextPartner =
        partnerRepository.saveAndFlush(
            new BusinessPartner("판매 수정 거래처", PartnerType.WHOLESALE, null, null, null, null));
    SalesSlip slip =
        new SalesSlip(
            "AUDIT-" + System.nanoTime(),
            LocalDate.of(2026, 8, 1),
            SalesType.DIRECT,
            null,
            partner.getId(),
            "미입금",
            "작성중",
            "현금",
            "최초");
    SalesSlipItem item =
        new SalesSlipItem(null, variety.getName(), variety.getGenus(), "4인치", 2, 1000, "품목");
    item.addAllocation(new SalesSlipItemAllocation(group.getId(), 2));
    slip.addItem(item);
    DirectSaleFixtures.refreshProjection(slip);
    salesSlipRepository.saveAndFlush(slip);
    DirectSaleFixtures.copyTerms(jdbc, slip.getId());

    mockMvc
        .perform(
            put("/api/sales-slips/{id}", slip.getId())
                .with(user("operator"))
                .header("X-Request-Id", "sales-update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{"saleDate":"2026-08-02","salesType":"DIRECT","partnerId":%d,
						 "paymentStatus":"미입금","salesStatus":"작성중","paymentMethod":"계좌이체","memo":"수정",
						 "items":[{"itemName":"%s","genus":"%s","spec":"5인치","quantity":1,
						 "unitPrice":2000,"memo":"수정 품목","allocations":[{"orchidGroupId":%d,"quantity":1}]}]}
						"""
                        .formatted(
                            nextPartner.getId(),
                            variety.getName(),
                            variety.getGenus(),
                            group.getId())))
        .andExpect(status().isOk());
    assertThat(
            inventoryMovementRepository.findBySalesSlipIdAndChangeType(
                slip.getId(), SalesInventoryMovementType.SALES_RELEASE))
        .hasSize(1);
    assertThat(
            inventoryMovementRepository.findBySalesSlipIdAndChangeType(
                slip.getId(), SalesInventoryMovementType.SALES_RESERVE))
        .hasSize(1);
    assertThat(
            balanceSummaryRepository
                .findByPartnerId(partner.getId())
                .orElseThrow()
                .getReceivableBalance())
        .isZero();
    assertThat(
            balanceSummaryRepository
                .findByPartnerId(nextPartner.getId())
                .orElseThrow()
                .getReceivableBalance())
        .isEqualTo(2_000L);
    mockMvc
        .perform(
            patch("/api/sales-slips/{id}/sales-status", slip.getId())
                .with(user("operator"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salesStatus\":\"취소\"}"))
        .andExpect(status().isOk());

    var events =
        auditEventRepository.findAll().stream()
            .filter(event -> event.getSource() == AuditSource.SALES_MANAGEMENT)
            .filter(event -> event.getEntityId().equals(slip.getId()))
            .toList();
    assertThat(events)
        .extracting(event -> event.getAction())
        .containsExactly(AuditAction.UPDATED, AuditAction.DEACTIVATED);
    assertThat(events.getFirst().getChangedFields())
        .contains("saleDate", "paymentMethod", "memo", "items");
    assertThat(events.getLast().getChangedFields()).containsExactly("salesStatus");
    assertThat(events)
        .allSatisfy(
            event -> {
              assertThat(event.getEntityType()).isEqualTo("SALES_SLIP");
              assertThat(event.getActorId()).isEqualTo("operator");
            });
  }
}
