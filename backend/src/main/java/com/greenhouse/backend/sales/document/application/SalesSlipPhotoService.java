package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.document.repository.SalesSlipPhotoRepository;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesSlipPhotoService {
  private final SalesSlipRepository slips;
  private final SalesSlipPhotoRepository photos;

  public PhotoInfo info(Long id) {
    if (!slips.existsById(id)) throw new NotFoundException("판매 전표를 찾을 수 없습니다.");
    return photos.findInfo(id).map(p -> new PhotoInfo(p.fileName(), p.contentType())).orElse(null);
  }

  public PhotoContent content(Long id) {
    var photo = photos.findById(id).orElseThrow(() -> new NotFoundException("첨부된 사진이 없습니다."));
    return new PhotoContent(
        photo.getFileName(),
        photo.getContentType(),
        Base64.getEncoder().encodeToString(photo.getContent()));
  }

  public record PhotoInfo(String fileName, String contentType) {}

  public record PhotoContent(String fileName, String contentType, String contentBase64) {}
}
