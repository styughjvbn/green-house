package com.greenhouse.backend.sales.partner.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.api.partner.PartnerTextMatch;
import com.greenhouse.backend.sales.api.partner.PartnerTextSearch;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BusinessPartnerReader implements BusinessPartnerQueryApi {

  private static final int ID_BATCH_SIZE = 500;
  private static final int SEARCH_BATCH_SIZE = 32;

  private final BusinessPartnerRepository partnerRepository;

  /** All matching IDs, read in bounded batches; historical searches include inactive partners. */
  @Override
  public List<Long> findMatchingIds(PartnerTextMatch match, String value) {
    var matches = new ArrayList<Long>();
    long afterId = 0;
    while (true) {
      var batch = partnerRepository.findMatchingIds(match, value, afterId, ID_BATCH_SIZE);
      matches.addAll(batch);
      if (batch.size() < ID_BATCH_SIZE) return List.copyOf(matches);
      afterId = batch.getLast();
    }
  }

  @Override
  public Map<PartnerTextSearch, List<Long>> findMatchingIds(
      Collection<PartnerTextSearch> searches) {
    var unique = new ArrayList<>(new LinkedHashSet<>(searches));
    if (unique.isEmpty()) return Map.of();
    if (unique.size() == 1) {
      var term = unique.getFirst();
      return Map.of(term, findMatchingIds(term.match(), term.value()));
    }
    var matches = new LinkedHashMap<PartnerTextSearch, List<Long>>();
    unique.forEach(term -> matches.put(term, new ArrayList<>()));
    for (int start = 0; start < unique.size(); start += SEARCH_BATCH_SIZE) {
      var terms = unique.subList(start, Math.min(start + SEARCH_BATCH_SIZE, unique.size()));
      long afterId = 0;
      while (true) {
        var batch = partnerRepository.findMatchingIds(terms, afterId, ID_BATCH_SIZE);
        for (var row : batch) {
          for (var index : row.matchingSearchIndexes()) matches.get(terms.get(index)).add(row.id());
        }
        if (batch.size() < ID_BATCH_SIZE) break;
        afterId = batch.getLast().id();
      }
    }
    matches.replaceAll((term, ids) -> List.copyOf(ids));
    return Map.copyOf(matches);
  }

  @Override
  public Map<Long, Identity> getIdentities(Collection<Long> partnerIds) {
    var ids = new ArrayList<>(new HashSet<>(partnerIds));
    var identities = new HashMap<Long, Identity>();
    for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
      var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
      for (var row : partnerRepository.findIdentities(batch)) {
        identities.put(row.getId(), new Identity(row.getId(), row.getName(), row.getPartnerType()));
      }
    }
    if (identities.size() != ids.size()) {
      throw new NotFoundException("거래처를 찾을 수 없습니다.");
    }
    return Map.copyOf(identities);
  }

  @Override
  public BusinessPartnerInfo getInfo(Long partnerId) {
    return partnerRepository
        .findById(partnerId)
        .map(BusinessPartnerInfoFactory::from)
        .orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
  }

  @Override
  public BusinessPartnerInfo getActiveInfo(Long partnerId) {
    var partner = getInfo(partnerId);
    if (!partner.active()) {
      throw new IllegalArgumentException("비활성 거래처는 사용할 수 없습니다.");
    }
    return partner;
  }

  @Override
  public Map<Long, BusinessPartnerInfo> getAllInfo(Collection<Long> partnerIds) {
    var requestedIds = new HashSet<>(partnerIds);
    if (requestedIds.isEmpty()) {
      return Map.of();
    }
    var ids = new ArrayList<>(requestedIds);
    var partners = new ArrayList<BusinessPartner>();
    for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
      partners.addAll(
          partnerRepository.findAllById(
              ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()))));
    }
    if (partners.size() != requestedIds.size()) {
      throw new NotFoundException("거래처를 찾을 수 없습니다.");
    }
    return partners.stream()
        .map(BusinessPartnerInfoFactory::from)
        .collect(Collectors.toUnmodifiableMap(BusinessPartnerInfo::id, Function.identity()));
  }
}
