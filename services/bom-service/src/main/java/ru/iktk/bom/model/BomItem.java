package ru.iktk.bom.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Доменная модель позиции состава изделия (BOM) ИКТК.
 *
 * <p>Описывает одну позицию в дереве состава изделия:
 * деталь, подсборку, стандартное изделие, крепёж или материал.
 *
 * <p><b>Иерархия.</b> Позиции образуют дерево через {@code parentId}:
 * <ul>
 *   <li>корневые позиции — {@code parentId = null};</li>
 *   <li>дочерние позиции — {@code parentId} указывает на родителя.</li>
 * </ul>
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code assemblyId} — сборка, к которой относится позиция;</li>
 *   <li>{@code parentId} — родительская позиция (для иерархии);</li>
 *   <li>{@code order} — порядок в дереве;</li>
 *   <li>{@code level} — уровень вложенности (0 — корень);</li>
 *   <li>{@code position} — номер позиции по ГОСТ (например, «1», «2»);</li>
 *   <li>{@code designation} — обозначение (например, «КР-001.01»);</li>
 *   <li>{@code name} — наименование;</li>
 *   <li>{@code quantity} — количество;</li>
 *   <li>{@code unit} — единица измерения;</li>
 *   <li>{@code material} — материал;</li>
 *   <li>{@code type} — тип позиции (PART, ASSEMBLY, STANDARD, MATERIAL, FASTENER);</li>
 *   <li>{@code geometryId} — ссылка на CAD-геометрию детали;</li>
 *   <li>{@code massKg} — масса позиции;</li>
 *   <li>{@code parameters} — произвольные параметры (JSONB).</li>
 * </ul>
 *
 * <p><b>Использование:</b>
 * <ul>
 *   <li>построение дерева BOM в {@code BomService.buildTree};</li>
 *   <li>спецификация по ГОСТ 2.106;</li>
 *   <li>экспорт BOM (CSV, Excel, PDF);</li>
 *   <li>проектирование ТП сборки (assembly-process-service).</li>
 * </ul>
 *
 * <p>Таблица: {@code bom_items} (PostgreSQL, миграция V5).
 */
@Entity
@Table(
        name = "bom_items",
        indexes = {
                @Index(name = "idx_bom_items_assembly",   columnList = "assembly_id"),
                @Index(name = "idx_bom_items_parent",     columnList = "parent_id"),
                @Index(name = "idx_bom_items_order",      columnList = "assembly_id, item_order"),
                @Index(name = "idx_bom_items_type",       columnList = "item_type"),
                @Index(name = "idx_bom_items_designation",columnList = "designation"),
                @Index(name = "idx_bom_items_material",   columnList = "material"),
                @Index(name = "idx_bom_items_level",      columnList = "assembly_id, level")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BomItem {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Идентификатор сборки, к которой относится позиция. */
    @Column(name = "assembly_id", nullable = false, length = 64)
    private String assemblyId;

    /** Идентификатор родительской позиции (для иерархии). */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    /**
     * Ссылка на сборку (для навигации).
     * Загружается лениво, не сериализуется.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assembly_id", insertable = false, updatable = false)
    private AssemblyUnit assembly;

    /**
     * Ссылка на родительскую позицию (для навигации).
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private BomItem parent;

    // ------------------------------------------------------------------
    // Позиционирование в дереве
    // ------------------------------------------------------------------

    /** Порядок позиции в дереве (для сортировки). */
    @Column(name = "item_order", nullable = false)
    private int order;

    /** Уровень вложенности (0 — корень). */
    @Column(name = "level", nullable = false)
    private int level = 0;

    /** Номер позиции по ГОСТ (например, «1», «2», «1.1»). */
    @Size(max = 32)
    @Column(name = "position", length = 32)
    private String position;

    // ------------------------------------------------------------------
    // Описание позиции
    // ------------------------------------------------------------------

    /** Обозначение (например, «КР-001.01»). */
    @Size(max = 128)
    @Column(name = "designation", length = 128)
    private String designation;

    /** Наименование. */
    @Size(max = 255)
    @Column(name = "name", length = 255)
    private String name;

    /** Количество. */
    @Min(1)
    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    /** Единица измерения (шт, кг, м, ...). */
    @Size(max = 32)
    @Column(name = "unit", length = 32)
    private String unit = "шт";

    /** Материал (код из справочника). */
    @Size(max = 128)
    @Column(name = "material", length = 128)
    private String material;

    /** Тип позиции. */
    @Column(name = "item_type", nullable = false, length = 32)
    private String type;

    /** Ссылка на CAD-геометрию детали. */
    @Size(max = 64)
    @Column(name = "geometry_id", length = 64)
    private String geometryId;

    /** CAD-система-источник. */
    @Size(max = 32)
    @Column(name = "cad_system", length = 32)
    private String cadSystem;

    /** Масса позиции (кг). */
    @Column(name = "mass_kg", precision = 12, scale = 4)
    private BigDecimal massKg;

    /** Примечание. */
    @Size(max = 1000)
    @Column(name = "note", length = 1000)
    private String note;

    // ------------------------------------------------------------------
    // Дополнительные атрибуты
    // ------------------------------------------------------------------

    /** Произвольные параметры (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

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

    protected BomItem() {
    }

    public BomItem(String id, String assemblyId, String designation, String name) {
        this.id = id;
        this.assemblyId = assemblyId;
        this.designation = designation;
        this.name = name;
        this.type = BomItemType.PART.name();
        this.quantity = 1;
        this.unit = "шт";
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
        private String assemblyId;
        private String parentId;
        private int order;
        private int level;
        private String position;
        private String designation;
        private String name;
        private int quantity = 1;
        private String unit = "шт";
        private String material;
        private String type = "PART";
        private String geometryId;
        private String cadSystem;
        private BigDecimal massKg;
        private String note;
        private Map<String, Object> parameters;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder assemblyId(String v) { this.assemblyId = v; return this; }
        public Builder parentId(String v) { this.parentId = v; return this; }
        public Builder order(int v) { this.order = v; return this; }
        public Builder level(int v) { this.level = v; return this; }
        public Builder position(String v) { this.position = v; return this; }
        public Builder designation(String v) { this.designation = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder quantity(int v) { this.quantity = v; return this; }
        public Builder unit(String v) { this.unit = v; return this; }
        public Builder material(String v) { this.material = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder geometryId(String v) { this.geometryId = v; return this; }
        public Builder cadSystem(String v) { this.cadSystem = v; return this; }
        public Builder massKg(BigDecimal v) { this.massKg = v; return this; }
        public Builder note(String v) { this.note = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public BomItem build() {
            BomItem item = new BomItem();
            item.id = this.id;
            item.assemblyId = this.assemblyId;
            item.parentId = this.parentId;
            item.order = this.order;
            item.level = this.level;
            item.position = this.position;
            item.designation = this.designation;
            item.name = this.name;
            item.quantity = this.quantity;
            item.unit = this.unit;
            item.material = this.material;
            item.type = this.type;
            item.geometryId = this.geometryId;
            item.cadSystem = this.cadSystem;
            item.massKg = this.massKg;
            item.note = this.note;
            item.parameters = this.parameters;
            Instant now = Instant.now();
            item.createdAt = this.createdAt == null ? now : this.createdAt;
            item.updatedAt = this.updatedAt == null ? now : this.updatedAt;
            return item;
        }
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Является ли позиция деталью.
     */
    public boolean isPart() {
        return BomItemType.PART.name().equals(this.type);
    }

    /**
     * Является ли позиция сборкой.
     */
    public boolean isAssembly() {
        return BomItemType.ASSEMBLY.name().equals(this.type);
    }

    /**
     * Является ли позиция стандартным изделием.
     */
    public boolean isStandard() {
        return BomItemType.STANDARD.name().equals(this.type);
    }

    /**
     * Является ли позиция материалом.
     */
    public boolean isMaterial() {
        return BomItemType.MATERIAL.name().equals(this.type);
    }

    /**
     * Является ли позиция крепежом.
     */
    public boolean isFastener() {
        return BomItemType.FASTENER.name().equals(this.type);
    }

    /**
     * Есть ли родитель.
     */
    public boolean hasParent() {
        return parentId != null && !parentId.isBlank();
    }

    /**
     * Является ли позиция корневой.
     */
    public boolean isRoot() {
        return !hasParent();
    }

    /**
     * Установить тип позиции.
     */
    public void setTypeFromEnum(BomItemType itemType) {
        this.type = itemType.name();
        touch();
    }

    /**
     * Получить тип как enum.
     */
    public BomItemType getTypeEnum() {
        try {
            return BomItemType.valueOf(this.type);
        } catch (Exception e) {
            return BomItemType.PART;
        }
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

    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; touch(); }

    public AssemblyUnit getAssembly() { return assembly; }
    public void setAssembly(AssemblyUnit assembly) { this.assembly = assembly; }

    public BomItem getParent() { return parent; }
    public void setParent(BomItem parent) { this.parent = parent; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; touch(); }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; touch(); }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; touch(); }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; touch(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; touch(); }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; touch(); }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; touch(); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; touch(); }

    public String getGeometryId() { return geometryId; }
    public void setGeometryId(String geometryId) { this.geometryId = geometryId; touch(); }

    public String getCadSystem() { return cadSystem; }
    public void setCadSystem(String cadSystem) { this.cadSystem = cadSystem; touch(); }

    public BigDecimal getMassKg() { return massKg; }
    public void setMassKg(BigDecimal massKg) { this.massKg = massKg; touch(); }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; touch(); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }

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
        if (!(o instanceof BomItem other)) {
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
        return "BomItem{" +
                "id='" + id + '\'' +
                ", assemblyId='" + assemblyId + '\'' +
                ", position='" + position + '\'' +
                ", designation='" + designation + '\'' +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                ", type='" + type + '\'' +
                ", level=" + level +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенный enum: тип позиции
    // ------------------------------------------------------------------

    /**
     * Тип позиции BOM.
     */
    public enum BomItemType {
        /** Деталь. */
        PART,
        /** Сборочная единица (подсборка). */
        ASSEMBLY,
        /** Стандартное изделие (по ГОСТ). */
        STANDARD,
        /** Материал. */
        MATERIAL,
        /** Крепёж (болт, винт, гайка, шайба). */
        FASTENER
    }
}