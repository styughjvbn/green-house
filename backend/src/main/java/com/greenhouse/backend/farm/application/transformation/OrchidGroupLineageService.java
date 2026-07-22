package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageItemResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageResponse;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidGroupLineageService {

	private final OrchidGroupLineageRepository lineageRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	@Transactional
	public OrchidGroupLineage record(
			OrchidGroup source,
			OrchidGroup result,
			OrchidGroupLineageRelationType relationType,
			WorkOperation workOperation,
			Integer sourceQuantity,
			Integer resultQuantity) {
		return lineageRepository.save(new OrchidGroupLineage(
				source, result, relationType, workOperation, sourceQuantity, resultQuantity));
	}

	@Transactional(readOnly = true)
	public OrchidGroupLineageResponse getLineage(Long orchidGroupId) {
		if (!orchidGroupRepository.existsById(orchidGroupId)) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		var sources = lineageRepository.findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(orchidGroupId).stream()
				.map(OrchidGroupLineageItemResponse::from)
				.toList();
		var results = lineageRepository.findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(orchidGroupId).stream()
				.map(OrchidGroupLineageItemResponse::from)
				.toList();
		return new OrchidGroupLineageResponse(orchidGroupId, sources, results);
	}
}
