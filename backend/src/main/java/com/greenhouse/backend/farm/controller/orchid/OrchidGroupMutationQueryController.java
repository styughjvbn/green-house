package com.greenhouse.backend.farm.controller.orchid;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationGraphQueryService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationQueryService;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationResponse;
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
  public ApiResponse<OrchidGroupMutationGraphResponse> getMutationGraph(
      @PathVariable Long orchidGroupId,
      @RequestParam(defaultValue = "2") int depth,
      @RequestParam(defaultValue = "120") int maxNodes) {
    return ApiResponse.ok(graphQueryService.getGraph(orchidGroupId, depth, maxNodes));
  }
}
