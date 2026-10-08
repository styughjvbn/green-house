package com.greenhouse.backend.farm.mutation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.farm.api.orchid.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CompensateCreateMutationsCommand;
import com.greenhouse.backend.farm.api.orchid.CompensateTransformMutationsCommand;
import com.greenhouse.backend.farm.api.orchid.ConsumeOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CorrectOrchidGroupMutationItem;
import com.greenhouse.backend.farm.api.orchid.CorrectOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.api.orchid.DiscardOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.MoveOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.MoveOrchidGroupMutationItem;
import com.greenhouse.backend.farm.api.orchid.MoveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupQuantityMutationItem;
import com.greenhouse.backend.farm.api.orchid.ReconcileOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.api.orchid.ReleaseOrchidGroupReservationsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.ReserveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.RestoreOutboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.StockCountOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.api.orchid.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.api.orchid.TransformOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.UpdateOrchidGroupMutationCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MutationFingerprintCompatibilityTest {

  @Test
  void keepsCorrectedCancellationAndCurrentCorrectionPayloads() {
    var source =
        new OrchidGroupMutationSource(
            OrchidGroupMutationSourceDomain.WORK, "TEST", "12", "CORRECT", UUID.randomUUID());
    var date = LocalDate.of(2026, 9, 8);
    var related = RelatedOrchidGroupMutations.current(List.of(11L, 9L));
    var fingerprint = new OrchidGroupMutationFingerprint();
    var calculator = new OrchidGroupMutationCommandFingerprint(fingerprint);
    assertThat(
            calculator.calculate(
                new CancelOrchidGroupCreationMutationCommand(source, 5L, related, date, "reason")))
        .isEqualTo(
            fingerprint.calculate(
                Map.of(
                    "mutationType",
                    "CANCEL_CREATION",
                    "orchidGroupId",
                    5L,
                    "correctedMutations",
                    Map.of("legacySource", false, "mutationIds", List.of(9L, 11L)),
                    "effectiveBusinessDate",
                    date,
                    "reason",
                    "reason")));
    assertThat(
            calculator.calculate(
                new CorrectOrchidGroupsMutationCommand(
                    source,
                    List.of(new CorrectOrchidGroupMutationItem(5L, 0, "폐기")),
                    related,
                    date,
                    "reason")))
        .isEqualTo(
            fingerprint.calculate(
                Map.of(
                    "mutationType",
                    "CORRECTION",
                    "items",
                    List.of(
                        Map.of(
                            "orchidGroupId", 5L, "correctedQuantity", 0, "correctedStatus", "폐기")),
                    "correctedMutations",
                    Map.of("legacySource", false, "mutationIds", List.of(9L, 11L)),
                    "effectiveBusinessDate",
                    date,
                    "reason",
                    "reason")));
  }

  @Test
  void keepsPartialTransformReleasedPositionsInTheLegacyPayload() {
    var source =
        new OrchidGroupMutationSource(
            OrchidGroupMutationSourceDomain.WORK, "TEST", "12", "EXECUTE", UUID.randomUUID());
    var date = LocalDate.of(2026, 9, 8);
    var details =
        new OrchidGroupMutationDetails(
            3L,
            2,
            "4치",
            1,
            "정상",
            "TRAY",
            1,
            false,
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            null);
    var command =
        new TransformOrchidGroupsMutationCommand(
            source,
            List.of(
                new TransformOrchidGroupMutationSource(
                    5L, 2, new BigDecimal("1"), new BigDecimal("2"))),
            List.of(new TransformOrchidGroupMutationResult(2L, details)),
            date,
            "reason",
            Set.of(9L, 8L));
    var fingerprint = new OrchidGroupMutationFingerprint();
    assertThat(new OrchidGroupMutationCommandFingerprint(fingerprint).calculate(command))
        .isEqualTo(
            fingerprint.calculate(
                Map.of(
                    "mutationType",
                    "TRANSFORM",
                    "sources",
                    List.of(
                        Map.of(
                            "orchidGroupId",
                            5L,
                            "transformedQuantity",
                            2,
                            "releasedStartPosition",
                            new BigDecimal("1.00"),
                            "releasedEndPosition",
                            new BigDecimal("2.00"))),
                    "results",
                    List.of(Map.of("bedZoneId", 2L, "details", details)),
                    "placementExclusionOrchidGroupIds",
                    List.of(8L, 9L),
                    "effectiveBusinessDate",
                    date,
                    "reason",
                    "reason")));
  }

  @Test
  void creationCancellationSelectionIsNormalizedAndPartOfTheFingerprint() {
    var source =
        new OrchidGroupMutationSource(
            OrchidGroupMutationSourceDomain.WORK, "TEST", "12", "VOID", UUID.randomUUID());
    var date = LocalDate.of(2026, 9, 8);
    var calculator =
        new OrchidGroupMutationCommandFingerprint(new OrchidGroupMutationFingerprint());
    var legacy = new CompensateTransformMutationsCommand(source, List.of(9L, 11L), date, "reason");
    assertThat(calculator.calculate(legacy))
        .isEqualTo(
            calculator.calculate(
                new CompensateTransformMutationsCommand(
                    source, List.of(11L, 9L), date, "reason", Set.of())));
    var first =
        new CompensateTransformMutationsCommand(
            source, List.of(9L, 11L), date, "reason", Set.of(3L, 1L));
    var second =
        new CompensateTransformMutationsCommand(
            source, List.of(11L, 9L), date, "reason", new LinkedHashSet<>(List.of(1L, 3L)));
    assertThat(calculator.calculate(first))
        .isEqualTo(calculator.calculate(second))
        .isNotEqualTo(calculator.calculate(legacy));
  }

  @Test
  void keepsStableCommandFingerprints() throws Exception {
    var source =
        new OrchidGroupMutationSource(
            OrchidGroupMutationSourceDomain.WORK,
            "TEST",
            "12",
            "EXECUTE:12",
            UUID.fromString("00000000-0000-0000-0000-000000000001"));
    var date = LocalDate.of(2026, 9, 8);
    var details =
        new OrchidGroupMutationDetails(
            3L,
            10,
            "4치",
            1,
            " 정상 ",
            "TRAY",
            1,
            false,
            new BigDecimal("0.00"),
            new BigDecimal("2.00"),
            " memo ");
    var groups = List.of(new CreateOrchidGroupMutationItem(2L, details));
    var quantities =
        List.of(
            new OrchidGroupQuantityMutationItem(7L, 2), new OrchidGroupQuantityMutationItem(5L, 1));
    List<OrchidGroupMutationCommand> commands =
        List.of(
            new CreateOrchidGroupMutationCommand(source, 2L, details, date, " reason "),
            new CreateInboundOrchidGroupsMutationCommand(source, 4L, groups, date, " reason "),
            new TransformOrchidGroupsMutationCommand(
                source,
                List.of(new TransformOrchidGroupMutationSource(5L, 10, null, null)),
                List.of(new TransformOrchidGroupMutationResult(2L, details)),
                date,
                " reason ",
                Set.of(8L, 9L)),
            new UpdateOrchidGroupMutationCommand(source, 5L, details, date, " reason "),
            new MoveOrchidGroupMutationCommand(
                source, 5L, 2L, BigDecimal.ZERO, new BigDecimal("2.00"), date, " reason "),
            new MoveOrchidGroupsMutationCommand(
                source,
                List.of(
                    new MoveOrchidGroupMutationItem(
                        5L, 2L, BigDecimal.ZERO, new BigDecimal("2.00")),
                    new MoveOrchidGroupMutationItem(
                        7L, 3L, new BigDecimal("2.00"), new BigDecimal("4.00"))),
                date,
                " reason ",
                Set.of(8L, 9L)),
            new CancelOrchidGroupCreationMutationCommand(source, 5L, date, " reason "),
            new DiscardOrchidGroupMutationCommand(source, 5L, 2, date, " reason "),
            new ReserveOrchidGroupsMutationCommand(source, quantities, date, " reason "),
            new ReleaseOrchidGroupReservationsMutationCommand(source, quantities, date, " reason "),
            new ConsumeOrchidGroupReservationsMutationCommand(source, quantities, date, " reason "),
            new RestoreOutboundOrchidGroupsMutationCommand(
                source,
                quantities,
                RelatedOrchidGroupMutations.current(List.of(11L, 9L)),
                date,
                " reason "),
            new CorrectOrchidGroupsMutationCommand(
                source,
                List.of(new CorrectOrchidGroupMutationItem(5L, 8, " 정상 ")),
                RelatedOrchidGroupMutations.legacy(),
                date,
                " reason "),
            new ReconcileOrchidGroupMutationCommand(
                source,
                5L,
                8,
                " 정상 ",
                2L,
                BigDecimal.ZERO,
                new BigDecimal("2.00"),
                date,
                " reason "),
            new CompensateTransformMutationsCommand(source, List.of(11L, 9L), date, " reason "),
            new CompensateCreateMutationsCommand(source, List.of(11L, 9L), date, " reason "),
            new StockCountOrchidGroupMutationCommand(source, 5L, 2L, 8, date, " reason "));
    var calculator =
        new OrchidGroupMutationCommandFingerprint(new OrchidGroupMutationFingerprint());
    var hashes = new TreeMap<String, String>();
    commands.forEach(
        command -> hashes.put(command.getClass().getSimpleName(), calculator.calculate(command)));
    assertThat(hashes.get("TransformOrchidGroupsMutationCommand"))
        .isEqualTo("d0b0ff8022e25df4b57b63da968e8f922e0d52483529e42902579f72fa72c654");
    var mapper = JsonMapper.builder().findAndAddModules().build();
    try (var input = getClass().getResourceAsStream("/farm/mutation-fingerprints.json")) {
      Map<String, List<String>> existingHashes = mapper.readValue(input, new TypeReference<>() {});
      assertThat(hashes).containsOnlyKeys(existingHashes.keySet());
      hashes.forEach(
          (command, hash) -> assertThat(hash).as(command).isIn(existingHashes.get(command)));
    }
    assertThat(commands.stream().map(Object::getClass).collect(Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(
            Arrays.asList(OrchidGroupMutationCommand.class.getPermittedSubclasses()));
  }
}
