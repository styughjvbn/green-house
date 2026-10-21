package com.greenhouse.backend.sales.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.repository.SalesCreationReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesPhotoCreationReplay {
  private final SalesCreationReceiptRepository receipts;

  public SalesSlipDocument find(SalesSlipCommand request, String key) {
    if (key == null) return null;
    if (key.isBlank() || key.length() > 100)
      throw new IllegalArgumentException("판매 생성 요청 키는 1~100자의 공백이 아닌 값이 필요합니다.");
    var receipt = receipts.findById(key).orElse(null);
    if (receipt == null) return null;
    receipt.validate(SalesSlipCreationTransaction.fingerprint(request));
    if (receipt.getResponseSnapshot() == null) return null;
    try {
      return SalesSlipCreationTransaction.readStoredResponse(receipt.getResponseSnapshot());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("판매 생성 응답을 읽지 못했습니다.", e);
    }
  }
}
