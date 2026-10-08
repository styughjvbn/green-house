package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.document.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.document.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.document.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

class SalesIntegrationTests extends FarmFixtureIntegrationTest {

  @Autowired BusinessPartnerRepository policyPartners;
  @Autowired SalesSlipCreationService policyCreation;

  @ParameterizedTest
  @CsvSource({
    "missingPartner,일반 판매는 거래처를 선택해야 합니다.",
    "missingBoth,일반 판매는 거래처를 선택해야 합니다.",
    "emptyItems,일반 판매 품목은 1개 이상 입력해야 합니다.",
    "auctionPartner,경매장 거래처는 경매 판매 전표에서 사용해야 합니다."
  })
  void creationAndUpdateKeepTheSameDirectInputErrors(String violation, String message)
      throws Exception {
    var partner = policyPartner(PartnerType.WHOLESALE);
    var valid = policyRequest(null, partner.getId(), null);
    var created = policyCreation.create(valid);
    Long invalidPartner =
        violation.startsWith("missing")
            ? null
            : violation.equals("auctionPartner")
                ? policyPartner(PartnerType.AUCTION_HOUSE).getId()
                : partner.getId();
    var invalid =
        policyInput(
            valid,
            invalidPartner,
            violation.equals("emptyItems") || violation.equals("missingBoth")
                ? List.of()
                : valid.items());
    var original =
        mockMvc
            .perform(get("/api/sales-slips/{id}", created.id()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    mockMvc
        .perform(
            post("/api/sales-slips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.error.details[0]").value(message));
    mockMvc
        .perform(
            put("/api/sales-slips/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.error.details[0]").value(message));
    var after =
        mockMvc
            .perform(get("/api/sales-slips/{id}", created.id()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(after).isEqualTo(original);
  }

  @ParameterizedTest
  @MethodSource("directPaymentDefaults")
  void creationAndUpdateKeepPaymentDefaultsAndExplicitLegacyLabels(
      SalesType type, String input, String expected) throws Exception {
    var request = policyRequest(type, policyPartner(PartnerType.WHOLESALE).getId(), input);
    var created =
        mockMvc
            .perform(
                post("/api/sales-slips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(policyJson(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.salesType").value("DIRECT"))
            .andExpect(jsonPath("$.data.paymentStatus").value(expected))
            .andReturn();
    long id =
        JsonMapper.builder()
            .build()
            .readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();
    mockMvc
        .perform(
            put("/api/sales-slips/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentStatus").value(expected));
  }

  static Stream<Arguments> directPaymentDefaults() {
    return Stream.of(
        Arguments.of(null, null, "미입금"),
        Arguments.of(SalesType.DIRECT, null, "미입금"),
        Arguments.of(SalesType.DIRECT, " \t ", "미입금"),
        Arguments.of(null, "  입금 보류  ", "입금 보류"));
  }

  @Test
  void auctionCreationKeepsItsOwnDefaultsAndRequestTypeStillBlocksDirectEditing() throws Exception {
    var auctionRequest =
        policyRequest(SalesType.AUCTION, policyPartner(PartnerType.AUCTION_HOUSE).getId(), null);
    mockMvc
        .perform(
            post("/api/sales-slips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(auctionRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.paymentStatus").value("정산 대기"))
        .andExpect(jsonPath("$.data.paymentMethod").value("경매 정산"));
    var direct =
        policyCreation.create(
            policyRequest(SalesType.DIRECT, policyPartner(PartnerType.WHOLESALE).getId(), null));
    var invalid = policyInput(auctionRequest, null, List.of());
    mockMvc
        .perform(
            put("/api/sales-slips/{id}", direct.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.error.details[0]").value("경매 판매 전표 수정은 아직 지원하지 않습니다."));
  }

  @ParameterizedTest
  @CsvSource({
    "missingPartner,경매 판매는 경매장을 선택해야 합니다.",
    "emptyItems,경매 판매는 1개 이상의 lot 품목이 필요합니다.",
    "directPartner,경매 판매는 경매장 거래처만 선택할 수 있습니다."
  })
  void auctionCreationKeepsItsDistinctRequiredInputErrors(String violation, String message)
      throws Exception {
    var request =
        policyRequest(
            SalesType.AUCTION,
            policyPartner(
                    violation.equals("directPartner")
                        ? PartnerType.WHOLESALE
                        : PartnerType.AUCTION_HOUSE)
                .getId(),
            null);
    var invalid =
        policyInput(
            request,
            violation.equals("missingPartner") ? null : request.partnerId(),
            violation.equals("emptyItems") ? List.of() : request.items());
    mockMvc
        .perform(
            post("/api/sales-slips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.error.details[0]").value(message));
  }

  private BusinessPartner policyPartner(PartnerType type) {
    return policyPartners.saveAndFlush(
        new BusinessPartner("공통 판매 정책 " + type, type, null, null, null, null));
  }

  private SalesSlipCommand policyRequest(SalesType type, Long partnerId, String paymentStatus) {
    var group =
        orchidGroupRepository.findAll().stream()
            .filter(value -> value.getVariety() != null)
            .findFirst()
            .orElseThrow();
    return new SalesSlipCommand(
        LocalDate.of(2026, 8, 1),
        type,
        partnerId,
        null,
        paymentStatus,
        null,
        null,
        null,
        List.of(
            new SalesSlipItemInput(
                group.getVarietyName(),
                group.getGenus(),
                "4치",
                5,
                100,
                null,
                List.of(new SalesSlipAllocationInput(group.getId(), 5)))));
  }

  private SalesSlipCommand policyInput(
      SalesSlipCommand request, Long partnerId, List<SalesSlipItemInput> items) {
    return new SalesSlipCommand(
        request.saleDate(),
        request.salesType(),
        partnerId,
        request.auctionShipmentId(),
        request.paymentStatus(),
        request.salesStatus(),
        request.paymentMethod(),
        request.memo(),
        items);
  }

  private String policyJson(SalesSlipCommand request) throws Exception {
    return JsonMapper.builder()
        .findAndAddModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()
        .writeValueAsString(request);
  }

  @Test
  void createsBusinessPartnersAndSalesSlipsWithCalculatedAmounts() throws Exception {
    var sampleGroups =
        orchidGroupRepository.findAll().stream()
            .filter(group -> group.getVariety() != null)
            .limit(2)
            .toList();
    var firstGroup = sampleGroups.get(0);
    var secondGroup = sampleGroups.get(1);

    var partnerResult =
        mockMvc
            .perform(
                post("/api/business-partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
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
    var partnerId =
        Long.valueOf(
            partnerResult
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

    mockMvc
        .perform(get("/api/business-partners").param("keyword", "???"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(partnerId))
        .andExpect(jsonPath("$.data[0].partnerType").value("WHOLESALE"))
        .andExpect(jsonPath("$.data[0].active").value(true));

    mockMvc
        .perform(
            put("/api/business-partners/{partnerId}", partnerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{
						  "name": "??? ??",
						  "partnerType": "RETAIL",
						  "ownerName": "???",
						  "phone": "010-1111-2222",
						  "address": "?? ??",
						  "memo": "?? ??"
						}
						"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(partnerId))
        .andExpect(jsonPath("$.data.name").value("??? ??"))
        .andExpect(jsonPath("$.data.partnerType").value("RETAIL"))
        .andExpect(jsonPath("$.data.phone").value("010-1111-2222"));

    var slipResult =
        mockMvc
            .perform(
                post("/api/sales-slips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
						{
						  "saleDate": "2026-06-24",
						  "partnerId": %d,
						  "paymentStatus": "미입금",
						  "salesStatus": "작성중",
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
						"""
                            .formatted(
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
            .andExpect(
                jsonPath("$.data.items[0].allocations[0].orchidGroupId").value(firstGroup.getId()))
            .andReturn();
    var salesSlipId =
        Long.valueOf(
            slipResult
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

    mockMvc
        .perform(get("/api/sales-slips/{salesSlipId}", salesSlipId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(salesSlipId))
        .andExpect(jsonPath("$.data.totalAmount").value(60000))
        .andExpect(
            jsonPath("$.data.items[1].allocations[0].orchidGroupId").value(secondGroup.getId()));

    mockMvc
        .perform(get("/api/sales-slips/{salesSlipId}/print", salesSlipId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(salesSlipId))
        .andExpect(jsonPath("$.data.slipNumber").exists())
        .andExpect(jsonPath("$.data.partner.id").value(partnerId))
        .andExpect(jsonPath("$.data.items", hasSize(2)))
        .andExpect(jsonPath("$.data.totalAmount").value(60000));

    mockMvc
        .perform(
            get("/api/sales-slips")
                .param("partnerId", partnerId.toString())
                .param("from", "2026-06-01")
                .param("to", "2026-06-30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(salesSlipId));
  }

  @Test
  void returnsValidationErrorsForInvalidSalesRequests() throws Exception {
    var sampleGroup =
        orchidGroupRepository.findAll().stream()
            .filter(group -> group.getVariety() != null)
            .findFirst()
            .orElseThrow();

    mockMvc
        .perform(
            post("/api/business-partners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"partnerType\":\"WHOLESALE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc
        .perform(
            post("/api/sales-slips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
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
				"""
                        .formatted(sampleGroup.getVarietyName(), sampleGroup.getId())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

    mockMvc
        .perform(
            post("/api/sales-slips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
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
    var groups =
        orchidGroupRepository.findAll().stream()
            .filter(group -> group.getVariety() != null)
            .limit(3)
            .toList();
    var firstGroup = orchidGroupRepository.findById(groups.get(0).getId()).orElseThrow();
    var secondGroup = orchidGroupRepository.findById(groups.get(1).getId()).orElseThrow();
    var thirdGroup = orchidGroupRepository.findById(groups.get(2).getId()).orElseThrow();
    var firstReservedBefore = firstGroup.getReservedQuantity();
    var secondReservedBefore = secondGroup.getReservedQuantity();
    var thirdReservedBefore = thirdGroup.getReservedQuantity();
    var partnerResult =
        mockMvc
            .perform(
                post("/api/business-partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
					{
					  "name": "Update Test Partner",
					  "partnerType": "WHOLESALE"
					}
					"""))
            .andExpect(status().isCreated())
            .andReturn();
    var partnerId =
        Long.valueOf(
            partnerResult
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

    var createResult =
        mockMvc
            .perform(
                post("/api/sales-slips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
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
						"""
                            .formatted(
                                partnerId,
                                firstGroup.getVarietyName(),
                                firstGroup.getGenus(),
                                firstGroup.getId(),
                                secondGroup.getVarietyName(),
                                secondGroup.getGenus(),
                                secondGroup.getId())))
            .andExpect(status().isCreated())
            .andReturn();
    var salesSlipId =
        Long.valueOf(
            createResult
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

    assertThat(
            orchidGroupRepository.findById(firstGroup.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(firstReservedBefore + 2);
    assertThat(
            orchidGroupRepository.findById(secondGroup.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(secondReservedBefore + 3);

    mockMvc
        .perform(
            put("/api/sales-slips/{salesSlipId}", salesSlipId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
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
						"""
                        .formatted(
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
        .andExpect(
            jsonPath("$.data.items[0].allocations[0].orchidGroupId").value(firstGroup.getId()))
        .andExpect(
            jsonPath("$.data.items[1].allocations[0].orchidGroupId").value(thirdGroup.getId()));

    assertThat(
            orchidGroupRepository.findById(firstGroup.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(firstReservedBefore + 1);
    assertThat(
            orchidGroupRepository.findById(secondGroup.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(secondReservedBefore);
    assertThat(
            orchidGroupRepository.findById(thirdGroup.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(thirdReservedBefore + 4);
  }

  @Test
  @Transactional
  void cancelsDraftDirectSalesSlipAndReleasesReservations() throws Exception {
    var sampleGroup =
        orchidGroupRepository.findAll().stream()
            .filter(group -> group.getVariety() != null)
            .findFirst()
            .orElseThrow();
    var reservedBefore =
        orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getReservedQuantity();

    var partnerResult =
        mockMvc
            .perform(
                post("/api/business-partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
					{
					  "name": "취소 테스트 거래처",
					  "partnerType": "WHOLESALE"
					}
					"""))
            .andExpect(status().isCreated())
            .andReturn();
    var partnerId =
        Long.valueOf(
            partnerResult
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

    var createResult =
        mockMvc
            .perform(
                post("/api/sales-slips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
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
						"""
                            .formatted(
                                partnerId,
                                sampleGroup.getVarietyName(),
                                sampleGroup.getGenus(),
                                sampleGroup.getId())))
            .andExpect(status().isCreated())
            .andReturn();
    var salesSlipId =
        Long.valueOf(
            createResult
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

    assertThat(
            orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(reservedBefore + 4);

    mockMvc
        .perform(
            patch("/api/sales-slips/{salesSlipId}/sales-status", salesSlipId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
						{
						  "salesStatus": "취소"
						}
						"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.salesStatus").value("취소"));

    assertThat(
            orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(reservedBefore);
  }

  @Test
  @Transactional
  void cancelsCompletedDirectSalesSlipAndRestoresQuantity() throws Exception {
    var sampleGroup =
        orchidGroupRepository.findAll().stream()
            .filter(group -> group.getVariety() != null)
            .findFirst()
            .orElseThrow();
    var quantityBefore =
        orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getQuantity();

    var partnerResult =
        mockMvc
            .perform(
                post("/api/business-partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
					{
					  "name": "출고 취소 거래처",
					  "partnerType": "WHOLESALE"
					}
					"""))
            .andExpect(status().isCreated())
            .andReturn();
    var partnerId =
        Long.valueOf(
            partnerResult
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

    var createResult =
        mockMvc
            .perform(
                post("/api/sales-slips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
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
						"""
                            .formatted(
                                partnerId,
                                sampleGroup.getVarietyName(),
                                sampleGroup.getGenus(),
                                sampleGroup.getId())))
            .andExpect(status().isCreated())
            .andReturn();
    var salesSlipId =
        Long.valueOf(
            createResult
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*?\\\"id\\\":(\\d+).*", "$1"));

    assertThat(orchidGroupRepository.findById(sampleGroup.getId()).orElseThrow().getQuantity())
        .isEqualTo(quantityBefore - 3);

    mockMvc
        .perform(
            patch("/api/sales-slips/{salesSlipId}/sales-status", salesSlipId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
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
