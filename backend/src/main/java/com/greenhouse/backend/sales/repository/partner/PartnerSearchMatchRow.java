package com.greenhouse.backend.sales.repository.partner;

import java.util.List;

public record PartnerSearchMatchRow(Long id, List<Integer> matchingSearchIndexes) {}
