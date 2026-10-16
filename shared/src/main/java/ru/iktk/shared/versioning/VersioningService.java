package ru.iktk.shared.versioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Универсальный сервис версионности и отката ИКТК.
 */
@Service
public class VersioningService {

    private static final Logger log = LoggerFactory.getLogger(VersioningService.class);
    private static final int MAX_HISTORY_LIMIT = 1000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public VersioningService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public EntityVersionRecord createVersion(String entityType,
                                             UUID entityId,
                                             UUID projectId,
                                             JsonNode snapshot,
                                             ChangeType changeType,
                                             String reason,
                                             String createdBy) {
        validate(entityType, entityId);
        if (snapshot == null) {
            throw new VersioningException("Снапшот не задан");
        }

        EntityVersionRecord current = findCurrent(entityType, entityId);
        int nextNumber = current == null ? 1 : current.versionNumber() + 1;
        String versionLabel = "v" + nextNumber;
        JsonNode diff = current == null ? null : computeDiff(current.snapshot(), snapshot);

        if (current != null) {
            jdbc.update(
                    "UPDATE iktk.entity_versions SET is_current = FALSE "
                            + "WHERE entity_type = ? AND entity_id = ? AND is_current = TRUE",
                    entityType, entityId);
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO iktk.entity_versions "
                        + "(id, entity_type, entity_id, project_id, version, version_number, "
                        + " state, snapshot, diff, change_type, change_reason, created_by, "
                        + " created_at, is_current) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, TRUE)",
                id, entityType, entityId, projectId, versionLabel, nextNumber,
                extractState(snapshot), toJson(snapshot),
                diff == null ? null : toJson(diff),
                changeType.name(), reason, createdBy,
                Timestamp.from(Instant.now()));

        log.info("VersioningService.createVersion: entityType={}, entityId={}, version={}",
                entityType, entityId, versionLabel);

        return new EntityVersionRecord(
                id, entityType, entityId, projectId, versionLabel, nextNumber,
                extractState(snapshot), snapshot, diff, changeType, reason, createdBy,
                Instant.now(), true);
    }

    @Transactional(readOnly = true)
    public EntityVersionRecord getCurrentVersion(String entityType, UUID entityId) {
        validate(entityType, entityId);
        return findCurrent(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<EntityVersionRecord> getHistory(String entityType, UUID entityId, int limit) {
        validate(entityType, entityId);
        int effective = normalizeLimit(limit);
        return jdbc.query(
                "SELECT * FROM iktk.entity_versions "
                        + "WHERE entity_type = ? AND entity_id = ? "
                        + "ORDER BY version_number DESC LIMIT ?",
                entityVersionMapper(), entityType, entityId, effective);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public EntityVersionRecord rollbackToVersion(String entityType,
                                                 UUID entityId,
                                                 String toVersion,
                                                 String reason,
                                                 String performedBy) {
        validate(entityType, entityId);
        if (toVersion == null || toVersion.isBlank()) {
            throw new VersioningException("Не задана целевая версия");
        }
        EntityVersionRecord target = findVersion(entityType, entityId, toVersion);
        if (target == null) {
            throw new VersioningException("Версия не найдена: " + toVersion);
        }
        return createVersion(entityType, entityId, target.projectId(), target.snapshot(),
                ChangeType.ROLLBACK,
                "Откат к " + toVersion + (reason == null ? "" : ": " + reason),
                performedBy);
    }

    public JsonNode computeDiff(JsonNode oldSnapshot, JsonNode newSnapshot) {
        if (oldSnapshot == null && newSnapshot == null) return null;
        if (oldSnapshot == null) return objectMapper.createObjectNode().set("added", newSnapshot);
        if (newSnapshot == null) return objectMapper.createObjectNode().set("removed", oldSnapshot);

        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode added = objectMapper.createObjectNode();
        ObjectNode removed = objectMapper.createObjectNode();
        ObjectNode changed = objectMapper.createObjectNode();

        Iterator<Map.Entry<String, JsonNode>> newFields = newSnapshot.fields();
        while (newFields.hasNext()) {
            Map.Entry<String, JsonNode> entry = newFields.next();
            JsonNode oldValue = oldSnapshot.get(entry.getKey());
            if (oldValue == null) {
                added.set(entry.getKey(), entry.getValue());
            } else if (!oldValue.equals(entry.getValue())) {
                ObjectNode change = objectMapper.createObjectNode();
                change.set("from", oldValue);
                change.set("to", entry.getValue());
                changed.set(entry.getKey(), change);
            }
        }

        Iterator<String> oldFields = oldSnapshot.fieldNames();
        while (oldFields.hasNext()) {
            String field = oldFields.next();
            if (!newSnapshot.has(field)) {
                removed.set(field, oldSnapshot.get(field));
            }
        }

        if (!added.isEmpty()) result.set("added", added);
        if (!removed.isEmpty()) result.set("removed", removed);
        if (!changed.isEmpty()) result.set("changed", changed);
        return result.isEmpty() ? null : result;
    }

    private EntityVersionRecord findCurrent(String entityType, UUID entityId) {
        List<EntityVersionRecord> list = jdbc.query(
                "SELECT * FROM iktk.entity_versions "
                        + "WHERE entity_type = ? AND entity_id = ? AND is_current = TRUE LIMIT 1",
                entityVersionMapper(), entityType, entityId);
        return list.isEmpty() ? null : list.get(0);
    }

    private EntityVersionRecord findVersion(String entityType, UUID entityId, String version) {
        List<EntityVersionRecord> list = jdbc.query(
                "SELECT * FROM iktk.entity_versions "
                        + "WHERE entity_type = ? AND entity_id = ? AND version = ?",
                entityVersionMapper(), entityType, entityId, version);
        return list.isEmpty() ? null : list.get(0);
    }

    private RowMapper<EntityVersionRecord> entityVersionMapper() {
        return (rs, rowNum) -> new EntityVersionRecord(
                rs.getObject("id", UUID.class),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("version"),
                rs.getInt("version_number"),
                rs.getString("state"),
                readJson(rs.getString("snapshot")),
                readJson(rs.getString("diff")),
                parseChangeType(rs.getString("change_type")),
                rs.getString("change_reason"),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getBoolean("is_current"));
    }

    private void validate(String entityType, UUID entityId) {
        if (entityType == null || entityType.isBlank()) throw new VersioningException("Не задан entityType");
        if (entityId == null) throw new VersioningException("Не задан entityId");
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) return 100;
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new VersioningException("Ошибка сериализации JSON", e);
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("VersioningService.readJson: ошибка разбора JSON", e);
            return null;
        }
    }

    private String extractState(JsonNode snapshot) {
        if (snapshot == null) return null;
        if (snapshot.has("state")) return snapshot.get("state").asText();
        if (snapshot.has("status")) return snapshot.get("status").asText();
        return null;
    }

    private ChangeType parseChangeType(String value) {
        if (value == null) return null;
        try { return ChangeType.valueOf(value); } catch (IllegalArgumentException e) { return ChangeType.UPDATE; }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public enum ChangeType {
        CREATE, UPDATE, DELETE, ROLLBACK, APPROVE, REJECT, ARCHIVE, RESTORE
    }

    public record EntityVersionRecord(
            UUID id, String entityType, UUID entityId, UUID projectId,
            String version, int versionNumber, String state,
            JsonNode snapshot, JsonNode diff, ChangeType changeType,
            String changeReason, String createdBy, Instant createdAt, boolean isCurrent) {
    }

    public static class VersioningException extends RuntimeException {
        public VersioningException(String message) { super(message); }
        public VersioningException(String message, Throwable cause) { super(message, cause); }
    }
}