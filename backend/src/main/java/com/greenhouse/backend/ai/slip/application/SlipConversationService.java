package com.greenhouse.backend.ai.slip.application;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.port.out.AgentRuntimePort;
import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.common.image.UploadImage;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlipConversationService {
  private final AgentRuntimePort runtime;
  private final AgentToolGateway tools;
  private final SlipExtractionService extraction;
  private final Clock clock;
  private final VarietySearchApi varieties;
  private final Map<String, Conversation> sessions = new ConcurrentHashMap<>();
  private final Semaphore slot = new Semaphore(1);

  public synchronized SlipConversationResult.Session open(String actor) {
    sessions.entrySet().removeIf(e -> !e.getValue().expiresAt.isAfter(clock.instant()));
    if (sessions.size() >= 100)
      throw new AgentException(429, "AI_SESSION_LIMIT", "진행 중인 사진 등록이 많습니다. 잠시 후 다시 시도해주세요.");
    String id = UUID.randomUUID().toString();
    var varietyNames = varieties.findActiveNamesWithLinkedGroups();
    runtime.openSession(id, varietyNames);
    sessions.put(id, new Conversation(actor, clock.instant().plusSeconds(1800)));
    return new SlipConversationResult.Session(id);
  }

  public void close(String actor, String sessionId) {
    Conversation session = require(actor, sessionId);
    sessions.remove(sessionId, session);
    runtime.closeSession(sessionId);
  }

  public void removePhoto(String actor, String sessionId, String photoId) {
    var session = require(actor, sessionId);
    synchronized (session) {
      session.photos.remove(photoId);
    }
  }

  public SlipConversationResult extract(
      String actor, String sessionId, SlipConversationInput input) {
    var session = require(actor, sessionId);
    if (input == null
        || input.photos() == null
        || input.photos().isEmpty()
        || input.photos().size() > 10)
      throw new AgentException(400, "AI_INVALID_REQUEST", "사진은 한 번에 1~10장까지 읽을 수 있습니다.");
    var ids = new HashSet<String>();
    for (var photo : input.photos()) {
      if (photo == null
          || photo.photoId() == null
          || !photo.photoId().matches("[-a-zA-Z0-9]{1,64}")
          || !ids.add(photo.photoId())
          || (photo.feedback() != null && photo.feedback().length() > 1000))
        throw new AgentException(400, "AI_INVALID_REQUEST", "사진 ID와 피드백을 확인해주세요.");
      UploadImage.validate(photo.contentType(), photo.image());
    }
    if (!slot.tryAcquire())
      throw new AgentException(429, "AI_BUSY", "다른 전표를 읽고 있습니다. 잠시 후 다시 시도해주세요.");
    String requestId = UUID.randomUUID().toString();
    try (var scope = tools.open(requestId, actor)) {
      synchronized (session) {
        var next = new HashMap<>(session.photos);
        for (var photo : input.photos()) {
          String fingerprint = fingerprint(photo);
          if (next.containsKey(photo.photoId()) && !next.get(photo.photoId()).equals(fingerprint))
            throw new AgentException(409, "AI_PHOTO_ID_CONFLICT", "동일한 사진 ID에 다른 사진을 사용할 수 없습니다.");
          next.put(photo.photoId(), fingerprint);
        }
        if (next.size() > 10)
          throw new AgentException(400, "AI_INVALID_REQUEST", "등록 사진은 최대 10장입니다.");
        session.photos.clear();
        session.photos.putAll(next);
        session.expiresAt = clock.instant().plusSeconds(1800);
      }
      var result = runtime.extractConversation(requestId, sessionId, scope.token(), input);
      if (result == null || result.photos() == null || result.photos().size() != ids.size())
        throw invalidResult();
      var seen = new HashSet<String>();
      var photos = new ArrayList<SlipConversationResult.Photo>();
      for (var photo : result.photos()) {
        if (photo == null || !ids.contains(photo.photoId()) || !seen.add(photo.photoId()))
          throw invalidResult();
        if (photo.result() == null)
          photos.add(
              new SlipConversationResult.Photo(photo.photoId(), null, "AI_PHOTO_UNREADABLE"));
        else
          photos.add(
              new SlipConversationResult.Photo(
                  photo.photoId(), extraction.validate(requestId, photo.result(), scope), null));
      }
      return new SlipConversationResult(sessionId, requestId, List.copyOf(photos));
    } finally {
      slot.release();
    }
  }

  private Conversation require(String actor, String sessionId) {
    var session = sessions.get(sessionId);
    if (session == null
        || !session.actor.equals(actor)
        || !session.expiresAt.isAfter(clock.instant()))
      throw new AgentException(410, "AI_SESSION_EXPIRED", "사진 등록 대화가 만료되었습니다. 창을 닫고 다시 등록해주세요.");
    return session;
  }

  private AgentException invalidResult() {
    return new AgentException(502, "AI_INVALID_RESULT", "사진별 인식 결과를 확인하지 못했습니다. 다시 시도해주세요.");
  }

  private String fingerprint(SlipConversationInput.Photo photo) {
    try {
      var hash = MessageDigest.getInstance("SHA-256");
      hash.update(photo.contentType().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash.digest(photo.image()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class Conversation {
    final String actor;
    volatile Instant expiresAt;
    final Map<String, String> photos = new HashMap<>();

    Conversation(String actor, Instant expiresAt) {
      this.actor = actor;
      this.expiresAt = expiresAt;
    }
  }
}
