package com.greenhouse.backend.audit.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.audit.domain.AuditEventEntity;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidGroupAuditHistoryReader {

	private static final String ENTITY_TYPE = "ORCHID_GROUP";
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

	private final AuditEventRepository repository;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Transactional(readOnly = true)
	public List<OrchidGroupAuditHistoryEvent> findAfter(long afterId, Instant sourceCutoff, int limit) {
		if (afterId < 0 || sourceCutoff == null || limit < 1 || limit > 500) {
			throw new IllegalArgumentException("OrchidGroup Audit 이력 조회 범위가 올바르지 않습니다.");
		}
		return repository.findHistoryAfter(
				ENTITY_TYPE, afterId, sourceCutoff, PageRequest.of(0, limit)).stream()
				.map(this::toHistoryEvent)
				.toList();
	}

	private OrchidGroupAuditHistoryEvent toHistoryEvent(AuditEventEntity entity) {
		return new OrchidGroupAuditHistoryEvent(
				entity.getId(),
				entity.getOccurredAt(),
				entity.getAction(),
				entity.getEntityId(),
				Arrays.asList(entity.getChangedFields()),
				map(entity.getBeforeData()),
				map(entity.getAfterData()),
				map(entity.getContextData()));
	}

	private Map<String, Object> map(Object value) {
		return value == null ? Map.of() : objectMapper.convertValue(value, MAP_TYPE);
	}
}
