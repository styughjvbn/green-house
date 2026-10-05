package com.greenhouse.backend.partner.repository;

import java.util.List;

public record PartnerSearchMatchRow(Long id, List<Integer> matchingSearchIndexes) {}
