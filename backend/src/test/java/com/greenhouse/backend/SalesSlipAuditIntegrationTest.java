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
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SalesSlipAuditIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired AuditEventRepository auditEventRepository;
	@Autowired BusinessPartnerRepository partnerRepository;
	@Autowired SalesSlipRepository salesSlipRepository;

	@Test
	void recordsDirectSlipEditAndCancellation() throws Exception {
		House house = new House(9920, "판매 감사동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		BedZone zone = new BedZone("왼쪽", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone); house.addPhysicalBed(bed); houseRepository.saveAndFlush(house);
		Variety variety = varietyRepository.saveAndFlush(new Variety(
				"SALE-AUDIT-" + System.nanoTime(), "판매감사속", "판매감사품종", null,
				"4인치", true, true, null, null));
		OrchidGroup group = new OrchidGroup(zone, variety.getGenus(), variety.getName(), 20, "4인치", 2,
				"정상", 1, BigDecimal.ONE, BigDecimal.TWO);
		group.assignVariety(variety);
		group.reserve(2);
		orchidGroupRepository.saveAndFlush(group);
		BusinessPartner partner = partnerRepository.saveAndFlush(new BusinessPartner(
				"판매 감사 거래처", PartnerType.WHOLESALE, null, null, null, null));
		SalesSlip slip = new SalesSlip("AUDIT-" + System.nanoTime(), LocalDate.of(2026, 8, 1),
				SalesType.DIRECT, null, partner, "미입금", "작성중", "현금", "최초");
		SalesSlipItem item = new SalesSlipItem(null, variety.getName(), variety.getGenus(), "4인치", 2, 1000, "품목");
		item.addAllocation(new SalesSlipItemAllocation(group, 2));
		slip.addItem(item);
		salesSlipRepository.saveAndFlush(slip);

		mockMvc.perform(put("/api/sales-slips/{id}", slip.getId())
				.with(user("operator"))
				.header("X-Request-Id", "sales-update")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"saleDate":"2026-08-02","salesType":"DIRECT","partnerId":%d,
						 "paymentStatus":"미입금","salesStatus":"작성중","paymentMethod":"계좌이체","memo":"수정",
						 "items":[{"itemName":"%s","genus":"%s","spec":"5인치","quantity":1,
						 "unitPrice":2000,"memo":"수정 품목","allocations":[{"orchidGroupId":%d,"quantity":1}]}]}
						""".formatted(partner.getId(), variety.getName(), variety.getGenus(), group.getId())))
				.andExpect(status().isOk());
		mockMvc.perform(patch("/api/sales-slips/{id}/sales-status", slip.getId())
				.with(user("operator"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"salesStatus\":\"취소\"}"))
				.andExpect(status().isOk());

		var events = auditEventRepository.findAll().stream()
				.filter(event -> event.getSource() == AuditSource.SALES_MANAGEMENT)
				.filter(event -> event.getEntityId().equals(slip.getId()))
				.toList();
		assertThat(events).extracting(event -> event.getAction())
				.containsExactly(AuditAction.UPDATED, AuditAction.DEACTIVATED);
		assertThat(events.getFirst().getChangedFields())
				.contains("saleDate", "paymentMethod", "memo", "items");
		assertThat(events.getLast().getChangedFields()).containsExactly("salesStatus");
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getEntityType()).isEqualTo("SALES_SLIP");
			assertThat(event.getActorId()).isEqualTo("operator");
		});
	}
}
