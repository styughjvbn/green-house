package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboundRecordFinder {

  private final InboundRecordRepository inboundRecordRepository;

  public InboundRecord find(Long inboundRecordId) {
    return inboundRecordRepository
        .findWithDetailsById(inboundRecordId)
        .orElseThrow(() -> new NotFoundException("입고 기록을 찾을 수 없습니다."));
  }

  public InboundRecord findForUpdate(Long inboundRecordId) {
    return inboundRecordRepository.findAllForUpdateByIdIn(List.of(inboundRecordId)).stream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("입고 기록을 찾을 수 없습니다."));
  }
}
