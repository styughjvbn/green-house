package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.dto.operation.WorkExecutionLocationResponse;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkExecutionReferenceReader {

	private final EntityManager entityManager;

	public Map<Long, String> varietyNames(Collection<Long> orchidGroupIds) {
		if (orchidGroupIds.isEmpty()) return Map.of();
		@SuppressWarnings("unchecked")
		var rows = (java.util.List<Object[]>) entityManager.createNativeQuery("""
				select id, variety_name
				from orchid_groups
				where id in (:ids)
				""", Object[].class)
				.setParameter("ids", orchidGroupIds)
				.getResultList();
		Map<Long, String> result = new LinkedHashMap<>();
		for (Object[] row : rows) {
			result.put(((Number) row[0]).longValue(), (String) row[1]);
		}
		return result;
	}

	public Map<Long, WorkExecutionLocationResponse> locations(Collection<Long> bedZoneIds) {
		if (bedZoneIds.isEmpty()) return Map.of();
		@SuppressWarnings("unchecked")
		var rows = (java.util.List<Object[]>) entityManager.createNativeQuery("""
				select z.id, h.number, b.number, z.name
				from bed_zones z
				join physical_beds b on b.id = z.physical_bed_id
				join houses h on h.id = b.house_id
				where z.id in (:ids)
				""", Object[].class)
				.setParameter("ids", bedZoneIds)
				.getResultList();
		Map<Long, WorkExecutionLocationResponse> result = new LinkedHashMap<>();
		for (Object[] row : rows) {
			result.put(
					((Number) row[0]).longValue(),
					new WorkExecutionLocationResponse(
							((Number) row[1]).intValue(),
							((Number) row[2]).intValue(),
							(String) row[3]));
		}
		return result;
	}
}
