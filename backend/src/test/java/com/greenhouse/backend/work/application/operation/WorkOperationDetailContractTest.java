package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.application.target.WorkExecutionReferenceGateway;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WorkOperationDetailContractTest {

	@Test
	void preservesStoredAndLegacyDetails() throws Exception {
		var mapper = new ObjectMapper().findAndRegisterModules();
		var fixtures = mapper.readTree(getClass().getResourceAsStream("/work/detail-contract.json"));
		for (var fixture : fixtures) {
			var operations = mock(WorkOperationRepository.class);
			var effects = mock(WorkAppliedEffectRepository.class);
			var links = mock(WorkEffectOrchidGroupRepository.class);
			var corrections = mock(WorkOperationCorrectionRepository.class);
			var references = mock(WorkExecutionReferenceGateway.class);
			var operation = mock(WorkOperation.class);
			var type = new WorkType("CUSTOM", "작업", WorkTypeTemplate.MEMO, false, false, true, 1);
			when(operation.getWorkType()).thenReturn(type);
			when(operation.getDetails()).thenReturn(mapper.readValue("""
					{"materialName":"자재","quantity":3,"enabled":true,"tags":["가",2],
					 "empty":"  ","nested":{"x":1},"rows":[[1]],"legacyField":1,"idempotencyKey":"hidden"}
					""", new TypeReference<Map<String, Object>>() {
			}));
			when(operations.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));
			var effect = new WorkAppliedEffect(operation, null, "EXECUTION:fixture", WorkEffectKind.STRUCTURE_CHANGE,
					"REPOT", LocalDateTime.of(2026, 8, 20, 0, 0), "작업자",
					mapper.convertValue(fixture.get("command"), new TypeReference<Map<String, Object>>() {
					}), mapper.convertValue(fixture.get("result"), new TypeReference<Map<String, Object>>() {
					}));
			ReflectionTestUtils.setField(effect, "id", 10L);
			when(effects.findByWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of(effect));
			when(links.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(1L))
				.thenReturn(List.of(new WorkEffectOrchidGroup(effect, 1L, WorkEffectOrchidGroupRelationType.SOURCE),
						new WorkEffectOrchidGroup(effect, 3L, WorkEffectOrchidGroupRelationType.RESULT)));
			when(references.varietyNames(any())).thenReturn(Map.of(3L, "현재 품종"));
			var service = new WorkOperationDetailService(operations, effects, links, corrections, references);
			var actual = mapper.readTree(mapper.writeValueAsBytes(service.get(1L)));
			assertThat(actual).as(fixture.path("name").asText()).isEqualTo(fixture.get("expected"));
		}
	}

}
