package ru.iktk.equipment.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
 * Доменная модель единицы оборудования ИКТК.
 *
 * <p>Описывает паспорт станка, агрегата или иного средства производства,
 * задействованного в ТПП:
 * <ul>
 *   <li>идентификация: наименование, тип, модель, инвентарный номер;</li>
 *   <li>расположение: предприятие, подразделение, цех/участок;</li>
 *   <li>состояние: {@code status} — AVAILABLE, BUSY, MAINTENANCE,
 *       DECOMMISSIONED;</li>
 *   <li>возможности: {@link EquipmentCapability} (OneToMany);</li>
 *   <li>текущая загрузка: {@link EquipmentStatus} (OneToOne);</li>
 *   <li>совместимая оснастка: {@code equipment_tooling} (ManyToMany,
 *       реализуется через отдельную таблицу и репозиторий);</li>
 *   <li>дополнительные атрибуты: {@code parameters} (JSONB).</li>
 * </ul>
 *
 * <p><b>Использование:</b>
 * <ul>
 *   <li>реестр оборудования (UI, API);</li>
 *   <li>matching — сопоставление требований ТП с парком;</li>
 *   <li>gap-анализ — выявление дефицита;</li>
 *   <li>проверка доступности метода обработки при выборе ТП.</li>
 * </ul>
 *
 * <p>Таблица: {@code equipment} (PostgreSQL, миграция V4).
 */
@Entity
@Table(
        name = "equipment",
        indexes = {
                @Index(name = "idx_equipment_type",            columnList = "equipment_type"),
                @Index(name = "idx_equipment_status",          columnList = "status"),
                @Index(name = "idx_equipment_inventory_no",    columnList = "inventory_no", unique = true),
                @Index(name = "idx_equipment_enterprise",      columnList = "enterprise_code"),
                @Index(name = "idx_equipment_department",      columnList = "department_code"),
                @Index(name = "idx_equipment_location",        columnList = "location")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Equipment {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Наименование (например, «Токарный станок с ЧПУ»). */
    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Тип оборудования (например, TURNING, MILLING, PRESS, ...). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "equipment_type", nullable = false, length = 64)
    private String type;

    /** Модель производителя. */
    @Size(max = 128)
    @Column(name = "model", length = 128)
    private String model;

    /** Производитель. */
    @Size(max = 128)
    @Column(name = "manufacturer", length = 128)
    private String manufacturer;

    /** Инвентарный номер (уникальный). */
    @Size(max = 64)
    @Column(name = "inventory_no", length = 64, unique = true)
    private String inventoryNo;

    /** Серийный номер. */
    @Size(max = 64)
    @Column(name = "serial_no", length = 64)
    private String serialNo;

    /** Год выпуска. */
    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    // ------------------------------------------------------------------
    // Расположение
    // ------------------------------------------------------------------

    /** Код предприятия. */
    @Size(max = 64)
    @Column(name = "enterprise_code", length = 64)
    private String enterpriseCode;

    /** Код подразделения. */
    @Size(max = 64)
    @Column(name = "department_code", length = 64)
    private String departmentCode;

    /** Расположение (цех, участок, линия). */
    @Size(max = 255)
    @Column(name = "location", length = 255)
    private String location;

    // ------------------------------------------------------------------
    // Состояние
    // ------------------------------------------------------------------

    /** Статус оборудования. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** Ответственный (ФИО или логин). */
    @Size(max = 128)
    @Column(name = "responsible", length = 128)
    private String responsible;

    /** Дата ввода в эксплуатацию. */
    @Column(name = "commissioned_at")
    private Instant commissionedAt;

    /** Дата списания. */
    @Column(name = "decommissioned_at")
    private Instant decommissionedAt;

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

    /** Возможности оборудования. */
    @OneToMany(
            mappedBy = "equipmentId",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<EquipmentCapability> capabilities = new ArrayList<>();

    /** Текущее состояние и загрузка. */
    @OneToOne(
            mappedBy = "equipmentId",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private EquipmentStatus currentStatus;

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

    protected Equipment() {
    }

    public Equipment(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = EquipmentStatusEnum.AVAILABLE.name();
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Пометить оборудование как доступное.
     */
    public void markAvailable() {
        this.status = EquipmentStatusEnum.AVAILABLE.name();
        touch();
    }

    /**
     * Пометить оборудование как занятое.
     */
    public void markBusy() {
        this.status = EquipmentStatusEnum.BUSY.name();
        touch();
    }

    /**
     * Пометить оборудование как находящееся на обслуживании.
     */
    public void markMaintenance() {
        this.status = EquipmentStatusEnum.MAINTENANCE.name();
        touch();
    }

    /**
     * Списать оборудование.
     */
    public void decommission() {
        this.status = EquipmentStatusEnum.DECOMMISSIONED.name();
        this.decommissionedAt = Instant.now();
        touch();
    }

    /**
     * Является ли оборудование доступным.
     */
    public boolean isAvailable() {
        return EquipmentStatusEnum.AVAILABLE.name().equals(this.status);
    }

    /**
     * Поддерживает ли оборудование указанную операцию.
     */
    public boolean supportsOperation(String operationType) {
        if (operationType == null || capabilities == null) {
            return false;
        }
        return capabilities.stream()
                .anyMatch(c -> operationType.equals(c.getOperationType()));
    }

    /**
     * Добавить возможность.
     */
    public void addCapability(EquipmentCapability capability) {
        if (capability == null) {
            return;
        }
        capability.setEquipmentId(this.id);
        this.capabilities.add(capability);
        touch();
    }

    /**
     * Удалить возможность.
     */
    public void removeCapability(EquipmentCapability capability) {
        if (capability == null) {
            return;
        }
        this.capabilities.remove(capability);
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; touch(); }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; touch(); }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; touch(); }

    public String getInventoryNo() { return inventoryNo; }
    public void setInventoryNo(String inventoryNo) { this.inventoryNo = inventoryNo; touch(); }

    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; touch(); }

    public Integer getManufactureYear() { return manufactureYear; }
    public void setManufactureYear(Integer manufactureYear) { this.manufactureYear = manufactureYear; touch(); }

    public String getEnterpriseCode() { return enterpriseCode; }
    public void setEnterpriseCode(String enterpriseCode) { this.enterpriseCode = enterpriseCode; touch(); }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; touch(); }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; touch(); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }

    public String getResponsible() { return responsible; }
    public void setResponsible(String responsible) { this.responsible = responsible; touch(); }

    public Instant getCommissionedAt() { return commissionedAt; }
    public void setCommissionedAt(Instant commissionedAt) { this.commissionedAt = commissionedAt; touch(); }

    public Instant getDecommissionedAt() { return decommissionedAt; }
    public void setDecommissionedAt(Instant decommissionedAt) { this.decommissionedAt = decommissionedAt; touch(); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }

    public List<EquipmentCapability> getCapabilities() { return capabilities; }
    public void setCapabilities(List<EquipmentCapability> capabilities) { this.capabilities = capabilities; touch(); }

    public EquipmentStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(EquipmentStatus currentStatus) { this.currentStatus = currentStatus; touch(); }

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
        if (!(o instanceof Equipment other)) {
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
        return "Equipment{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", inventoryNo='" + inventoryNo + '\'' +
                ", status='" + status + '\'' +
                ", location='" + location + '\'' +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенный enum: статус оборудования
    // ------------------------------------------------------------------

    /**
     * Статус оборудования.
     */
    public enum EquipmentStatusEnum {
        /** Доступно для работы. */
        AVAILABLE,
        /** Занято. */
        BUSY,
        /** На обслуживании. */
        MAINTENANCE,
        /** Списано. */
        DECOMMISSIONED
    }
}