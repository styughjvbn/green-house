package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MutationFingerprintCompatibilityTest {

	@Test
	void keepsStableCommandFingerprints() throws Exception {
		var source = new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.WORK, "TEST", "12", "EXECUTE:12",
				UUID.fromString("00000000-0000-0000-0000-000000000001"));
		var date = LocalDate.of(2026, 9, 8);
		var details = new OrchidGroupMutationDetails(3L, 10, "4치", 1, " 정상 ", "TRAY", 1, false, new BigDecimal("0.00"),
				new BigDecimal("2.00"), " memo ");
		var groups = List.of(new CreateOrchidGroupMutationItem(2L, details));
		var quantities = List.of(new OrchidGroupQuantityMutationItem(7L, 2),
				new OrchidGroupQuantityMutationItem(5L, 1));
		List<OrchidGroupMutationCommand> commands = List.of(
				new CreateOrchidGroupMutationCommand(source, 2L, details, date, " reason "),
				new CreateOrchidGroupsMutationCommand(source, groups, date, " reason "),
				new CreateInboundOrchidGroupsMutationCommand(source, 4L, groups, date, " reason "),
				new TransformOrchidGroupsMutationCommand(source,
						List.of(new TransformOrchidGroupMutationSource(5L, 10, null, null)),
						List.of(new TransformOrchidGroupMutationResult(2L, details)), date, " reason ", Set.of(8L, 9L)),
				new UpdateOrchidGroupMutationCommand(source, 5L, details, date, " reason "),
				new MoveOrchidGroupMutationCommand(source, 5L, 2L, BigDecimal.ZERO, new BigDecimal("2.00"), date,
						" reason "),
				new CancelOrchidGroupCreationMutationCommand(source, 5L, date, " reason "),
				new DiscardOrchidGroupMutationCommand(source, 5L, 2, date, " reason "),
				new ReserveOrchidGroupsMutationCommand(source, quantities, date, " reason "),
				new ReleaseOrchidGroupReservationsMutationCommand(source, quantities, date, " reason "),
				new ConsumeOrchidGroupReservationsMutationCommand(source, quantities, date, " reason "),
				new RestoreOutboundOrchidGroupsMutationCommand(source, quantities,
						RelatedOrchidGroupMutations.current(List.of(11L, 9L)), date, " reason "),
				new CorrectOrchidGroupsMutationCommand(source,
						List.of(new CorrectOrchidGroupMutationItem(5L, 8, " 정상 ")),
						RelatedOrchidGroupMutations.legacy(), date, " reason "));
		var calculator = new OrchidGroupMutationCommandFingerprint(new OrchidGroupMutationFingerprint());
		var hashes = new java.util.TreeMap<String, String>();
		commands.forEach(command -> hashes.put(command.getClass().getSimpleName(), calculator.calculate(command)));
		assertThat(hashes.get("TransformOrchidGroupsMutationCommand"))
			.isEqualTo("d0b0ff8022e25df4b57b63da968e8f922e0d52483529e42902579f72fa72c654");
		var mapper = JsonMapper.builder().findAndAddModules().build();
		try (var input = getClass().getResourceAsStream("/farm/mutation-fingerprints.json")) {
			Map<String, List<String>> existingHashes = mapper.readValue(input, new TypeReference<>() {
			});
			assertThat(hashes).containsOnlyKeys(existingHashes.keySet());
			hashes.forEach((command, hash) -> assertThat(hash).as(command).isIn(existingHashes.get(command)));
		}
		assertThat(commands.stream().map(Object::getClass).collect(java.util.stream.Collectors.toSet()))
			.containsExactlyInAnyOrderElementsOf(
					java.util.Arrays.asList(OrchidGroupMutationCommand.class.getPermittedSubclasses()));
	}

}
