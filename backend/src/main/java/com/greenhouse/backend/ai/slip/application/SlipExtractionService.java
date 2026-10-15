package com.greenhouse.backend.ai.slip.application;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.port.out.AgentRuntimePort;
import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.common.image.UploadImage;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlipExtractionService {
  private final AgentRuntimePort runtime;
  private final AgentToolGateway tools;
  private final Validator validator;
  private final Semaphore slot = new Semaphore(1);

  public SlipExtractionResult extract(String actor, String contentType, byte[] image) {
    UploadImage.validate(contentType, image);
    if (!slot.tryAcquire())
      throw new AgentException(429, "AI_BUSY", "다른 전표를 읽고 있습니다. 잠시 후 다시 시도해주세요.");
    String requestId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    boolean success = false;
    try (var scope = tools.open(requestId, actor)) {
      var result = runtime.extract(requestId, scope.token(), contentType, image);
      var validated = validate(requestId, result, scope);
      success = true;
      return validated;
    } finally {
      slot.release();
      LoggerFactory.getLogger(SlipExtractionService.class)
          .info(
              "ai_slip requestId={} durationMs={} success={}",
              requestId,
              (System.nanoTime() - started) / 1000000,
              success);
    }
  }

  SlipExtractionResult validate(
      String requestId, SlipModelResult result, AgentToolGateway.Scope scope) {
    if (result == null || !validator.validate(result).isEmpty())
      throw new AgentException(502, "AI_INVALID_RESULT", "인식 결과 형식이 올바르지 않습니다. 다시 시도해주세요.");
    var warnings = new ArrayList<>(result.warnings());
    SalesType type = result.salesType() == null ? null : SalesType.valueOf(result.salesType());
    String date = result.saleDate();
    if (date != null)
      try {
        if (!LocalDate.parse(date).toString().equals(date)) throw new IllegalArgumentException();
      } catch (RuntimeException e) {
        date = null;
        warnings.add("판매일·출하일을 확인해주세요.");
      }
    var partner = scope.partner(result.partnerId());
    if (partner != null
        && (type == null
            || !partner.active()
            || (type != null
                && (type == SalesType.AUCTION)
                    != (partner.partnerType() == PartnerType.AUCTION_HOUSE)))) partner = null;
    if (partner == null && result.partnerId() != null)
      warnings.add("거래처 매칭을 확인하지 못했습니다. 직접 선택해주세요.");
    var items = new ArrayList<SlipExtractionResult.Item>();
    for (var item : result.items()) {
      if (item == null) throw new AgentException(502, "AI_INVALID_RESULT", "품목 인식 결과가 올바르지 않습니다.");
      var variety = scope.variety(item.varietyId());
      if (item.varietyId() != null && variety == null)
        warnings.add("품종 매칭을 확인하지 못했습니다: " + Objects.toString(item.itemName(), "미확인 품목"));
      Long calculated =
          item.quantity() != null && item.unitPrice() != null
              ? (long) item.quantity() * item.unitPrice()
              : null;
      if (calculated != null && item.amount() != null && !calculated.equals(item.amount()))
        warnings.add(Objects.toString(item.itemName(), "품목") + ": 사진 금액과 수량 × 단가가 다릅니다.");
      items.add(
          new SlipExtractionResult.Item(
              variety == null ? item.itemName() : variety.name(),
              variety == null ? item.genus() : variety.genus(),
              variety == null ? null : variety.id(),
              item.itemName(),
              item.genus(),
              item.spec(),
              item.unit(),
              item.boxes(),
              item.quantity(),
              item.unitPrice(),
              item.amount(),
              calculated,
              item.memo()));
    }
    return new SlipExtractionResult(
        requestId,
        type,
        date,
        partner == null ? result.partnerName() : partner.name(),
        partner == null ? null : partner.id(),
        result.partnerName(),
        result.memo(),
        List.copyOf(items),
        List.copyOf(warnings),
        result.confidence(),
        result.imageLongEdge(),
        result.attempts());
  }
}
