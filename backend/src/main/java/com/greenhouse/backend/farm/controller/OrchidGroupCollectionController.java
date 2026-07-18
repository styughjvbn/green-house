package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.OrchidGroupCollectionService;
import com.greenhouse.backend.farm.dto.OrchidGroupCollectionCreateRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupCollectionMemberAddRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupCollectionResponse;
import com.greenhouse.backend.farm.dto.OrchidGroupCollectionUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrchidGroupCollectionController {

	private final OrchidGroupCollectionService collectionService;

	@GetMapping("/orchid-group-collections")
	public ApiResponse<List<OrchidGroupCollectionResponse>> getCollections(
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return ApiResponse.ok(collectionService.getCollections(includeArchived));
	}

	@PostMapping("/orchid-group-collections")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<OrchidGroupCollectionResponse> create(
			@Valid @RequestBody OrchidGroupCollectionCreateRequest request) {
		return ApiResponse.ok(collectionService.create(request));
	}

	@GetMapping("/orchid-group-collections/{collectionId}")
	public ApiResponse<OrchidGroupCollectionResponse> get(@PathVariable Long collectionId) {
		return ApiResponse.ok(collectionService.get(collectionId));
	}

	@PatchMapping("/orchid-group-collections/{collectionId}")
	public ApiResponse<OrchidGroupCollectionResponse> update(
			@PathVariable Long collectionId,
			@Valid @RequestBody OrchidGroupCollectionUpdateRequest request) {
		return ApiResponse.ok(collectionService.update(collectionId, request));
	}

	@PostMapping("/orchid-group-collections/{collectionId}/archive")
	public ApiResponse<OrchidGroupCollectionResponse> archive(@PathVariable Long collectionId) {
		return ApiResponse.ok(collectionService.archive(collectionId));
	}

	@PostMapping("/orchid-group-collections/{collectionId}/members")
	public ApiResponse<OrchidGroupCollectionResponse> addMembers(
			@PathVariable Long collectionId,
			@Valid @RequestBody OrchidGroupCollectionMemberAddRequest request) {
		return ApiResponse.ok(collectionService.addMembers(collectionId, request));
	}

	@DeleteMapping("/orchid-group-collections/{collectionId}/members/{orchidGroupId}")
	public ApiResponse<OrchidGroupCollectionResponse> removeMember(
			@PathVariable Long collectionId,
			@PathVariable Long orchidGroupId) {
		return ApiResponse.ok(collectionService.removeMember(collectionId, orchidGroupId));
	}

	@GetMapping("/orchid-groups/{orchidGroupId}/collections")
	public ApiResponse<List<OrchidGroupCollectionResponse>> getForOrchidGroup(
			@PathVariable Long orchidGroupId) {
		return ApiResponse.ok(collectionService.getCollectionsForOrchidGroup(orchidGroupId));
	}
}
