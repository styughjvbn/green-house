package com.greenhouse.backend.farm.mutation.query;

import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.mutation.ledger.repository.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.mutation.ledger.repository.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.mutation.ledger.repository.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupMutationEntryResponse;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupMutationRelationResponse;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupMutationResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrchidGroupMutationQueryService {

  private final OrchidGroupMutationRepository mutationRepository;

  private final OrchidGroupMutationEntryRepository entryRepository;

  private final OrchidGroupMutationRelationRepository relationRepository;

  private final OrchidGroupMutationWorkOperationReader workOperationReader;

  public PageResponse<OrchidGroupMutationResponse> getMutations(
      Long orchidGroupId,
      OrchidGroupMutationType mutationType,
      OrchidGroupMutationSourceDomain sourceDomain,
      int page,
      int size) {
    PageRequests.validate(page, size);
    var mutations =
        mutationRepository.search(
            orchidGroupId,
            mutationType,
            sourceDomain,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    if (mutations.isEmpty()) {
      return PageResponse.from(
          mutations.map(
              mutation -> OrchidGroupMutationResponse.from(mutation, null, List.of(), List.of())));
    }

    List<Long> mutationIds = mutations.stream().map(mutation -> mutation.getId()).toList();
    Map<Long, List<OrchidGroupMutationEntryResponse>> entriesByMutationId = new LinkedHashMap<>();
    entryRepository
        .findByMutationIdInOrderByMutationIdAscIdAsc(mutationIds)
        .forEach(
            entry ->
                entriesByMutationId
                    .computeIfAbsent(entry.getMutation().getId(), ignored -> new ArrayList<>())
                    .add(OrchidGroupMutationEntryResponse.from(entry)));

    Map<Long, List<OrchidGroupMutationRelationResponse>> relationsByMutationId =
        new LinkedHashMap<>();
    relationRepository
        .findConnectedToMutationIds(mutationIds)
        .forEach(
            relation -> {
              var response = OrchidGroupMutationRelationResponse.from(relation);
              addRelation(relationsByMutationId, response.mutationId(), response);
              if (!response.mutationId().equals(response.relatedMutationId())) {
                addRelation(relationsByMutationId, response.relatedMutationId(), response);
              }
            });

    var workOperationsByMutationId =
        workOperationReader.resolveByMutationId(mutations.getContent());
    return PageResponse.from(
        mutations.map(
            mutation ->
                OrchidGroupMutationResponse.from(
                    mutation,
                    workOperationsByMutationId.get(mutation.getId()),
                    entriesByMutationId.getOrDefault(mutation.getId(), List.of()),
                    relationsByMutationId.getOrDefault(mutation.getId(), List.of()))));
  }

  private void addRelation(
      Map<Long, List<OrchidGroupMutationRelationResponse>> relationsByMutationId,
      Long mutationId,
      OrchidGroupMutationRelationResponse relation) {
    relationsByMutationId.computeIfAbsent(mutationId, ignored -> new ArrayList<>()).add(relation);
  }
}
