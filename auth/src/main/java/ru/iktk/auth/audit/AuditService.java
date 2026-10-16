package ru.iktk.auth.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.auth.model.AuditEvent;
import ru.iktk.auth.repository.AuditRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final List<String> SENSITIVE_KEYS = List.of(
            "password", "oldPassword", "newPassword", "passwordHash",
            "token", "accessToken", "refreshToken", "secret", "apiKey"
    );

    private static final String MASK = "***";

    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String userId, String action, Map<String, ?> details) {
        write(userId, action, AuditResult.SUCCESS, null, details);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String userId, String action, Map<String, ?> details) {
        String reason = details == null ? null : (details.get("reason") == null ? null : String.valueOf(details.get("reason")));
        write(userId, action, AuditResult.FAILURE, reason, details);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystem(String action, Map<String, ?> details) {
        write("SYSTEM", action, AuditResult.SUCCESS, null, details);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystemError(String action, String reason, Map<String, ?> details) {
        write("SYSTEM", action, AuditResult.FAILURE, reason, details);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String userId,
                      String action,
                      AuditResult result,
                      String reason,
                      Map<String, ?> details) {
        try {
            Map<String, Object> safeDetails = sanitize(details);

            AuditEvent event = new AuditEvent();
            event.setId(UUID.randomUUID().toString());
            event.setUserId(userId == null ? "UNKNOWN" : userId);
            event.setAction(action);
            event.setResult(result.name());
            event.setReason(reason);
            event.setDetails(safeDetails);
            event.setCreatedAt(Instant.now());

            event.setRequestId(asString(safeDetails.get("requestId")));
            event.setCorrelationId(asString(safeDetails.get("correlationId")));
            event.setIp(asString(safeDetails.get("ip")));
            event.setUserAgent(asString(safeDetails.get("userAgent")));

            auditRepository.save(event);

            log.debug("audit: userId={}, action={}, result={}, reason={}",
                    event.getUserId(), action, result, reason);
        } catch (Exception e) {
            log.error("audit: error writing event userId={}, action={}, result={}",
                    userId, action, result, e);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByUser(String userId, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        return auditRepository.findByUserIdOrderByCreatedAtDesc(userId, effectiveLimit)
                .stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByAction(String action, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        return auditRepository.findByActionOrderByCreatedAtDesc(action, effectiveLimit)
                .stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByUserAndAction(String userId, String action, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        return auditRepository.findByUserIdAndActionOrderByCreatedAtDesc(userId, action, effectiveLimit)
                .stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFailures(String userId, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        return auditRepository.findByUserIdAndResultOrderByCreatedAtDesc(
                        userId, AuditResult.FAILURE.name(), effectiveLimit)
                .stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByPeriod(Instant from, Instant to, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        return auditRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, effectiveLimit)
                .stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public long countLoginFailures(String userId, Instant since) {
        return auditRepository.countByUserIdAndActionAndResultAndCreatedAtAfter(
                userId, "LOGIN", AuditResult.FAILURE.name(), since);
    }

    private Map<String, Object> sanitize(Map<String, ?> details) {
        Map<String, Object> result = new HashMap<>();
        if (details == null || details.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitive(key)) {
                result.put(key, MASK);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, ?> nested = (Map<String, ?>) value;
                result.put(key, sanitize(nested));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private boolean isSensitive(String key) {
        if (key == null) return false;
        for (String s : SENSITIVE_KEYS) {
            if (s.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) return 100;
        return Math.min(limit, 1000);
    }

    private Map<String, Object> toMap(AuditEvent e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("userId", e.getUserId());
        m.put("action", e.getAction());
        m.put("result", e.getResult() == null ? "" : e.getResult().name());
        m.put("reason", e.getReason() == null ? "" : e.getReason());
        m.put("requestId", e.getRequestId() == null ? "" : e.getRequestId());
        m.put("ip", e.getIp() == null ? "" : e.getIp());
        m.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        m.put("details", e.getDetails() == null ? new HashMap<>() : e.getDetails());
        return m;
    }

    public String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("audit: error serializing details", e);
            return "{}";
        }
    }

    public enum AuditResult {
        SUCCESS, FAILURE
    }
}
