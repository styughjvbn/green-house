package com.greenhouse.backend.farm.controller.orchid;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.orchid.DerivedOrchidGroupService;
import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import com.greenhouse.backend.farm.dto.orchid.DerivedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchid-groups/derived-groups")
@RequiredArgsConstructor
public class DerivedOrchidGroupController {

	private final DerivedOrchidGroupService derivedOrchidGroupService;

	@GetMapping
	public ApiResponse<List<DerivedOrchidGroupResponse>> getGroups(
			@RequestParam(required = false) Long varietyId,
			@RequestParam(required = false) Integer ageYear,
			@RequestParam(required = false) PotSizeCode potSizeCode,
			@RequestParam(required = false) Long houseId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword) {
		return ApiResponse.ok(derivedOrchidGroupService.getGroups(
				varietyId, ageYear, potSizeCode, houseId, status, keyword));
	}

	@GetMapping("/{groupKey}/members")
	public ApiResponse<List<OrchidGroupResponse>> getMembers(
			@PathVariable String groupKey,
			@RequestParam(required = false) Long houseId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword) {
		return ApiResponse.ok(derivedOrchidGroupService.getMembers(groupKey, houseId, status, keyword));
	}
}
