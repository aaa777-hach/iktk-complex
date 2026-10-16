package ru.iktk.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.model.DecisionLogEntry;
import ru.iktk.orchestrator.repository.DecisionLogRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DecisionLogService {

    private static final Logger log = LoggerFactory.getLogger(DecisionLogService.class);

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final DecisionLogRepository decisionLogRepository;

    public DecisionLogService(DecisionLogRepository decisionLogRepository) {
        this.decisionLogRepository = decisionLogRepository;
    }

    @Transactional
    public DecisionLogEntry log(String projectId, String eventType, Map<String, ?> payload) {
        return log(projectId, eventType, payload, "SYSTEM");
    }

    @Transactional
    public DecisionLogEntry log(String projectId,
                                String eventType,
                                Map<String, ?> payload,
                                String actor) {
        if (projectId == null || projectId.isBlank()) {
            throw new OrchestrationException("РќРµ СѓРєР°Р·Р°РЅ projectId РґР»СЏ Р¶СѓСЂРЅР°Р»Р° СЂРµС€РµРЅРёР№");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new OrchestrationException("РќРµ СѓРєР°Р·Р°РЅ eventType РґР»СЏ Р¶СѓСЂРЅР°Р»Р° СЂРµС€РµРЅРёР№");
        }

        Map<String, Object> safePayload = toObjectMap(payload);

        DecisionLogEntry entry = DecisionLogEntry.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .eventType(eventType)
                .payload(safePayload)
                .actor(actor == null ? "SYSTEM" : actor)
                .timestamp(Instant.now())
                .build();

        decisionLogRepository.save(entry);

        log.debug("DecisionLog: projectId={}, eventType={}, actor={}",
                projectId, eventType, entry.getActor());

        return entry;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(String projectId, int limit) {
        validateProjectId(projectId);
        int effectiveLimit = normalizeLimit(limit);
        List<DecisionLogEntry> entries =
                decisionLogRepository.findByProjectIdOrderByTimestampDesc(projectId);
        return toLimitedMapList(entries, effectiveLimit);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistoryByEventType(String projectId,
                                                           String eventType,
                                                           int limit) {
        validateProjectId(projectId);
        int effectiveLimit = normalizeLimit(limit);
        List<DecisionLogEntry> entries =
                decisionLogRepository.findByProjectIdAndEventTypeOrderByTimestampDesc(projectId, eventType);
        return toLimitedMapList(entries, effectiveLimit);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFullHistory(String projectId) {
        validateProjectId(projectId);
        List<DecisionLogEntry> entries =
                decisionLogRepository.findByProjectIdOrderByTimestampAsc(projectId);
        return toLimitedMapList(entries, MAX_LIMIT);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLastEntry(String projectId) {
        validateProjectId(projectId);
        return decisionLogRepository
                .findTopByProjectIdOrderByTimestampDesc(projectId)
                .map(this::toMap)
                .orElseGet(HashMap::new);
    }

    @Transactional(readOnly = true)
    public long count(String projectId) {
        validateProjectId(projectId);
        return decisionLogRepository.countByProjectId(projectId);
    }

    private void validateProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new OrchestrationException("РќРµ СѓРєР°Р·Р°РЅ projectId РґР»СЏ С‡С‚РµРЅРёСЏ Р¶СѓСЂРЅР°Р»Р°");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<Map<String, Object>> toLimitedMapList(List<DecisionLogEntry> entries, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (DecisionLogEntry e : entries) {
            if (count >= limit) break;
            result.add(toMap(e));
            count++;
        }
        return result;
    }

    private Map<String, Object> toMap(DecisionLogEntry entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entry.getId());
        map.put("projectId", entry.getProjectId());
        map.put("eventType", entry.getEventType());
        map.put("actor", entry.getActor());
        map.put("timestamp", entry.getTimestamp() == null ? "" : entry.getTimestamp().toString());
        map.put("payload", toObjectMap(entry.getPayload()));
        return map;
    }

    private Map<String, Object> toObjectMap(Map<String, ?> source) {
        Map<String, Object> result = new HashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, ?> e : source.entrySet()) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    public static final class EventType {
        public static final String PROJECT_STARTED        = "PROJECT_STARTED";
        public static final String ORCHESTRATION_RUN      = "ORCHESTRATION_RUN";
        public static final String APPROVED               = "APPROVED";
        public static final String EVENT_SENT             = "EVENT_SENT";
        public static final String MODULE_RESULT_OK       = "MODULE_RESULT_OK";
        public static final String MODULE_RESULT_FAIL     = "MODULE_RESULT_FAIL";
        public static final String MODULE_FAILED          = "MODULE_FAILED";
        public static final String RECOMMENDATION_ACCEPT  = "RECOMMENDATION_ACCEPT";
        public static final String PROCESS_ADJUSTED       = "PROCESS_ADJUSTED";
        public static final String OUTSOURCING_SELECTED   = "OUTSOURCING_SELECTED";
        public static final String ROLLBACK               = "ROLLBACK";

        private EventType() {
        }
    }
}