package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import com.greenhouse.backend.farm.structure.domain.House;
import com.greenhouse.backend.farm.structure.domain.PhysicalBed;
import com.greenhouse.backend.farm.variety.domain.Variety;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

class VarietyQueryIntegrationTests extends AbstractBackendIntegrationTest {

  @Autowired VarietySearchApi varietySearchApi;

  @Test
  @Transactional
  void aiCandidatesCanBeFoundUsingASingleLegibleCharacter() {
    var candidate = saveCandidate("SHORT-READ", "테스트속", "대구 싱싱", null, true);
    assertThat(varietySearchApi.searchOptions("대", null, 10))
        .extracting(VarietySearchApi.Option::id)
        .contains(candidate.getId());
    assertThat(varietySearchApi.searchOptions("대구상상", null, 10))
        .extracting(VarietySearchApi.Option::id)
        .contains(candidate.getId());
  }

  @Test
  @Transactional
  void uniquePrefixesCountVarietiesOnceAcrossNamesAndAliasesWithoutUsingLimitOrGenus() {
    var candidate = saveCandidate("PREFIX-ONE", "다른속", "유일가나", "유일가별칭", false);
    saveCandidate("PREFIX-TWO", "힌트속", "유일다라", null, true);
    assertThat(varietySearchApi.searchOptions("유일가오독", "힌트속", 1))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(candidate.getId());
    assertThat(varietySearchApi.searchOptions("유일오독", "힌트속", 1)).isEmpty();
  }

  @Test
  @Transactional
  void sessionReferenceContainsAllDistinctActiveNamesWithLinkedGroupsEvenAtZeroQuantity() {
    var house = new House(99, "초기 참조 테스트동");
    var bed = new PhysicalBed(1, 1);
    var zone = new BedZone("참조 구역", BedZoneSide.LEFT, 1);
    bed.addBedZone(zone);
    house.addPhysicalBed(bed);
    houseRepository.save(house);
    var expected = new ArrayList<String>();
    for (int i = 0; i < 12; i++) {
      String name = "세션참조품종" + i;
      var variety = saveCandidate("REF-" + i, "참조속", name, "노출하지않는별명", true);
      saveOrchidGroup(createGroup(zone, variety, i == 0 ? 0 : 1, i + 1));
      expected.add(name);
    }
    var duplicate = saveCandidate("REF-DUPLICATE", "다른속", expected.getFirst(), null, true);
    saveOrchidGroup(createGroup(zone, duplicate, 1, 13));
    saveOrchidGroup(createGroup(zone, duplicate, 1, 14));
    var inactive = saveCandidate("REF-INACTIVE", "참조속", "세션비활성품종", null, false);
    saveOrchidGroup(createGroup(zone, inactive, 1, 15));
    saveCandidate("REF-UNLINKED", "참조속", "세션미연결품종", null, true);
    // Legacy text without an actual variety relationship must not qualify a variety.
    saveOrchidGroup(new OrchidGroup(zone, "참조속", "세션미연결품종", 1, "2치", 1, "정상", 16, null, null));

    var names = varietySearchApi.findActiveNamesWithLinkedGroups();
    assertThat(names).containsAll(expected).doesNotHaveDuplicates().isSorted();
    assertThat(names).doesNotContain("세션비활성품종", "세션미연결품종", "노출하지않는별명");
  }

  @Test
  @Transactional
  void aiCandidatesSearchNamesAndAliasesButNotCodesOrGenusAlone() {
    var name = saveCandidate("AI-NAME", "다른속", "검색테스트목련", null, true);
    var alias = saveCandidate("AI-ALIAS", "다른속", "별도품종", "검색테스트목련", true);
    saveCandidate("검색테스트목련-CODE", "힌트속", "무관한품종", null, true);
    saveCandidate("AI-GENUS", "검색테스트목련", "속만일치", null, true);

    assertThat(varietySearchApi.searchOptions("검색테스트목련", "힌트속", 10))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(name.getId(), alias.getId());
    assertThat(varietySearchApi.searchOptions("AI-NAME", null, 10)).isEmpty();
    assertThat(varietyRepository.search("AI-NAME", null, null, PageRequest.of(0, 10)))
        .extracting(Variety::getId)
        .containsExactly(name.getId());
  }

  @Test
  @Transactional
  void aiCandidatesRankNameRelevanceBeforeGenusAndApplyLimitAfterRanking() {
    for (int i = 0; i < 12; i++) saveCandidate("AI-PARTIAL-" + i, "힌트속", "가테스트꽃" + i, null, true);
    var exact = saveCandidate("AI-EXACT", "다른속", "테스트꽃", null, false);
    var alias = saveCandidate("AI-EXACT-ALIAS", "힌트속", "가별명품종", "테스트꽃", true);
    var partialAlias = saveCandidate("AI-PARTIAL-ALIAS", "힌트속", "가품종", "테스트꽃별명", true);

    assertThat(varietySearchApi.searchOptions("테스트꽃", "힌트속", 2))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(exact.getId(), alias.getId());
    var all = varietySearchApi.searchOptions("테스트꽃", "힌트속", 20);
    assertThat(all).hasSize(15);
    assertThat(all.getFirst().active()).isFalse();
    assertThat(all.getLast().id()).isEqualTo(partialAlias.getId());
  }

  @Test
  @Transactional
  void aiCandidatesIgnoreInternalSpacesInNamesAliasesAndQueries() {
    var compact = saveCandidate("SPACE-COMPACT", "테스트속", "하니문", null, true);
    var spaced = saveCandidate("SPACE-SPACED", "테스트속", "하니 문", null, true);
    var alias = saveCandidate("SPACE-ALIAS", "테스트속", "별명공백품종", "하 니 문", true);
    var partial = saveCandidate("SPACE-PARTIAL", "테스트속", "하 니문골드", null, true);
    saveCandidate("하니문-CODE", "테스트속", "무관한공백품종", null, true);

    assertThat(varietySearchApi.searchOptions("하니 문", null, 10))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(spaced.getId(), compact.getId(), alias.getId(), partial.getId());
    assertThat(varietySearchApi.searchOptions("하니문", null, 10))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(compact.getId(), spaced.getId(), alias.getId(), partial.getId());
    assertThat(varietySearchApi.searchOptions("니 문", null, 10))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(spaced.getId(), partial.getId(), compact.getId(), alias.getId());
  }

  @Test
  @Transactional
  void aiCandidatesIgnoreCommonOcrWhitespaceAndRejectWhitespaceOnlySearches() {
    var mixed = saveCandidate("SPACE-MIXED", "테스트속", "공\t백\u00a0테\u3000스\n트", null, true);
    assertThat(varietySearchApi.searchOptions("공 백테\r스트", null, 10))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(mixed.getId());
    assertThat(varietySearchApi.searchOptions("\u00a0\u3000\t", null, 10)).isEmpty();
  }

  @Test
  @Transactional
  void aiCandidatesUseGenusOnlyToBreakEqualRelevanceAndNormalizeQueries() {
    var other = saveCandidate("AI-OTHER", "가다른속", "ReviewFlower", null, true);
    var hint = saveCandidate("AI-HINT", "힌트속", "ReviewFlower", null, true);
    var alias = saveCandidate("AI-CASE-ALIAS", "힌트속", "별명테스트", "REVIEWFLOWER", true);

    assertThat(varietySearchApi.searchOptions("  reviewflower  ", " 힌트속 ", 10))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(hint.getId(), other.getId(), alias.getId());
    assertThat(varietySearchApi.searchOptions("reviewflower", null, 10))
        .extracting(VarietySearchApi.Option::id)
        .containsExactly(other.getId(), hint.getId(), alias.getId());
    assertThat(varietySearchApi.searchOptions("reviewflower", " ", 10))
        .isEqualTo(varietySearchApi.searchOptions("reviewflower", null, 10));
    assertThat(varietySearchApi.searchOptions(" ", null, 10)).isEmpty();
  }

  private Variety saveCandidate(
      String code, String genus, String name, String alias, boolean active) {
    return varietyRepository.save(new Variety(code, genus, name, alias, "2치", active, null, null));
  }

  @Test
  @Transactional
  void returnsVarietySummariesFromBatchLoadedOrchidGroups() throws Exception {
    var firstVariety =
        varietyRepository.save(
            new Variety("BATCH-001", "테스트속", "배치품종A", null, "2치", true, null, null));
    var secondVariety =
        varietyRepository.save(
            new Variety("BATCH-002", "테스트속", "배치품종B", null, "3치", true, null, null));
    var house = new House(99, "배치 테스트동");
    var bed = new PhysicalBed(1, 1);
    var zone = new BedZone("테스트 구역", BedZoneSide.LEFT, 1);
    bed.addBedZone(zone);
    house.addPhysicalBed(bed);
    houseRepository.save(house);

    saveOrchidGroup(createGroup(zone, firstVariety, 12, 1));
    saveOrchidGroup(createGroup(zone, secondVariety, 7, 2));

    mockMvc
        .perform(
            get("/api/varieties").param("keyword", "배치품종").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].totalQuantity").value(12))
        .andExpect(jsonPath("$.data.content[1].totalQuantity").value(7));
  }

  @Test
  @Transactional
  void returnsGeneraAndActiveVarietyNames() throws Exception {
    varietyRepository.save(
        new Variety("GENERA-001", "카틀레야", "활성품종A", null, "2치", true, null, null));
    varietyRepository.save(
        new Variety("GENERA-002", "덴드로비움", "활성품종B", null, "3치", true, null, null));
    varietyRepository.save(
        new Variety("GENERA-003", "카틀레야", "비활성품종", null, "3치", false, null, null));

    mockMvc
        .perform(get("/api/varieties/genera"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.genera").isArray())
        .andExpect(jsonPath("$.data.varieties").isArray())
        .andExpect(jsonPath("$.data.varieties[?(@.name == '활성품종A')]").exists())
        .andExpect(jsonPath("$.data.varieties[?(@.name == '활성품종B')]").exists())
        .andExpect(jsonPath("$.data.varieties[?(@.name == '비활성품종')]").doesNotExist());
  }

  private OrchidGroup createGroup(BedZone zone, Variety variety, int quantity, int sortOrder) {
    var group =
        new OrchidGroup(
            zone,
            variety.getGenus(),
            variety.getName(),
            quantity,
            variety.getDefaultPotSize(),
            1,
            "정상",
            sortOrder,
            null,
            null);
    group.assignVariety(variety);
    return group;
  }
}
