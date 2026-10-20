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
 * Компонент (деталь/подсборка) в составе сборочного процесса.
 */
@Entity
@Table(
        name = "assembly_component",
        indexes = {
                @Index(name = "idx_assembly_component_process", columnList = "assembly_process_id"),
                @Index(name = "idx_assembly_component_parent",  columnList = "parent_component_id")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyComponent {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @NotBlank @Size(max = 64)
    @Column(name = "assembly_process_id", nullable = false, length = 64)
    private String assemblyProcessId;

    @Size(max = 64)
    @Column(name = "parent_component_id", length = 64)
    private String parentComponentId;

    @Size(max = 128)
    @Column(name = "designation", length = 128)
    private String designation;

    @Size(max = 255)
    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "position")
    private Integer position;

    @Size(max = 32)
    @Column(name = "part_kind", length = 32)
    private String partKind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssemblyComponent() {
    }

    public AssemblyComponent(String id, String assemblyProcessId) {
        this.id = id;
        this.assemblyProcessId = assemblyProcessId;
        this.quantity = 1;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAssemblyProcessId() { return assemblyProcessId; }
    public void setAssemblyProcessId(String assemblyProcessId) { this.assemblyProcessId = assemblyProcessId; }
    public String getParentComponentId() { return parentComponentId; }
    public void setParentComponentId(String parentComponentId) { this.parentComponentId = parentComponentId; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public String getPartKind() { return partKind; }
    public void setPartKind(String partKind) { this.partKind = partKind; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssemblyComponent other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "AssemblyComponent{" +
                "id='" + id + '\'' +
                ", assemblyProcessId='" + assemblyProcessId + '\'' +
                ", designation='" + designation + '\'' +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                '}';
    }
}