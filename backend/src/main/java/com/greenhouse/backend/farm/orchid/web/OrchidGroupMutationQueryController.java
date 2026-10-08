package com.greenhouse.backend.farm.orchid.web;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import com.greenhouse.backend.farm.mutation.query.OrchidGroupMutationGraphQueryService;
import com.greenhouse.backend.farm.mutation.query.OrchidGroupMutationQueryService;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupMutationGraphResponse;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupMutationResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchid-group-mutations")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.environment", havingValue = "dev")
public class OrchidGroupMutationQueryController {

  private final OrchidGroupMutationQueryService queryService;

  private final OrchidGroupMutationGraphQueryService graphQueryService;

  @GetMapping
  public ApiResponse<PageResponse<OrchidGroupMutationResponse>> getMutations(
      @RequestParam(required = false) Long orchidGroupId,
      @RequestParam(required = false) OrchidGroupMutationType mutationType,
      @RequestParam(required = false) OrchidGroupMutationSourceDomain sourceDomain,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        queryService.getMutations(orchidGroupId, mutationType, sourceDomain, page, size));
  }

  @GetMapping("/graph/{orchidGroupId}")
  @Operation(
      description =
          "노드·깊이 한도 안에서 탐색하며 표시되는 Mutation 간 관계는 최대 maxNodes × 4건이다. 노드·탐색·관계가 생략되면 truncated=true이며 전체 이력으로 취급하지 않는다.")
  public ApiResponse<OrchidGroupMutationGraphResponse> getMutationGraph(
      @PathVariable Long orchidGroupId,
      @RequestParam(defaultValue = "2") int depth,
      @RequestParam(defaultValue = "120") int maxNodes) {
    return ApiResponse.ok(graphQueryService.getGraph(orchidGroupId, depth, maxNodes));
  }
}
