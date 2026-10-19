package com.greenhouse.backend.ai.tool.application;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentToolGateway {
  private final VarietySearchApi varieties;
  private final BusinessPartnerQueryApi partners;
  private final Clock clock;
  private final int maxCalls;
  private final long timeoutSeconds;
  private final Map<String, Scope> scopes = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  public AgentToolGateway(
      VarietySearchApi varieties,
      BusinessPartnerQueryApi partners,
      Clock clock,
      @Value("${app.ai.max-tool-calls:20}") int maxCalls,
      @Value("${app.ai.timeout-seconds:120}") long timeoutSeconds) {
    this.varieties = varieties;
    this.partners = partners;
    this.clock = clock;
    if (maxCalls < 1 || maxCalls > 100 || timeoutSeconds < 1 || timeoutSeconds > 600)
      throw new IllegalArgumentException("AI limits are outside their supported range");
    this.maxCalls = maxCalls;
    this.timeoutSeconds = timeoutSeconds;
  }

  public Scope open(String requestId, String actor) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var scope = new Scope(requestId, actor, token, clock.instant().plusSeconds(timeoutSeconds));
    scopes.put(token, scope);
    return scope;
  }

  public List<?> execute(String authorization, String name, Arguments args) {
    String token =
        authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring(7)
            : "";
    var scope = scopes.get(token);
    if (scope == null || !scope.expiresAt.isAfter(clock.instant()))
      throw new AgentException(403, "AI_TOOL_ACCESS_DENIED", "유효한 AI 실행 권한이 필요합니다.");
    if (!Set.of("search_varieties", "search_partners").contains(name))
      throw new AgentException(403, "AI_TOOL_NOT_ALLOWED", "허용되지 않은 AI 도구입니다.");
    if (scope.calls.incrementAndGet() > maxCalls)
      throw new AgentException(429, "AI_TOOL_LIMIT", "도구 호출 한도를 초과했습니다.");
    if (args == null
        || args.query() == null
        || args.query().isBlank()
        || args.query().length() > 100
        || (args.genus() != null && args.genus().length() > 100))
      throw new AgentException(400, "AI_TOOL_ARGUMENT_INVALID", "검색어는 1~100자여야 합니다.");
    var log = LoggerFactory.getLogger(AgentToolGateway.class);
    long started = System.nanoTime();
    try {
      List<?> result;
      if (name.equals("search_varieties")) {
        var found = varieties.searchOptions(args.query().trim(), args.genus(), 10);
        found.forEach(v -> scope.varieties.put(v.id(), v));
        result = found;
      } else {
        var found = scope.lookupPartners(args.query(), args.auctionHouse());
        result = found;
      }
      log.info(
          "ai_tool requestId={} tool={} durationMs={} success=true count={}",
          scope.requestId,
          name,
          (System.nanoTime() - started) / 1000000,
          result.size());
      return result;
    } catch (RuntimeException e) {
      log.info(
          "ai_tool requestId={} tool={} durationMs={} success=false",
          scope.requestId,
          name,
          (System.nanoTime() - started) / 1000000);
      throw e;
    }
  }

  public record Arguments(String query, String genus, Boolean auctionHouse) {}

  private record PartnerLookup(String query, Boolean auctionHouse) {}

  public final class Scope implements AutoCloseable {
    public final String requestId;
    public final String actor;
    private final String token;
    private final Instant expiresAt;
    private final AtomicInteger calls = new AtomicInteger();
    private final Map<Long, VarietySearchApi.Option> varieties = new ConcurrentHashMap<>();
    private final Map<Long, BusinessPartnerQueryApi.Option> partners = new ConcurrentHashMap<>();
    private final Map<Long, Optional<BusinessPartnerQueryApi.Option>> partnerIdLookups =
        new ConcurrentHashMap<>();
    private final Map<PartnerLookup, List<BusinessPartnerQueryApi.Option>> partnerLookups =
        new ConcurrentHashMap<>();

    private Scope(String requestId, String actor, String token, Instant expiresAt) {
      this.requestId = requestId;
      this.actor = actor;
      this.token = token;
      this.expiresAt = expiresAt;
    }

    public String token() {
      return token;
    }

    public int calls() {
      return calls.get();
    }

    public VarietySearchApi.Option variety(Long id) {
      return id == null ? null : varieties.get(id);
    }

    /** Resolve configured defaults independently of names and agent search candidates. */
    public BusinessPartnerQueryApi.Option lookupPartnerById(long id) {
      if (id <= 0) return null;
      return partnerIdLookups
          .computeIfAbsent(
              id,
              key -> {
                try {
                  var found = AgentToolGateway.this.partners.getInfo(key);
                  return Optional.of(
                      new BusinessPartnerQueryApi.Option(
                          found.id(), found.name(), found.partnerType(), found.active()));
                } catch (NotFoundException e) {
                  return Optional.empty();
                }
              })
          .orElse(null);
    }

    /** Cache agent searches within this execution. */
    public List<BusinessPartnerQueryApi.Option> lookupPartners(String query, Boolean auctionHouse) {
      return partnerLookups.computeIfAbsent(
          new PartnerLookup(query.trim(), auctionHouse),
          key -> {
            var found =
                List.copyOf(
                    AgentToolGateway.this.partners.searchOptions(
                        key.query(), key.auctionHouse(), 10));
            found.forEach(p -> partners.put(p.id(), p));
            return found;
          });
    }

    public BusinessPartnerQueryApi.Option partner(Long id) {
      return id == null ? null : partners.get(id);
    }

    public void close() {
      scopes.remove(token, this);
    }
  }
}
