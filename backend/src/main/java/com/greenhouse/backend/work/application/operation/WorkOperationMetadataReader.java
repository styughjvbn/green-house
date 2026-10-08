package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.api.operation.WorkOperationMetadataApi;
import com.greenhouse.backend.work.api.operation.WorkOperationMetadataApi.WorkOperationMetadata;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationMetadataReader implements WorkOperationMetadataApi {

  private final WorkOperationRepository workOperationRepository;

  private final WorkOperationCorrectionRepository correctionRepository;

  @Override
  public Map<Long, WorkOperationMetadata> findOriginalsByCorrectionMutationIds(
      Collection<Long> mutationIds) {
    if (mutationIds.isEmpty()) return Map.of();
    return correctionRepository.findByMutationIdIn(mutationIds).stream()
        .collect(
            Collectors.toMap(
                correction -> correction.getMutationId(),
                correction -> {
                  var operation = correction.getOriginalWorkOperation();
                  return new WorkOperationMetadata(
                      operation.getId(),
                      operation.getWorkType().getCode(),
                      operation.getWorkType().getName(),
                      operation.getTitle());
                }));
  }

  @Override
  public List<WorkOperationMetadata> findByIds(Collection<Long> workOperationIds) {
    return workOperationRepository.findByIdIn(workOperationIds).stream()
        .map(
            operation ->
                new WorkOperationMetadata(
                    operation.getId(),
                    operation.getWorkType().getCode(),
                    operation.getWorkType().getName(),
                    operation.getTitle()))
        .toList();
  }
}
