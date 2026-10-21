package ru.iktk.bom.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Доменная модель сборочной единицы ИКТК.
 *
 * <p>Описывает корневую или вложенную сборочную единицу проекта.
 * Сборочная единица — объект, состоящий из деталей, подсборок,
 * стандартных изделий и материалов (см. {@link BomItem}).
 *
 * <p><b>Иерархия.</b> Сборочные единицы могут быть вложенными:
 * <ul>
 *   <li>корневая сборка — {@code parentId = null};</li>
 *   <li>подсборка — {@code parentId} указывает на родительскую сборку.</li>
 * </ul>
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code projectId} — проект, к которому относится сборка;</li>
 *   <li>{@code designation} — обозначение (например, «КР-001 СБ»);</li>
 *   <li>{@code name} — наименование;</li>
 *   <li>{@code geometryId} — идентификатор CAD-геометрии;</li>
 *   <li>{@code itemCount} — число позиций BOM;</li>
 *   <li>{@code maxDepth} — максимальная глубина дерева;</li>
 *   <li>{@code version} — версия сборки (инкрементируется при перестроении);</li>
 *   <li>{@code status} — DRAFT, ACTIVE, APPROVED, ARCHIVED.</li>
 * </ul>
 *
 * <p><b>Связи:</b>
 * <ul>
 *   <li>{@code parent} — родительская сборка (ManyToOne);</li>
 *   <li>{@code children} — дочерние подсборки (OneToMany);</li>
 *   <li>{@code items} — позиции BOM (OneToMany на {@link BomItem});</li>
 *   <li>{@code connections} — связи и сопряжения
 *       (OneToMany на {@link AssemblyConnection}).</li>
 * </ul>
 *
 * <p>Таблица: {@code assembly_units} (PostgreSQL, миграция V5).
 */
@Entity
@Table(
        name = "assembly_units",
        indexes = {
                @Index(name = "idx_assembly_project",    columnList = "project_id"),
                @Index(name = "idx_assembly_parent",     columnList = "parent_id"),
                @Index(name = "idx_assembly_designation",columnList = "designation"),
                @Index(name = "idx_assembly_status",     columnList = "status"),
                @Index(name = "idx_assembly_geometry",   columnList = "geometry_id")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyUnit {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Идентификатор проекта. */
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    /** Идентификатор родительской сборки (null — корневая). */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    /** Обозначение сборочной единицы (например, «КР-001 СБ»). */
    @NotBlank
    @Size(max = 128)
    @Column(name = "designation", nullable = false, length = 128)
    private String designation;

    /** Наименование сборочной единицы. */
    @Size(max = 255)
    @Column(name = "name", length = 255)
    private String name;

    /** Описание. */
    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    /** Идентификатор CAD-геометрии. */
    @Size(max = 64)
    @Column(name = "geometry_id", length = 64)
    private String geometryId;

    /** CAD-система-источник. */
    @Size(max = 32)
    @Column(name = "cad_system", length = 32)
    private String cadSystem;

    // ------------------------------------------------------------------
    // Метрики
    // ------------------------------------------------------------------

    /** Количество позиций BOM. */
    @Column(name = "item_count", nullable = false)
    private int itemCount = 0;

    /** Максимальная глубина дерева. */
    @Column(name = "max_depth", nullable = false)
    private int maxDepth = 0;

    /** Суммарная масса (кг). */
    @Column(name = "total_mass_kg", precision = 14, scale = 4)
    private java.math.BigDecimal totalMassKg;

    /** Версия (инкрементируется при перестроении). */
    @Column(name = "version", nullable = false)
    private int version = 1;

    // ------------------------------------------------------------------
    // Состояние
    // ------------------------------------------------------------------

    /** Статус: DRAFT, ACTIVE, APPROVED, ARCHIVED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    /** Автор последнего изменения. */
    @Size(max = 128)
    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    // ------------------------------------------------------------------
    // Дополнительные атрибуты
    // ------------------------------------------------------------------

    /** Произвольные параметры (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    // ------------------------------------------------------------------
    // Связи
    // ------------------------------------------------------------------

    /** Родительская сборка (для подсборок). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private AssemblyUnit parent;

    /** Дочерние подсборки. */
    @OneToMany(
            mappedBy = "parentId",
            fetch = FetchType.LAZY
    )
    private List<AssemblyUnit> children = new ArrayList<>();

    /** Позиции BOM. */
    @OneToMany(
            mappedBy = "assemblyId",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<BomItem> items = new ArrayList<>();

    /** Связи и сопряжения. */
    @OneToMany(
            mappedBy = "assemblyId",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<AssemblyConnection> connections = new ArrayList<>();

    // ------------------------------------------------------------------
    // Служебные поля
    // ------------------------------------------------------------------

    /** Момент создания. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Момент последнего изменения. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    public AssemblyUnit() {
    }

    public AssemblyUnit(String id, String projectId, String designation) {
        this.id = id;
        this.projectId = projectId;
        this.designation = designation;
        this.status = AssemblyStatus.DRAFT.name();
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String projectId;
        private String parentId;
        private String designation;
        private String name;
        private String description;
        private String geometryId;
        private String cadSystem;
        private int itemCount;
        private int maxDepth;
        private java.math.BigDecimal totalMassKg;
        private int version = 1;
        private String status = "DRAFT";
        private String updatedBy;
        private Map<String, Object> parameters;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder parentId(String v) { this.parentId = v; return this; }
        public Builder designation(String v) { this.designation = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder geometryId(String v) { this.geometryId = v; return this; }
        public Builder cadSystem(String v) { this.cadSystem = v; return this; }
        public Builder itemCount(int v) { this.itemCount = v; return this; }
        public Builder maxDepth(int v) { this.maxDepth = v; return this; }
        public Builder totalMassKg(java.math.BigDecimal v) { this.totalMassKg = v; return this; }
        public Builder version(int v) { this.version = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder updatedBy(String v) { this.updatedBy = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public AssemblyUnit build() {
            AssemblyUnit u = new AssemblyUnit();
            u.id = this.id;
            u.projectId = this.projectId;
            u.parentId = this.parentId;
            u.designation = this.designation;
            u.name = this.name;
            u.description = this.description;
            u.geometryId = this.geometryId;
            u.cadSystem = this.cadSystem;
            u.itemCount = this.itemCount;
            u.maxDepth = this.maxDepth;
            u.totalMassKg = this.totalMassKg;
            u.version = this.version;
            u.status = this.status;
            u.updatedBy = this.updatedBy;
            u.parameters = this.parameters;
            Instant now = Instant.now();
            u.createdAt = this.createdAt == null ? now : this.createdAt;
            u.updatedAt = this.updatedAt == null ? now : this.updatedAt;
            return u;
        }
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Является ли сборка корневой.
     */
    public boolean isRoot() {
        return parentId == null || parentId.isBlank();
    }

    /**
     * Является ли сборка подсборкой.
     */
    public boolean isSubAssembly() {
        return !isRoot();
    }

    /**
     * Активировать сборку.
     */
    public void activate() {
        this.status = AssemblyStatus.ACTIVE.name();
        touch();
    }

    /**
     * Утвердить сборку.
     */
    public void approve() {
        this.status = AssemblyStatus.APPROVED.name();
        touch();
    }

    /**
     * Архивировать сборку.
     */
    public void archive() {
        this.status = AssemblyStatus.ARCHIVED.name();
        touch();
    }

    /**
     * Инкрементировать версию.
     */
    public void incrementVersion() {
        this.version++;
        touch();
    }

    /**
     * Обновить метрики.
     */
    public void updateMetrics(int itemCount, int maxDepth) {
        this.itemCount = itemCount;
        this.maxDepth = maxDepth;
        touch();
    }

    /**
     * Добавить позицию BOM.
     */
    public void addItem(BomItem item) {
        if (item == null) {
            return;
        }
        item.setAssemblyId(this.id);
        this.items.add(item);
        this.itemCount = this.items.size();
        touch();
    }

    /**
     * Добавить связь.
     */
    public void addConnection(AssemblyConnection connection) {
        if (connection == null) {
            return;
        }
        connection.setAssemblyId(this.id);
        this.connections.add(connection);
        touch();
    }

    /** Обновить метку времени. */
    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Геттеры и сеттеры
    // ------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; touch(); }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; touch(); }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; touch(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }

    public String getGeometryId() { return geometryId; }
    public void setGeometryId(String geometryId) { this.geometryId = geometryId; touch(); }

    public String getCadSystem() { return cadSystem; }
    public void setCadSystem(String cadSystem) { this.cadSystem = cadSystem; touch(); }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; touch(); }

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; touch(); }

    public java.math.BigDecimal getTotalMassKg() { return totalMassKg; }
    public void setTotalMassKg(java.math.BigDecimal totalMassKg) { this.totalMassKg = totalMassKg; touch(); }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; touch(); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; touch(); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }

    public AssemblyUnit getParent() { return parent; }
    public void setParent(AssemblyUnit parent) { this.parent = parent; }

    public List<AssemblyUnit> getChildren() { return children; }
    public void setChildren(List<AssemblyUnit> children) { this.children = children; }

    public List<BomItem> getItems() { return items; }
    public void setItems(List<BomItem> items) { this.items = items; }

    public List<AssemblyConnection> getConnections() { return connections; }
    public void setConnections(List<AssemblyConnection> connections) { this.connections = connections; }

    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AssemblyUnit other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AssemblyUnit{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", designation='" + designation + '\'' +
                ", name='" + name + '\'' +
                ", itemCount=" + itemCount +
                ", maxDepth=" + maxDepth +
                ", version=" + version +
                ", status='" + status + '\'' +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенный enum: статус сборки
    // ------------------------------------------------------------------

    /**
     * Статус сборочной единицы.
     */
    public enum AssemblyStatus {
        /** Черновик — извлечён из CAD, но не активирован. */
        DRAFT,
        /** Активный — используется в проектировании. */
        ACTIVE,
        /** Утверждён — согласован технологом. */
        APPROVED,
        /** Архивный — устаревшая версия. */
        ARCHIVED
    }
}