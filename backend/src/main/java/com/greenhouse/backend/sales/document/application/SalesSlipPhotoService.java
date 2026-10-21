package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.document.application.port.out.SalesPhotoStorage;
import com.greenhouse.backend.sales.document.repository.SalesSlipPhotoRepository;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SalesSlipPhotoService {
  private final SalesSlipRepository slips;
  private final SalesSlipPhotoRepository photos;
  private final SalesPhotoReader reader;
  private final SalesPhotoStorage storage;

  @Transactional(readOnly = true)
  public PhotoInfo info(Long id) {
    if (!slips.existsById(id)) throw new NotFoundException("판매 전표를 찾을 수 없습니다.");
    return photos.findInfo(id).map(p -> new PhotoInfo(p.fileName(), p.contentType())).orElse(null);
  }

  public PhotoContent content(Long id) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Photo reads must start outside a database transaction");
    var photo = reader.source(id);
    byte[] bytes = storage.read(photo.storageKey());
    SalesPhotoIntegrity.verify(bytes, photo.byteSize(), photo.sha256());
    return new PhotoContent(
        photo.fileName(), photo.contentType(), Base64.getEncoder().encodeToString(bytes));
  }

  public record PhotoInfo(String fileName, String contentType) {}

  public record PhotoContent(String fileName, String contentType, String contentBase64) {}
}
