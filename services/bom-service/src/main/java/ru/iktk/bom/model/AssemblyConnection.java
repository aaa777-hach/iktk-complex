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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Доменная модель связи/сопряжения сборочной единицы ИКТК.
 *
 * <p>Описывает связь между двумя деталями (или деталью и подсборкой)
 * в сборочной единице. Используется для:
 * <ul>
 *   <li>построения последовательности сборки
 *       ({@code BomService.getAssemblySequence});</li>
 *   <li>проектирования ТП сборки в {@code assembly-process-service};</li>
 *   <li>анализа технологичности сборки (доступность инструмента,
 *       порядок установки, базирование);</li>
 *   <li>формирования карты сборки для документации.</li>
 * </ul>
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code assemblyId} — сборочная единица, к которой относится связь;</li>
 *   <li>{@code type} — тип связи (FIXED, ROTATIONAL, SLIDING, WELDED,
 *       THREADED, GLUED, PRESSED, ...);</li>
 *   <li>{@code part1Id} — первая деталь (базовая/основание);</li>
 *   <li>{@code part2Id} — вторая деталь (устанавливаемая);</li>
 *   <li>{@code parameters} — параметры сопряжения (зазор, натяг,
 *       момент затяжки, диаметр, шаг резьбы и т.п.);</li>
 *   <li>{@code order} — рекомендуемый порядок установки;</li>
 *   <li>{@code needsTool} — требуется ли инструмент;</li>
 *   <li>{@code needsFixture} — требуется ли приспособление.</li>
 * </ul>
 *
 * <p>Таблица: {@code assembly_connections} (PostgreSQL, миграция V5).
 */
@Entity
@Table(
        name = "assembly_connections",
        indexes = {
                @Index(name = "idx_conn_assembly", columnList = "assembly_id"),
                @Index(name = "idx_conn_part1",    columnList = "part1_id"),
                @Index(name = "idx_conn_part2",    columnList = "part2_id"),
                @Index(name = "idx_conn_type",     columnList = "connection_type"),
                @Index(name = "idx_conn_order",    columnList = "assembly_id, connection_order")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyConnection {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Идентификатор сборочной единицы. */
    @Column(name = "assembly_id", nullable = false, length = 64)
    private String assemblyId;

    /**
     * Ссылка на сборочную единицу (для навигации).
     * Загружается лениво, не сериализуется.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assembly_id", insertable = false, updatable = false)
    private AssemblyUnit assembly;

    // ------------------------------------------------------------------
    // Тип связи
    // ------------------------------------------------------------------

    /** Тип связи (FIXED, ROTATIONAL, WELDED, THREADED, ...). */
    @NotBlank
    @Size(max = 32)
    @Column(name = "connection_type", nullable = false, length = 32)
    private String type;

    /** Наименование связи (человекочитаемое). */
    @Size(max = 255)
    @Column(name = "connection_name", length = 255)
    private String name;

    /** Описание. */
    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    // ------------------------------------------------------------------
    // Связываемые детали
    // ------------------------------------------------------------------

    /** Идентификатор первой детали (базовой/основания). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "part1_id", nullable = false, length = 64)
    private String part1Id;

    /** Идентификатор второй детали (устанавливаемой). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "part2_id", nullable = false, length = 64)
    private String part2Id;

    /** Обозначение первой детали (для удобства). */
    @Size(max = 128)
    @Column(name = "part1_designation", length = 128)
    private String part1Designation;

    /** Обозначение второй детали (для удобства). */
    @Size(max = 128)
    @Column(name = "part2_designation", length = 128)
    private String part2Designation;

    // ------------------------------------------------------------------
    // Порядок и технологичность
    // ------------------------------------------------------------------

    /** Рекомендуемый порядок установки (0 — раньше). */
    @Column(name = "connection_order", nullable = false)
    private int order = 0;

    /** Требуется ли инструмент. */
    @Column(name = "needs_tool", nullable = false)
    private boolean needsTool = false;

    /** Требуется ли приспособление. */
    @Column(name = "needs_fixture", nullable = false)
    private boolean needsFixture = false;

    /** Требуется ли контроль после установки. */
    @Column(name = "needs_control", nullable = false)
    private boolean needsControl = false;

    /** Тип инструмента (если применимо). */
    @Size(max = 64)
    @Column(name = "tool_type", length = 64)
    private String toolType;

    /** Тип приспособления (если применимо). */
    @Size(max = 64)
    @Column(name = "fixture_type", length = 64)
    private String fixtureType;

    // ------------------------------------------------------------------
    // Параметры сопряжения
    // ------------------------------------------------------------------

    /** Диаметр (мм) — для резьбовых, штифтовых, прессовых. */
    @Column(name = "diameter_mm", precision = 12, scale = 3)
    private BigDecimal diameterMm;

    /** Зазор (мм). */
    @Column(name = "clearance_mm", precision = 12, scale = 4)
    private BigDecimal clearanceMm;

    /** Натяг (мм). */
    @Column(name = "interference_mm", precision = 12, scale = 4)
    private BigDecimal interferenceMm;

    /** Момент затяжки (Н·м) — для резьбовых. */
    @Column(name = "torque_nm", precision = 12, scale = 3)
    private BigDecimal torqueNm;

    /** Усилие запрессовки (кН) — для прессовых. */
    @Column(name = "press_force_kn", precision = 12, scale = 3)
    private BigDecimal pressForceKn;

    /** Длина сварного шва (мм) — для сварных. */
    @Column(name = "weld_length_mm", precision = 12, scale = 3)
    private BigDecimal weldLengthMm;

    /** Катет сварного шва (мм) — для сварных. */
    @Column(name = "weld_leg_mm", precision = 12, scale = 3)
    private BigDecimal weldLegMm;

    // ------------------------------------------------------------------
    // Дополнительные атрибуты
    // ------------------------------------------------------------------

    /** Произвольные параметры сопряжения (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    /** Примечание. */
    @Size(max = 1000)
    @Column(name = "note", length = 1000)
    private String note;

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

    protected AssemblyConnection() {
    }

    public AssemblyConnection(String id, String assemblyId,
                              String part1Id, String part2Id, String type) {
        this.id = id;
        this.assemblyId = assemblyId;
        this.part1Id = part1Id;
        this.part2Id = part2Id;
        this.type = type;
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
        private String type;
        private String name;
        private String description;
        private String part1Id;
        private String part2Id;
        private String part1Designation;
        private String part2Designation;
        private int order;
        private boolean needsTool;
        private boolean needsFixture;
        private boolean needsControl;
        private String toolType;
        private String fixtureType;
        private BigDecimal diameterMm;
        private BigDecimal clearanceMm;
        private BigDecimal interferenceMm;
        private BigDecimal torqueNm;
        private BigDecimal pressForceKn;
        private BigDecimal weldLengthMm;
        private BigDecimal weldLegMm;
        private Map<String, Object> parameters;
        private String note;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder assemblyId(String v) { this.assemblyId = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder part1Id(String v) { this.part1Id = v; return this; }
        public Builder part2Id(String v) { this.part2Id = v; return this; }
        public Builder part1Designation(String v) { this.part1Designation = v; return this; }
        public Builder part2Designation(String v) { this.part2Designation = v; return this; }
        public Builder order(int v) { this.order = v; return this; }
        public Builder needsTool(boolean v) { this.needsTool = v; return this; }
        public Builder needsFixture(boolean v) { this.needsFixture = v; return this; }
        public Builder needsControl(boolean v) { this.needsControl = v; return this; }
        public Builder toolType(String v) { this.toolType = v; return this; }
        public Builder fixtureType(String v) { this.fixtureType = v; return this; }
        public Builder diameterMm(BigDecimal v) { this.diameterMm = v; return this; }
        public Builder clearanceMm(BigDecimal v) { this.clearanceMm = v; return this; }
        public Builder interferenceMm(BigDecimal v) { this.interferenceMm = v; return this; }
        public Builder torqueNm(BigDecimal v) { this.torqueNm = v; return this; }
        public Builder pressForceKn(BigDecimal v) { this.pressForceKn = v; return this; }
        public Builder weldLengthMm(BigDecimal v) { this.weldLengthMm = v; return this; }
        public Builder weldLegMm(BigDecimal v) { this.weldLegMm = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder note(String v) { this.note = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public AssemblyConnection build() {
            AssemblyConnection c = new AssemblyConnection();
            c.id = this.id;
            c.assemblyId = this.assemblyId;
            c.type = this.type;
            c.name = this.name;
            c.description = this.description;
            c.part1Id = this.part1Id;
            c.part2Id = this.part2Id;
            c.part1Designation = this.part1Designation;
            c.part2Designation = this.part2Designation;
            c.order = this.order;
            c.needsTool = this.needsTool;
            c.needsFixture = this.needsFixture;
            c.needsControl = this.needsControl;
            c.toolType = this.toolType;
            c.fixtureType = this.fixtureType;
            c.diameterMm = this.diameterMm;
            c.clearanceMm = this.clearanceMm;
            c.interferenceMm = this.interferenceMm;
            c.torqueNm = this.torqueNm;
            c.pressForceKn = this.pressForceKn;
            c.weldLengthMm = this.weldLengthMm;
            c.weldLegMm = this.weldLegMm;
            c.parameters = this.parameters;
            c.note = this.note;
            Instant now = Instant.now();
            c.createdAt = this.createdAt == null ? now : this.createdAt;
            c.updatedAt = this.updatedAt == null ? now : this.updatedAt;
            return c;
        }
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Является ли связь резьбовой.
     */
    public boolean isThreaded() {
        return ConnectionType.THREADED.name().equals(this.type);
    }

    /**
     * Является ли связь сварной.
     */
    public boolean isWelded() {
        return ConnectionType.WELDED.name().equals(this.type);
    }

    /**
     * Является ли связь прессовой.
     */
    public boolean isPressed() {
        return ConnectionType.PRESSED.name().equals(this.type);
    }

    /**
     * Является ли связь клеевой.
     */
    public boolean isGlued() {
        return ConnectionType.GLUED.name().equals(this.type);
    }

    /**
     * Требуется ли специальный инструмент.
     */
    public boolean requiresSpecialTool() {
        return needsTool && toolType != null && !toolType.isBlank();
    }

    /**
     * Требуется ли специальное приспособление.
     */
    public boolean requiresSpecialFixture() {
        return needsFixture && fixtureType != null && !fixtureType.isBlank();
    }

    /**
     * Установить тип связи.
     */
    public void setTypeFromEnum(ConnectionType connectionType) {
        this.type = connectionType.name();
        touch();
    }

    /**
     * Получить тип как enum.
     */
    public ConnectionType getTypeEnum() {
        try {
            return ConnectionType.valueOf(this.type);
        } catch (Exception e) {
            return ConnectionType.FIXED;
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

    public AssemblyUnit getAssembly() { return assembly; }
    public void setAssembly(AssemblyUnit assembly) { this.assembly = assembly; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; touch(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }

    public String getPart1Id() { return part1Id; }
    public void setPart1Id(String part1Id) { this.part1Id = part1Id; touch(); }

    public String getPart2Id() { return part2Id; }
    public void setPart2Id(String part2Id) { this.part2Id = part2Id; touch(); }

    public String getPart1Designation() { return part1Designation; }
    public void setPart1Designation(String part1Designation) { this.part1Designation = part1Designation; touch(); }

    public String getPart2Designation() { return part2Designation; }
    public void setPart2Designation(String part2Designation) { this.part2Designation = part2Designation; touch(); }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; touch(); }

    public boolean isNeedsTool() { return needsTool; }
    public void setNeedsTool(boolean needsTool) { this.needsTool = needsTool; touch(); }

    public boolean isNeedsFixture() { return needsFixture; }
    public void setNeedsFixture(boolean needsFixture) { this.needsFixture = needsFixture; touch(); }

    public boolean isNeedsControl() { return needsControl; }
    public void setNeedsControl(boolean needsControl) { this.needsControl = needsControl; touch(); }

    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; touch(); }

    public String getFixtureType() { return fixtureType; }
    public void setFixtureType(String fixtureType) { this.fixtureType = fixtureType; touch(); }

    public BigDecimal getDiameterMm() { return diameterMm; }
    public void setDiameterMm(BigDecimal diameterMm) { this.diameterMm = diameterMm; touch(); }

    public BigDecimal getClearanceMm() { return clearanceMm; }
    public void setClearanceMm(BigDecimal clearanceMm) { this.clearanceMm = clearanceMm; touch(); }

    public BigDecimal getInterferenceMm() { return interferenceMm; }
    public void setInterferenceMm(BigDecimal interferenceMm) { this.interferenceMm = interferenceMm; touch(); }

    public BigDecimal getTorqueNm() { return torqueNm; }
    public void setTorqueNm(BigDecimal torqueNm) { this.torqueNm = torqueNm; touch(); }

    public BigDecimal getPressForceKn() { return pressForceKn; }
    public void setPressForceKn(BigDecimal pressForceKn) { this.pressForceKn = pressForceKn; touch(); }

    public BigDecimal getWeldLengthMm() { return weldLengthMm; }
    public void setWeldLengthMm(BigDecimal weldLengthMm) { this.weldLengthMm = weldLengthMm; touch(); }

    public BigDecimal getWeldLegMm() { return weldLegMm; }
    public void setWeldLegMm(BigDecimal weldLegMm) { this.weldLegMm = weldLegMm; touch(); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; touch(); }

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
        if (!(o instanceof AssemblyConnection other)) {
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
        return "AssemblyConnection{" +
                "id='" + id + '\'' +
                ", assemblyId='" + assemblyId + '\'' +
                ", type='" + type + '\'' +
                ", part1Id='" + part1Id + '\'' +
                ", part2Id='" + part2Id + '\'' +
                ", order=" + order +
                ", needsTool=" + needsTool +
                ", needsFixture=" + needsFixture +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенный enum: тип связи
    // ------------------------------------------------------------------

    /**
     * Тип связи/сопряжения.
     */
    public enum ConnectionType {
        /** Фиксированное соединение (без подвижности). */
        FIXED,
        /** Вращательное соединение (подшипник, шарнир). */
        ROTATIONAL,
        /** Поступательное соединение (направляющая). */
        SLIDING,
        /** Сварное соединение. */
        WELDED,
        /** Резьбовое соединение (болт, винт, гайка). */
        THREADED,
        /** Клеевое соединение. */
        GLUED,
        /** Прессовое соединение (посадка с натягом). */
        PRESSED,
        /** Заклёпочное соединение. */
        RIVETED,
        /** Штифтовое соединение. */
        PINNED,
        /** Шпоночное соединение. */
        KEYED,
        /** Шлицевое соединение. */
        SPLINED,
        /** Прочее. */
        OTHER
    }
}