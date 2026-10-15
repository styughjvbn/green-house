package com.greenhouse.backend.ai.slip.application;

import static com.greenhouse.backend.ai.slip.application.SlipFieldReview.Field.*;

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
    var reviews = new LinkedHashMap<String, SlipFieldReview>();
    for (var review : result.reviewFields()) {
      if (review.field().itemField() != (review.itemIndex() != null)
          || (review.itemIndex() != null && review.itemIndex() >= result.items().size()))
        throw new AgentException(502, "AI_INVALID_RESULT", "확인 대상 항목이 올바르지 않습니다.");
      addReview(reviews, review.field(), review.itemIndex(), review.reason());
    }
    SalesType type = result.salesType() == null ? null : SalesType.valueOf(result.salesType());
    String date = result.saleDate();
    if (date != null)
      try {
        if (!LocalDate.parse(date).toString().equals(date)) throw new IllegalArgumentException();
      } catch (RuntimeException e) {
        date = null;
        warnings.add("판매일·출하일을 확인해주세요.");
      }
    if (date == null) addReview(reviews, SALE_DATE, null, "판매일·출하일 확인 필요");
    var partner = scope.partner(result.partnerId());
    if (partner != null && (!partner.active() || partner.partnerType() == null)) partner = null;
    if (partner != null) {
      SalesType matchedType =
          partner.partnerType() == PartnerType.AUCTION_HOUSE ? SalesType.AUCTION : SalesType.DIRECT;
      if (type != null && type != matchedType)
        warnings.add(
            "거래처 유형에 맞춰 판매 유형을 "
                + (matchedType == SalesType.AUCTION ? "경매 판매" : "일반 판매")
                + "로 변경했습니다.");
      type = matchedType;
    } else {
      type = null;
      addReview(reviews, PARTNER, null, "거래처 직접 선택 필요");
      addReview(reviews, SALES_TYPE, null, "판매 유형 확인 필요");
    }
    if (partner != null && result.partnerName() == null)
      addReview(reviews, PARTNER, null, "추정 거래처 확인 필요");
    if (partner == null && result.partnerId() != null)
      warnings.add("거래처 매칭을 확인하지 못했습니다. 직접 선택해주세요.");
    var items = new ArrayList<SlipExtractionResult.Item>();
    for (var item : result.items()) {
      int index = items.size();
      if (item == null) throw new AgentException(502, "AI_INVALID_RESULT", "품목 인식 결과가 올바르지 않습니다.");
      var variety = scope.variety(item.varietyId());
      if (variety == null) addReview(reviews, ITEM_NAME, index, "품종 매칭 확인 필요");
      if (item.quantity() == null) addReview(reviews, QUANTITY, index, "수량 확인 필요");
      if (type == SalesType.DIRECT && item.unitPrice() == null)
        addReview(reviews, UNIT_PRICE, index, "단가 확인 필요");
      if (item.varietyId() != null && variety == null)
        warnings.add("품종 매칭을 확인하지 못했습니다: " + Objects.toString(item.itemName(), "미확인 품목"));
      Long calculated =
          item.quantity() != null && item.unitPrice() != null
              ? (long) item.quantity() * item.unitPrice()
              : null;
      if (calculated != null && item.amount() != null && !calculated.equals(item.amount())) {
        warnings.add(Objects.toString(item.itemName(), "품목") + ": 사진 금액과 수량 × 단가가 다릅니다.");
        addReview(reviews, QUANTITY, index, "사진 금액과 불일치·수량 확인 필요");
        addReview(reviews, UNIT_PRICE, index, "사진 금액과 불일치·단가 확인 필요");
        addReview(reviews, AMOUNT, index, "사진 금액 대조 필요");
      }
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
        result.attempts(),
        List.copyOf(reviews.values()));
  }

  private static void addReview(
      Map<String, SlipFieldReview> reviews,
      SlipFieldReview.Field field,
      Integer itemIndex,
      String reason) {
    reviews.putIfAbsent(
        field.name() + ":" + itemIndex, new SlipFieldReview(field, itemIndex, reason));
  }
}
