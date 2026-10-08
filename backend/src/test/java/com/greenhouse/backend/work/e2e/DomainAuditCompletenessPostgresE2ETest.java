package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.farm.orchid.application.OrchidGroupCommandService;
import com.greenhouse.backend.farm.orchid.domain.PotSizeCode;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupBatchUpdateItem;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupUpdateRequest;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.document.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Tag("work-e2e")
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class DomainAuditCompletenessPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2044, 1, 1);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OrchidGroupRepository groups;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private MockMvc mockMvc;
  @Autowired private SalesSlipCreationService sales;
  @Autowired private OrchidGroupCommandService commands;
  private Long otherGroupId;
  private Long groupId;
  private Long varietyId;
  private Long partnerId;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    jdbc.execute(
        "TRUNCATE sales_creation_receipts, sales_slips, auction_shipments CONTINUE IDENTITY CASCADE");
    groupId = seeder.seedContractScenario().orchidGroupId();
    jdbc.update(
        "update orchid_groups set pot_size = ? where id = ?",
        PotSizeCode.POT_3_5.getDisplayValue(),
        groupId);
    varietyId =
        jdbc.queryForObject(
            "select variety_id from orchid_groups where id = ?", Long.class, groupId);
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "감사 거래처 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    otherGroupId =
        jdbc.queryForObject(
            """
        insert into orchid_groups (created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
          split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
        select created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, quantity, 2, status, variety_name, bed_zone_id,
          split_placement_allowed, variety_id, 6, 11, 0 from orchid_groups where id = ? returning id
        """,
            Long.class,
            groupId);
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
    jdbc.execute("TRUNCATE audit_events CONTINUE IDENTITY");
  }

  @Test
  void salesCreationPreservesAuthenticatedActorAndRequestIdentity() throws Exception {
    var session = new MockHttpSession();
    var response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/sales-slips")
                    .with(user("creator"))
                    .session(session)
                    .header("X-Request-Id", "sales-create-audit")
                    .header("X-Client-Instance-Id", "browser-audit")
                    .header("Idempotency-Key", "sales-create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(salesPayload()))
            .andExpect(status().isCreated())
            .andReturn();
    long id =
        objectMapper
            .readTree(response.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();
    var audits =
        jdbc.queryForList(
            "select actor_id, session_id, request_id, client_instance_id from audit_events where entity_type = 'SALES_SLIP' and entity_id = ? and action = 'CREATED'",
            id);
    assertThat(audits).hasSize(1);
    assertThat(audits.getFirst())
        .containsEntry("actor_id", "creator")
        .containsEntry("session_id", session.getId())
        .containsEntry("request_id", "sales-create-audit")
        .containsEntry("client_instance_id", "browser-audit");
  }

  @Test
  void memoOnlyEditCreatesRedactedAuditWithoutChangingQuantity() throws Exception {
    mockMvc
        .perform(
            patch("/api/orchid-groups/{id}", groupId)
                .with(user("editor"))
                .header("X-Request-Id", "memo-audit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(groupPayload("보존할 원문")))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select memo from orchid_groups where id = ?", String.class, groupId))
        .isEqualTo("보존할 원문");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where entity_type = 'ORCHID_GROUP' and entity_id = ? and source = 'ORCHID_GROUP_CORRECTION'",
                Integer.class,
                groupId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select array_to_string(changed_fields, ',') from audit_events where entity_type = 'ORCHID_GROUP' and entity_id = ?",
                String.class,
                groupId))
        .isEqualTo("memo");
    assertThat(
            jdbc.queryForObject(
                "select (before_data::text || after_data::text) from audit_events where entity_type = 'ORCHID_GROUP' and entity_id = ?",
                String.class,
                groupId))
        .doesNotContain("보존할 원문", "\"memo\"");
  }

  @ParameterizedTest
  @CsvSource({
    "placementType,false",
    "trayCount,false",
    "splitPlacementAllowed,false",
    "memo,false",
    "placementType,true",
    "trayCount,true",
    "splitPlacementAllowed,true",
    "memo,true"
  })
  void metadataOnlyChangesAreAuditedOnceForSingleAndBatchWrites(String field, boolean batch)
      throws Exception {
    ObjectNode request = (ObjectNode) objectMapper.readTree(groupPayload(null));
    switch (field) {
      case "placementType" -> request.put(field, "TRAY");
      case "trayCount" -> request.put(field, 3);
      case "splitPlacementAllowed" -> request.put(field, true);
      case "memo" -> request.put(field, "감사에 복제하지 않을 원문");
      default -> throw new IllegalStateException(field);
    }
    var session = new MockHttpSession();
    String body =
        batch
            ? objectMapper
                .createObjectNode()
                .set(
                    "orchidGroups",
                    objectMapper
                        .createArrayNode()
                        .add(
                            objectMapper
                                .createObjectNode()
                                .put("orchidGroupId", groupId)
                                .set("update", request)))
                .toString()
            : request.toString();
    String path = batch ? "/api/orchid-groups/batch" : "/api/orchid-groups/" + groupId;
    mockMvc
        .perform(
            patch(path)
                .with(user("metadata-editor"))
                .session(session)
                .header("X-Request-Id", "metadata-change")
                .header("X-Client-Instance-Id", "metadata-browser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    var audits =
        jdbc.queryForList(
            "select id, actor_id, session_id, request_id, client_instance_id, action, source from audit_events where entity_type = 'ORCHID_GROUP' and entity_id = ?",
            groupId);
    assertThat(audits).hasSize(1);
    var audit = audits.getFirst();
    assertThat(audit)
        .containsEntry("actor_id", "metadata-editor")
        .containsEntry("session_id", session.getId())
        .containsEntry("request_id", "metadata-change")
        .containsEntry("client_instance_id", "metadata-browser")
        .containsEntry("action", "UPDATED")
        .containsEntry("source", "ORCHID_GROUP_CORRECTION");
    long auditId = ((Number) audit.get("id")).longValue();
    assertThat(
            jdbc.queryForObject(
                "select array_to_string(changed_fields, ',') from audit_events where id = ?",
                String.class,
                auditId))
        .isEqualTo(field);
    var before = auditJson(auditId, "before_data");
    var after = auditJson(auditId, "after_data");
    var context = auditJson(auditId, "context_data");
    assertThat(before.path("quantity").asInt()).isEqualTo(100);
    assertThat(after.path("quantity").asInt()).isEqualTo(100);
    assertThat(context.path("correctionMode").asText()).isEqualTo(batch ? "BATCH" : "SINGLE");
    if (field.equals("memo")) {
      assertThat(before.has(field)).isFalse();
      assertThat(after.has(field)).isFalse();
      assertThat(context.path("redactedFields").get(0).asText()).isEqualTo("memo");
      assertThat(before.toString() + after + context).doesNotContain("감사에 복제하지 않을 원문");
    } else {
      assertThat(after.path(field)).isEqualTo(request.path(field));
      assertThat(before.path(field)).isNotEqualTo(after.path(field));
    }
    var committed = snapshot();
    mockMvc
        .perform(
            patch(path)
                .with(user("later-editor"))
                .header("X-Request-Id", "metadata-noop")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    assertThat(snapshot()).isEqualTo(committed);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void memoReplacementAndClearingKeepValuesOutOfAudit(boolean clear) throws Exception {
    mockMvc
        .perform(
            patch("/api/orchid-groups/{id}", groupId)
                .with(user("editor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(groupPayload("최초 원문")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/api/orchid-groups/{id}", groupId)
                .with(user("editor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(groupPayload(clear ? null : "수정 원문")))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where entity_type = 'ORCHID_GROUP' and entity_id = ?",
                Integer.class,
                groupId))
        .isEqualTo(2);
    var payloads =
        jdbc.queryForList(
            "select before_data::text || after_data::text || context_data::text from audit_events where entity_type = 'ORCHID_GROUP' and entity_id = ?",
            String.class,
            groupId);
    assertThat(payloads)
        .allSatisfy(value -> assertThat(value).doesNotContain("최초 원문", "수정 원문", "\"memo\":null"));
    assertThat(
            jdbc.queryForObject(
                "select memo from orchid_groups where id = ?", String.class, groupId))
        .isEqualTo(clear ? null : "수정 원문");
  }

  @ParameterizedTest
  @CsvSource({"DIRECT,작성중", "DIRECT,출고 완료", "AUCTION,작성중", "AUCTION,출하 완료"})
  void creationAuditCapturesFinalLifecycleAndReplayNeverReattributesIt(
      SalesType type, String lifecycle) throws Exception {
    var request = salesRequest(type, lifecycle);
    String body = objectMapper.writeValueAsString(request);
    var session = new MockHttpSession();
    var response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/sales-slips")
                    .with(user("first-creator"))
                    .session(session)
                    .header("Idempotency-Key", "lifecycle")
                    .header("X-Request-Id", "first-create")
                    .header("X-Client-Instance-Id", "first-browser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    var first = objectMapper.readTree(response.getResponse().getContentAsString()).path("data");
    long id = first.path("id").asLong();
    var audit =
        jdbc.queryForMap(
            "select id, actor_id, request_id, session_id, client_instance_id, action, source, before_data from audit_events where entity_type = 'SALES_SLIP' and entity_id = ?",
            id);
    assertThat(audit)
        .containsEntry("actor_id", "first-creator")
        .containsEntry("request_id", "first-create")
        .containsEntry("session_id", session.getId())
        .containsEntry("client_instance_id", "first-browser")
        .containsEntry("action", "CREATED")
        .containsEntry("source", "SALES_MANAGEMENT")
        .containsEntry("before_data", null);
    var after = auditJson(((Number) audit.get("id")).longValue(), "after_data");
    assertThat(after.path("salesStatus").asText()).isEqualTo(lifecycle);
    assertThat(after.path("salesType").asText()).isEqualTo(type.name());
    assertThat(after.path("partnerId").asLong()).isEqualTo(request.partnerId());
    assertThat(after.path("totalAmount").asInt()).isEqualTo(type == SalesType.DIRECT ? 5000 : 0);
    assertThat(after.path("items").get(0).path("allocations").get(0).path("orchidGroupId").asLong())
        .isEqualTo(groupId);
    var committed = snapshot();
    var retry =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/sales-slips")
                    .with(user("retry-user"))
                    .session(new MockHttpSession())
                    .header("Idempotency-Key", "lifecycle")
                    .header("X-Request-Id", "retry-create")
                    .header("X-Client-Instance-Id", "retry-browser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    assertThat(objectMapper.readTree(retry.getResponse().getContentAsString()).path("data"))
        .isEqualTo(first);
    assertThat(snapshot()).isEqualTo(committed);
  }

  @ParameterizedTest
  @CsvSource({
    "DIRECT,작성중,false",
    "DIRECT,작성중,true",
    "DIRECT,출고 완료,false",
    "DIRECT,출고 완료,true",
    "AUCTION,작성중,false",
    "AUCTION,작성중,true",
    "AUCTION,출하 완료,false",
    "AUCTION,출하 완료,true"
  })
  void auditInsertFailureRollsBackAllSalesFactsAndAllowsRetry(
      SalesType type, String lifecycle, boolean keyed) throws Exception {
    var request = salesRequest(type, lifecycle);
    var before = snapshot();
    jdbc.execute(
        "alter table audit_events add constraint test_sales_creation_audit_failure check (source <> 'SALES_MANAGEMENT')");
    try {
      assertThatThrownBy(() -> sales.create(request, keyed ? "audit-failure" : null))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_sales_creation_audit_failure");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table audit_events drop constraint test_sales_creation_audit_failure");
    }
    var created = sales.create(request, keyed ? "audit-failure" : null);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where entity_type = 'SALES_SLIP' and entity_id = ? and action = 'CREATED'",
                Integer.class,
                created.id()))
        .isEqualTo(1);
    // No request context exists for the application invocation; never invent an actor.
    assertThat(
            jdbc.queryForObject(
                "select actor_id from audit_events where entity_type = 'SALES_SLIP' and entity_id = ?",
                String.class,
                created.id()))
        .isNull();
    assertThat(jdbc.queryForObject("select count(*) from sales_slips", Integer.class)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void auditInsertFailureRollsBackSingleAndBatchMetadataMutations(boolean batch) throws Exception {
    var request = objectMapper.readValue(groupPayload("롤백 원문"), OrchidGroupUpdateRequest.class);
    var before = snapshot();
    jdbc.execute(
        "alter table audit_events add constraint test_group_audit_failure check (source <> 'ORCHID_GROUP_CORRECTION')");
    try {
      assertThatThrownBy(
              () -> {
                if (batch)
                  commands.updateBatch(
                      new OrchidGroupBatchUpdateRequest(
                          List.of(new OrchidGroupBatchUpdateItem(groupId, request))));
                else commands.update(groupId, request);
              })
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_group_audit_failure");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table audit_events drop constraint test_group_audit_failure");
    }
    assertThat(commands.update(groupId, request).memo()).isEqualTo("롤백 원문");
  }

  @Test
  void laterBatchFailureRollsBackEarlierMetadataAuditAndMutation() throws Exception {
    var request = objectMapper.readValue(groupPayload("선행 변경"), OrchidGroupUpdateRequest.class);
    // The second group currently occupies 6..11; overlapping 0..5 fails after the first update.
    var before = snapshot();
    assertThatThrownBy(
            () ->
                commands.updateBatch(
                    new OrchidGroupBatchUpdateRequest(
                        List.of(
                            new OrchidGroupBatchUpdateItem(groupId, request),
                            new OrchidGroupBatchUpdateItem(otherGroupId, request)))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void metadataNoOpSucceedsEvenWhenAuditInsertsAreUnavailable() throws Exception {
    var request = objectMapper.readValue(groupPayload(null), OrchidGroupUpdateRequest.class);
    var before = snapshot();
    jdbc.execute(
        "alter table audit_events add constraint test_noop_audit check (source <> 'ORCHID_GROUP_CORRECTION')");
    try {
      commands.update(groupId, request);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table audit_events drop constraint test_noop_audit");
    }
  }

  @Test
  void legacyKeylessSalesCreationsHaveTheirOwnActors() throws Exception {
    for (String actor : List.of("first-creator", "second-creator")) {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/api/sales-slips")
                  .with(user(actor))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(salesPayload()))
          .andExpect(status().isCreated());
    }
    assertThat(
            jdbc.queryForList(
                "select actor_id from audit_events where entity_type = 'SALES_SLIP' order by id",
                String.class))
        .containsExactly("first-creator", "second-creator");
    assertThat(jdbc.queryForObject("select count(*) from sales_creation_receipts", Integer.class))
        .isZero();
  }

  private SalesSlipCommand salesRequest(SalesType type, String lifecycle) throws Exception {
    ObjectNode request = (ObjectNode) objectMapper.readTree(salesPayload());
    request.put("salesType", type.name());
    request.put("salesStatus", lifecycle);
    if (type == SalesType.AUCTION) {
      long auctionPartner =
          partners
              .saveAndFlush(
                  new BusinessPartner(
                      "감사 경매장 " + UUID.randomUUID(),
                      PartnerType.AUCTION_HOUSE,
                      null,
                      null,
                      null,
                      null))
              .getId();
      request.put("partnerId", auctionPartner);
      ((ObjectNode) request.path("items").get(0)).put("unitPrice", 0);
    }
    return objectMapper.treeToValue(request, SalesSlipCommand.class);
  }

  private JsonNode auditJson(long id, String column) throws Exception {
    return objectMapper.readTree(
        jdbc.queryForObject(
            "select " + column + "::text from audit_events where id = ?", String.class, id));
  }

  private Map<String, List<String>> snapshot() {
    var result = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "audit_events",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "orchid_group_ledger_coverages",
            "orchid_group_lineage",
            "sales_creation_receipts",
            "sales_slips",
            "sales_slip_items",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots",
            "sales_inventory_movements",
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_lot_status_history",
            "partner_balance_summaries",
            "partner_payment_events",
            "sales_slip_daily_sequences")) {
      result.put(
          table,
          jdbc.queryForList(
              "select to_jsonb(r)::text from " + table + " r order by 1", String.class));
    }
    return result;
  }

  private String groupPayload(String memo) throws Exception {
    return """
        {"varietyId":%d,"quantity":100,"potSize":"3.5치","ageYear":2,"status":"정상",
         "placementType":"POT","splitPlacementAllowed":false,"startPosition":0,"endPosition":5,"memo":%s}
        """
        .formatted(varietyId, objectMapper.writeValueAsString(memo));
  }

  private String salesPayload() {
    return """
        {"saleDate":"2044-01-01","salesType":"DIRECT","partnerId":%d,"salesStatus":"작성중",
         "items":[{"itemName":"E2E 난","genus":"팔레놉시스","spec":"3.5치","quantity":5,
           "unitPrice":1000,"allocations":[{"orchidGroupId":%d,"quantity":5}]}]}
        """
        .formatted(partnerId, groupId);
  }
}
