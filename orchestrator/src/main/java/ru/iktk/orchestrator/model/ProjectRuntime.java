package ru.iktk.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.time.Instant;
import java.util.Map;
import jakarta.persistence.Transient;

@Entity
@Table(
        name = "project_state",
        indexes = {
                @Index(name = "idx_ps_project", columnList = "project_id", unique = true),
                @Index(name = "idx_ps_state", columnList = "state")
        }
)
public class ProjectRuntime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 64, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 64)
    private ProjectState state;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "payload", columnDefinition = "jsonb")
    @Transient
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectRuntime() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public ProjectState getState() { return state; }
    public void setState(ProjectState state) { this.state = state; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ProjectRuntime r = new ProjectRuntime();
        public Builder id(String v) { r.id = v; return this; }
        public Builder projectId(String v) { r.projectId = v; return this; }
        public Builder projectName(String v) { r.projectName = v; return this; }
        public Builder requestedBy(String v) { r.requestedBy = v; return this; }
        public Builder state(ProjectState v) { r.state = v; return this; }
        public Builder version(String v) { r.version = v; return this; }
        public Builder payload(Map<String, Object> v) { r.payload = v; return this; }
        public Builder createdAt(Instant v) { r.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { r.updatedAt = v; return this; }
        public ProjectRuntime build() { return r; }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
