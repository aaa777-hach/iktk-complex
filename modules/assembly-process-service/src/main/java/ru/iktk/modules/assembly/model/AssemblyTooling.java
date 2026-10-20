package ru.iktk.modules.assembly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Совместимость сборочного процесса с оснасткой.
 */
@Entity
@Table(
        name = "assembly_tooling",
        indexes = {
                @Index(name = "idx_assembly_tooling_process", columnList = "assembly_process_id"),
                @Index(name = "idx_assembly_tooling_tooling", columnList = "tooling_id")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyTooling {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @NotBlank @Size(max = 64)
    @Column(name = "assembly_process_id", nullable = false, length = 64)
    private String assemblyProcessId;

    @NotBlank @Size(max = 64)
    @Column(name = "tooling_id", nullable = false, length = 64)
    private String toolingId;

    @Size(max = 32)
    @Column(name = "compatibility", length = 32)
    private String compatibility;

    @Size(max = 512)
    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssemblyTooling() {
    }

    public AssemblyTooling(String id, String assemblyProcessId, String toolingId) {
        this.id = id;
        this.assemblyProcessId = assemblyProcessId;
        this.toolingId = toolingId;
        this.compatibility = "FULL";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAssemblyProcessId() { return assemblyProcessId; }
    public void setAssemblyProcessId(String assemblyProcessId) { this.assemblyProcessId = assemblyProcessId; }
    public String getToolingId() { return toolingId; }
    public void setToolingId(String toolingId) { this.toolingId = toolingId; }
    public String getCompatibility() { return compatibility; }
    public void setCompatibility(String compatibility) { this.compatibility = compatibility; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssemblyTooling other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "AssemblyTooling{" +
                "id='" + id + '\'' +
                ", assemblyProcessId='" + assemblyProcessId + '\'' +
                ", toolingId='" + toolingId + '\'' +
                ", compatibility='" + compatibility + '\'' +
                '}';
    }
}