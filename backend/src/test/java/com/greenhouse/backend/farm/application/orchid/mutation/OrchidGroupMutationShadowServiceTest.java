package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class OrchidGroupMutationShadowServiceTest {

	@Test
	void legacyModeDoesNotEvaluateMutationCommand() {
		var routing = mock(OrchidGroupMutationRoutingPolicy.class);
		var planner = mock(OrchidGroupShadowPlanner.class);
		var service = service(routing, planner);

		assertThat(service.prepare(null)).isNull();
		verifyNoInteractions(planner);
	}

	@Test
	void enginePlanRejectionIsCapturedWithoutEscapingToLegacyCaller() {
		var routing = mock(OrchidGroupMutationRoutingPolicy.class);
		var planner = mock(OrchidGroupShadowPlanner.class);
		var fingerprint = mock(OrchidGroupMutationCommandFingerprint.class);
		var repository = mock(OrchidGroupRepository.class);
		var publisher = mock(ApplicationEventPublisher.class);
		var service = new OrchidGroupMutationShadowService(
				routing, planner, fingerprint, repository, publisher);
		Object command = new Object();
		var source = new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.FARM,
				"TEST",
				"1",
				"UPDATE",
				UUID.randomUUID());
		when(routing.capturesShadowComparison()).thenReturn(true);
		when(planner.plan(command)).thenThrow(new IllegalArgumentException("engine validation"));
		when(planner.source(command)).thenReturn(source);
		when(planner.mutationType(command)).thenReturn(OrchidGroupMutationType.UPDATE_DETAILS);
		when(planner.payload(command)).thenReturn(Map.of("id", 1));
		when(fingerprint.calculate(command)).thenReturn("a".repeat(64));

		var plan = service.prepare(command);

		assertThat(plan.acceptedByEnginePlan()).isFalse();
		assertThat(plan.engineError()).contains("engine validation");
		assertThatCode(() -> service.complete(plan)).doesNotThrowAnyException();
	}

	private OrchidGroupMutationShadowService service(
			OrchidGroupMutationRoutingPolicy routing,
			OrchidGroupShadowPlanner planner) {
		OrchidGroupRepository repository = mock(OrchidGroupRepository.class);
		when(repository.findAllById(List.of())).thenReturn(List.of());
		return new OrchidGroupMutationShadowService(
				routing,
				planner,
				mock(OrchidGroupMutationCommandFingerprint.class),
				repository,
				mock(ApplicationEventPublisher.class));
	}
}
