package ru.iktk.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(
        name = "decision_log",
        indexes = {
                @Index(name = "idx_dl_project", columnList = "project_id"),
                @Index(name = "idx_dl_created", columnList = "created_at")
        }
)
public class DecisionLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 64, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant timestamp;

    public DecisionLogEntry() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final DecisionLogEntry e = new DecisionLogEntry();
        public Builder id(String v) { e.id = v; return this; }
        public Builder projectId(String v) { e.projectId = v; return this; }
        public Builder eventType(String v) { e.eventType = v; return this; }
        public Builder actor(String v) { e.actor = v; return this; }
        public Builder payload(Map<String, Object> v) { e.payload = v; return this; }
        public Builder timestamp(Instant v) { e.timestamp = v; return this; }
        public DecisionLogEntry build() { return e; }
    }

    @PrePersist
    void onCreate() {
        if (timestamp == null) timestamp = Instant.now();
    }
}
