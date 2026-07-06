package com.greenhouse.backend.dashboard.application;

import com.greenhouse.backend.dashboard.dto.DashboardSummaryResponse;
import com.greenhouse.backend.farm.application.FarmMetricsReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {
	private final FarmMetricsReader farmMetricsReader;

	public DashboardQueryService(FarmMetricsReader farmMetricsReader) {
		this.farmMetricsReader = farmMetricsReader;
	}

	public DashboardSummaryResponse getSummary() {
		var snapshot = farmMetricsReader.getSnapshot();
		return new DashboardSummaryResponse(
			snapshot.houseCount(),
			snapshot.physicalBedCount(),
			snapshot.bedZoneCount(),
			snapshot.orchidGroupCount(),
			snapshot.warningCount(),
			0,
			null);
	}
}
