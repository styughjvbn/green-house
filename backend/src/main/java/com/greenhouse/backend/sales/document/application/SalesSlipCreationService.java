package com.greenhouse.backend.sales.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.application.port.out.SalesPhotoStorage;
import com.greenhouse.backend.sales.document.domain.SalesSlipPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SalesSlipCreationService {
  private final SalesSlipCreationTransaction transaction;
  private final SalesPhotoStorage storage;
  private final SalesPhotoCleanup cleanup;
  private final SalesPhotoCreationReplay replay;

  public SalesSlipDocument create(SalesSlipCommand request) {
    return create(request, null);
  }

  public SalesSlipDocument create(SalesSlipCommand request, String idempotencyKey) {
    var photo = request.sourcePhoto();
    if (photo == null) return transaction.create(request, idempotencyKey, null);
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Photo creation must start outside a database transaction");
    var completed = replay.find(request, idempotencyKey);
    if (completed != null) return completed;
    byte[] bytes =
        SalesSlipPhoto.validate(photo.fileName(), photo.contentType(), photo.contentBase64());
    var stored = storage.store(bytes, photo.contentType());
    try {
      SalesPhotoIntegrity.verify(bytes, stored.byteSize(), stored.sha256());
    } catch (RuntimeException failure) {
      cleanup.discard(stored.key());
      throw failure;
    }
    // The transaction registers cleanup only for known rollback or an unused replay upload.
    // Connection/commit ambiguity leaves the immutable object available for reconciliation.
    return transaction.create(request, idempotencyKey, stored);
  }

  static SalesSlipDocument readStoredResponse(String snapshot) throws JsonProcessingException {
    return SalesSlipCreationTransaction.readStoredResponse(snapshot);
  }
}
