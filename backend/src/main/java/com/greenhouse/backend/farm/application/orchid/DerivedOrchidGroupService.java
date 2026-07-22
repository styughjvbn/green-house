package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import com.greenhouse.backend.farm.dto.orchid.DerivedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DerivedOrchidGroupService {

	private static final String UNKNOWN_AGE = "UNSPECIFIED";

	private final OrchidGroupRepository orchidGroupRepository;

	public List<DerivedOrchidGroupResponse> getGroups(
			Long varietyId,
			Integer ageYear,
			PotSizeCode potSizeCode,
			Long houseId,
			String status,
			String keyword) {
		List<OrchidGroup> candidates = findCandidates(varietyId, potSizeCode, houseId, status, keyword);
		Map<GroupKey, List<OrchidGroupResponse>> groups = new LinkedHashMap<>();
		for (OrchidGroup candidate : candidates) {
			OrchidGroupResponse member = OrchidGroupResponse.from(candidate);
			if (ageYear != null && !ageYear.equals(member.ageYear())) {
				continue;
			}
			GroupKey key = new GroupKey(member.varietyId(), member.ageYear(), member.potSizeCode());
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(member);
		}
		return groups.entrySet().stream()
				.map(entry -> toResponse(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(DerivedOrchidGroupResponse::varietyName)
						.thenComparing(DerivedOrchidGroupResponse::ageYear, Comparator.nullsLast(Integer::compareTo))
						.thenComparing(response -> response.potSizeCode().name()))
				.toList();
	}

	public List<OrchidGroupResponse> getMembers(
			String groupKey,
			Long houseId,
			String status,
			String keyword) {
		GroupKey key = parse(groupKey);
		List<OrchidGroupResponse> members = findCandidates(
				key.varietyId(), key.potSizeCode(), houseId, status, keyword).stream()
				.map(OrchidGroupResponse::from)
				.filter(member -> java.util.Objects.equals(member.ageYear(), key.ageYear()))
				.toList();
		if (members.isEmpty()) {
			throw new NotFoundException("현재 조건에 해당하는 자동 그룹을 찾을 수 없습니다.");
		}
		return members;
	}

	private List<OrchidGroup> findCandidates(
			Long varietyId,
			PotSizeCode potSizeCode,
			Long houseId,
			String status,
			String keyword) {
		if (potSizeCode == PotSizeCode.UNMAPPED) {
			return List.of();
		}
		return orchidGroupRepository.findDerivedGroupCandidates(
				varietyId,
				potSizeCode,
				houseId,
				normalize(status),
				normalize(keyword));
	}

	private DerivedOrchidGroupResponse toResponse(GroupKey key, List<OrchidGroupResponse> members) {
		OrchidGroupResponse first = members.getFirst();
		return new DerivedOrchidGroupResponse(
				key.serialize(),
				key.varietyId(),
				first.varietyName(),
				first.genus(),
				key.ageYear(),
				key.potSizeCode(),
				first.potSize(),
				members.size(),
				members.stream().mapToInt(OrchidGroupResponse::quantity).sum(),
				(int) members.stream().map(OrchidGroupResponse::bedZoneId).distinct().count());
	}

	private GroupKey parse(String value) {
		String[] parts = value.split(":", -1);
		if (parts.length != 3) {
			throw new IllegalArgumentException("자동 그룹 키 형식이 올바르지 않습니다.");
		}
		try {
			Long varietyId = Long.valueOf(parts[0]);
			Integer ageYear = UNKNOWN_AGE.equals(parts[1]) ? null : Integer.valueOf(parts[1]);
			PotSizeCode potSizeCode = PotSizeCode.valueOf(parts[2]);
			if (potSizeCode == PotSizeCode.UNMAPPED) {
				throw new IllegalArgumentException("검수 중인 화분 크기는 자동 그룹으로 사용할 수 없습니다.");
			}
			return new GroupKey(varietyId, ageYear, potSizeCode);
		} catch (IllegalArgumentException exception) {
			if (exception.getMessage() != null
					&& exception.getMessage().contains("검수 중인")) {
				throw exception;
			}
			throw new IllegalArgumentException("자동 그룹 키 형식이 올바르지 않습니다.");
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private record GroupKey(Long varietyId, Integer ageYear, PotSizeCode potSizeCode) {
		private String serialize() {
			return varietyId + ":" + (ageYear == null ? UNKNOWN_AGE : ageYear) + ":" + potSizeCode.name();
		}
	}
}
