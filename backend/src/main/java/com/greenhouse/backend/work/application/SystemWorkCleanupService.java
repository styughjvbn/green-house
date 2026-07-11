package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.repository.WorkRecordRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SystemWorkCleanupService {

	private static final String INBOUND_WORK_TYPE_CODE = "INBOUND";

	private final WorkRecordRepository workRecordRepository;

	public void deleteAutoInboundCreateRecords(Long inboundRecordId) {
		workRecordRepository.deleteByDetailsInboundRecordIdAndWorkTypeCode(
				inboundRecordId,
				INBOUND_WORK_TYPE_CODE);
	}
}
