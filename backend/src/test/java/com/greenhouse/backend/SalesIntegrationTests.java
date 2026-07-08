package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Disabled("Seed data is currently disabled; re-enable after deterministic test fixtures are restored.")
class SalesIntegrationTests extends AbstractBackendIntegrationTest {

	@Test
	void createsBusinessPartnersAndSalesSlipsWithCalculatedAmounts() throws Exception {
		var sampleGroups = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.limit(2)
				.toList();
		var firstGroup = sampleGroups.get(0);
		var secondGroup = sampleGroups.get(1);

		var partnerResult = mockMvc.perform(post("/api/business-partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "??? ???",
						  "partnerType": "WHOLESALE",
						  "ownerName": "???",
						  "phone": "010-0000-0000",
						  "address": "??",
						  "memo": "??? ??"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value("??? ???"))
				.andReturn();
		var partnerId = Long
				.valueOf(partnerResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/business-partners").param("keyword", "???"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(partnerId))
				.andExpect(jsonPath("$.data[0].partnerType").value("WHOLESALE"))
				.andExpect(jsonPath("$.data[0].active").value(true));

		var slipResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-06-24",
						  "partnerId": %d,
						  "paymentStatus": "???",
						  "salesStatus": "???",
						  "paymentMethod": "??",
						  "memo": "?? ??",
						  "items": [
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "spec": "4?",
						      "quantity": 2,
						      "unitPrice": 15000,
						      "memo": "??1",
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 2
						        }
						      ]
						    },
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 3,
						      "unitPrice": 10000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 3
						        }
						      ]
						    }
						  ]
						}
						""".formatted(
						partnerId,
						firstGroup.getVarietyName(),
						firstGroup.getGenus(),
						firstGroup.getId(),
						secondGroup.getVarietyName(),
						secondGroup.getGenus(),
						secondGroup.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.partner.id").value(partnerId))
				.andExpect(jsonPath("$.data.totalAmount").value(60000))
				.andExpect(jsonPath("$.data.items", hasSize(2)))
				.andExpect(jsonPath("$.data.items[0].amount").value(30000))
				.andExpect(jsonPath("$.data.items[0].allocations[0].orchidGroupId").value(firstGroup.getId()))
				.andReturn();
		var salesSlipId = Long
				.valueOf(slipResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/sales-slips/{salesSlipId}", salesSlipId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(salesSlipId))
				.andExpect(jsonPath("$.data.totalAmount").value(60000))
				.andExpect(jsonPath("$.data.items[1].allocations[0].orchidGroupId").value(secondGroup.getId()));

		mockMvc.perform(get("/api/sales-slips/{salesSlipId}/print", salesSlipId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(salesSlipId))
				.andExpect(jsonPath("$.data.slipNumber").exists())
				.andExpect(jsonPath("$.data.partner.id").value(partnerId))
				.andExpect(jsonPath("$.data.items", hasSize(2)))
				.andExpect(jsonPath("$.data.totalAmount").value(60000));

		mockMvc.perform(get("/api/sales-slips")
				.param("partnerId", partnerId.toString())
				.param("from", "2026-06-01")
				.param("to", "2026-06-30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(salesSlipId));
	}

	@Test
	void returnsValidationErrorsForInvalidSalesRequests() throws Exception {
		var sampleGroup = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.findFirst()
				.orElseThrow();

		mockMvc.perform(post("/api/business-partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"partnerType\":\"WHOLESALE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-06-24",
						  "partnerId": 999999,
						  "items": [
						    {
						      "itemName": "%s",
						      "quantity": 1,
						      "unitPrice": 1000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 1
						        }
						      ]
						    }
						  ]
						}
						""".formatted(sampleGroup.getVarietyName(), sampleGroup.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-06-24",
						  "partnerId": 1,
						  "items": []
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void updatesDraftDirectSalesSlipAndRecalculatesReservations() throws Exception {
		var groups = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.limit(3)
				.toList();
		var firstGroup = orchidGroupRepository.findById(groups.get(0).getId()).orElseThrow();
		var secondGroup = orchidGroupRepository.findById(groups.get(1).getId()).orElseThrow();
		var thirdGroup = orchidGroupRepository.findById(groups.get(2).getId()).orElseThrow();
		var firstReservedBefore = firstGroup.getReservedQuantity();
		var secondReservedBefore = secondGroup.getReservedQuantity();
		var thirdReservedBefore = thirdGroup.getReservedQuantity();
		var partnerResult = mockMvc.perform(post("/api/business-partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "Update Test Partner",
						  "partnerType": "WHOLESALE"
						}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		var partnerId = Long
				.valueOf(partnerResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		var createResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-07-08",
						  "partnerId": %d,
						  "items": [
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 2,
						      "unitPrice": 1000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 2
						        }
						      ]
						    },
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 3,
						      "unitPrice": 2000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 3
						        }
						      ]
						    }
						  ]
						}
						""".formatted(
						partnerId,
						firstGroup.getVarietyName(),
						firstGroup.getGenus(),
						firstGroup.getId(),
						secondGroup.getVarietyName(),
						secondGroup.getGenus(),
						secondGroup.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		var salesSlipId = Long
				.valueOf(createResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		assertThat(orchidGroupRepository.findById(firstGroup.getId()).orElseThrow().getReservedQuantity())
				.isEqualTo(firstReservedBefore + 2);
		assertThat(orchidGroupRepository.findById(secondGroup.getId()).orElseThrow().getReservedQuantity())
				.isEqualTo(secondReservedBefore + 3);

		mockMvc.perform(put("/api/sales-slips/{salesSlipId}", salesSlipId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-07-09",
						  "partnerId": %d,
						  "items": [
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 1,
						      "unitPrice": 3000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 1
						        }
						      ]
						    },
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 4,
						      "unitPrice": 1500,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 4
						        }
						      ]
						    }
						  ]
						}
						""".formatted(
						partnerId,
						firstGroup.getVarietyName(),
						firstGroup.getGenus(),
						firstGroup.getId(),
						thirdGroup.getVarietyName(),
						thirdGroup.getGenus(),
						thirdGroup.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.saleDate").value("2026-07-09"))
				.andExpect(jsonPath("$.data.totalAmount").value(9000))
				.andExpect(jsonPath("$.data.items[0].allocations[0].orchidGroupId").value(firstGroup.getId()))
				.andExpect(jsonPath("$.data.items[1].allocations[0].orchidGroupId").value(thirdGroup.getId()));

		assertThat(orchidGroupRepository.findById(firstGroup.getId()).orElseThrow().getReservedQuantity())
				.isEqualTo(firstReservedBefore + 1);
		assertThat(orchidGroupRepository.findById(secondGroup.getId()).orElseThrow().getReservedQuantity())
				.isEqualTo(secondReservedBefore);
		assertThat(orchidGroupRepository.findById(thirdGroup.getId()).orElseThrow().getReservedQuantity())
				.isEqualTo(thirdReservedBefore + 4);
	}

	@Test
	@Transactional
	void cancelsDraftDirectSalesSlipAndReleasesReservations() throws Exception {
		var sampleGroup = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.findFirst()
				.orElseThrow();
		var reservedBefore = orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getReservedQuantity();

		var partnerResult = mockMvc.perform(post("/api/business-partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "취소 테스트 거래처",
						  "partnerType": "WHOLESALE"
						}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		var partnerId = Long
				.valueOf(partnerResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		var createResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-07-08",
						  "partnerId": %d,
						  "salesStatus": "작성중",
						  "items": [
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 4,
						      "unitPrice": 1000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 4
						        }
						      ]
						    }
						  ]
						}
						""".formatted(partnerId, sampleGroup.getVarietyName(), sampleGroup.getGenus(),
						sampleGroup.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		var salesSlipId = Long
				.valueOf(createResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		assertThat(orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getReservedQuantity())
				.isEqualTo(reservedBefore + 4);

		mockMvc.perform(patch("/api/sales-slips/{salesSlipId}/sales-status", salesSlipId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "salesStatus": "취소"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.salesStatus").value("취소"));

		assertThat(orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getReservedQuantity())
				.isEqualTo(reservedBefore);
	}

	@Test
	@Transactional
	void cancelsCompletedDirectSalesSlipAndRestoresQuantity() throws Exception {
		var sampleGroup = orchidGroupRepository.findAll().stream()
				.filter(group -> group.getVariety() != null)
				.findFirst()
				.orElseThrow();
		var quantityBefore = orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getQuantity();

		var partnerResult = mockMvc.perform(post("/api/business-partners")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "출고 취소 거래처",
						  "partnerType": "WHOLESALE"
						}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		var partnerId = Long
				.valueOf(partnerResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

		var createResult = mockMvc.perform(post("/api/sales-slips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "saleDate": "2026-07-08",
						  "partnerId": %d,
						  "salesStatus": "출고 완료",
						  "items": [
						    {
						      "itemName": "%s",
						      "genus": "%s",
						      "quantity": 3,
						      "unitPrice": 1000,
						      "allocations": [
						        {
						          "orchidGroupId": %d,
						          "quantity": 3
						        }
						      ]
						    }
						  ]
						}
						""".formatted(partnerId, sampleGroup.getVarietyName(), sampleGroup.getGenus(),
						sampleGroup.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		var salesSlipId = Long
				.valueOf(createResult.getResponse().getContentAsString().replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

		assertThat(orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getQuantity())
				.isEqualTo(quantityBefore - 3);

		mockMvc.perform(patch("/api/sales-slips/{salesSlipId}/sales-status", salesSlipId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "salesStatus": "취소"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.salesStatus").value("취소"));

		assertThat(orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getQuantity())
				.isEqualTo(quantityBefore);
	}
}
