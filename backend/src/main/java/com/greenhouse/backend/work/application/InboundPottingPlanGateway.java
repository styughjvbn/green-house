package com.greenhouse.backend.work.application;

import java.util.List;

public interface InboundPottingPlanGateway {
	List<InboundPottingPlanTarget> findCandidates();
	List<InboundPottingPlanTarget> resolve(List<Long> inboundRecordIds);
	List<InboundPottingPlanTarget> findCurrent(List<Long> inboundRecordIds);
	void markPottingPlanned(List<Long> inboundRecordIds);
	void closePottingPlan(List<Long> inboundRecordIds);
}
