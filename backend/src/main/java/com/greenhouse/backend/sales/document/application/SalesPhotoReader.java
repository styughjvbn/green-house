package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.document.repository.SalesSlipPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesPhotoReader {
  private final SalesSlipPhotoRepository photos;

  public PhotoSource source(Long id) {
    var photo = photos.findById(id).orElseThrow(() -> new NotFoundException("첨부된 사진이 없습니다."));
    return new PhotoSource(
        photo.getFileName(),
        photo.getContentType(),
        photo.getStorageKey(),
        photo.getByteSize(),
        photo.getSha256());
  }

  public record PhotoSource(
      String fileName, String contentType, String storageKey, Long byteSize, String sha256) {}
}
