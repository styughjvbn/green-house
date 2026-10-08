package com.greenhouse.backend.sales.partner.repository;

import java.util.List;

public record PartnerSearchMatchRow(Long id, List<Integer> matchingSearchIndexes) {}
