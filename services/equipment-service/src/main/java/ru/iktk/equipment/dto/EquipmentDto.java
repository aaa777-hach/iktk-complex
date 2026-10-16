package ru.iktk.equipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DTO единицы оборудования ИКТК.
 *
 * <p>Используется для передачи данных между UI/API и сервисом
 * оборудования:
 * <ul>
 *   <li>получение списка и карточки оборудования;</li>
 *   <li>создание и обновление оборудования;</li>
 *   <li>сопоставление требований ТП с парком (matching);</li>
 *   <li>gap-анализ и рекомендации.</li>
 * </ul>
 *
 * <p><b>Согласован с сущностью {@code Equipment} (позиция 77):</b>
 * все бизнес-поля совпадают; связи ({@code capabilities},
 * {@code currentStatus}, {@code compatibleTooling}) передаются
 * отдельными полями-DTO.
 *
 * <p>DTO иммутабельно после сборки через {@link Builder}, но допускает
 * установку полей через сеттеры для совместимости с Jackson и MapStruct.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Equipment", description = "Единица оборудования предприятия")
public class EquipmentDto implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    @Schema(description = "Идентификатор оборудования", example = "eq-12345")
    private String id;

    @Schema(description = "Наименование", example = "Токарный станок с ЧПУ")
    @NotBlank(message = "Не указано наименование оборудования")
    @Size(max = 255)
    private String name;

    @Schema(description = "Тип оборудования", example = "TURNING",
            allowableValues = {"TURNING", "MILLING", "DRILLING", "GRINDING",
                    "PRESS", "CASTING", "WELDING", "EDM", "LASER", "OTHER"})
    @NotBlank(message = "Не указан тип оборудования")
    @Size(max = 64)
    private String type;

    @Schema(description = "Модель производителя", example = "16К20Ф3")
    @Size(max = 128)
    private String model;

    @Schema(description = "Производитель", example = "Станкозавод")
    @Size(max = 128)
    private String manufacturer;

    @Schema(description = "Инвентарный номер", example = "INV-001234")
    @Size(max = 64)
    private String inventoryNo;

    @Schema(description = "Серийный номер", example = "SN-987654")
    @Size(max = 64)
    private String serialNo;

    @Schema(description = "Год выпуска", example = "2018")
    @Min(1900)
    private Integer manufactureYear;

    // ------------------------------------------------------------------
    // Расположение
    // ------------------------------------------------------------------

    @Schema(description = "Код предприятия", example = "PLANT-01")
    @Size(max = 64)
    private String enterpriseCode;

    @Schema(description = "Код подразделения", example = "SHOP-3")
    @Size(max = 64)
    private String departmentCode;

    @Schema(description = "Расположение (цех, участок, линия)", example = "Цех №3, участок 2")
    @Size(max = 255)
    private String location;

    // ------------------------------------------------------------------
    // Состояние
    // ------------------------------------------------------------------

    @Schema(description = "Статус оборудования", example = "AVAILABLE",
            allowableValues = {"AVAILABLE", "BUSY", "MAINTENANCE", "DECOMMISSIONED"})
    private String status;

    @Schema(description = "Ответственный", example = "Петров И.И.")
    @Size(max = 128)
    private String responsible;

    @Schema(description = "Дата ввода в эксплуатацию")
    private Instant commissionedAt;

    @Schema(description = "Дата списания")
    private Instant decommissionedAt;

    // ------------------------------------------------------------------
    // Связи и агрегаты
    // ------------------------------------------------------------------

    @Schema(description = "Возможности оборудования")
    @Valid
    private List<EquipmentCapabilityDto> capabilities;

    @Schema(description = "Текущее состояние и загрузка")
    @Valid
    private EquipmentStatusDto currentStatus;

    @Schema(description = "Совместимая оснастка (ID)")
    private List<String> compatibleTooling;

    @Schema(description = "Произвольные параметры")
    private Map<String, Object> parameters;

    // ------------------------------------------------------------------
    // Служебные поля
    // ------------------------------------------------------------------

    @Schema(description = "Момент создания")
    private Instant createdAt;

    @Schema(description = "Момент последнего изменения")
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    /** Пустой конструктор для Jackson и MapStruct. */
    public EquipmentDto() {
    }

    /**
     * Основной конструктор.
     */
    public EquipmentDto(String name, String type) {
        this.name = name;
        this.type = type;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder для удобного создания DTO.
     */
    public static class Builder {
        private String id;
        private String name;
        private String type;
        private String model;
        private String manufacturer;
        private String inventoryNo;
        private String serialNo;
        private Integer manufactureYear;
        private String enterpriseCode;
        private String departmentCode;
        private String location;
        private String status;
        private String responsible;
        private Instant commissionedAt;
        private Instant decommissionedAt;
        private List<EquipmentCapabilityDto> capabilities;
        private EquipmentStatusDto currentStatus;
        private List<String> compatibleTooling;
        private Map<String, Object> parameters;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder manufacturer(String v) { this.manufacturer = v; return this; }
        public Builder inventoryNo(String v) { this.inventoryNo = v; return this; }
        public Builder serialNo(String v) { this.serialNo = v; return this; }
        public Builder manufactureYear(Integer v) { this.manufactureYear = v; return this; }
        public Builder enterpriseCode(String v) { this.enterpriseCode = v; return this; }
        public Builder departmentCode(String v) { this.departmentCode = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder responsible(String v) { this.responsible = v; return this; }
        public Builder commissionedAt(Instant v) { this.commissionedAt = v; return this; }
        public Builder decommissionedAt(Instant v) { this.decommissionedAt = v; return this; }
        public Builder capabilities(List<EquipmentCapabilityDto> v) { this.capabilities = v; return this; }
        public Builder currentStatus(EquipmentStatusDto v) { this.currentStatus = v; return this; }
        public Builder compatibleTooling(List<String> v) { this.compatibleTooling = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public EquipmentDto build() {
            EquipmentDto dto = new EquipmentDto();
            dto.id = this.id;
            dto.name = this.name;
            dto.type = this.type;
            dto.model = this.model;
            dto.manufacturer = this.manufacturer;
            dto.inventoryNo = this.inventoryNo;
            dto.serialNo = this.serialNo;
            dto.manufactureYear = this.manufactureYear;
            dto.enterpriseCode = this.enterpriseCode;
            dto.departmentCode = this.departmentCode;
            dto.location = this.location;
            dto.status = this.status;
            dto.responsible = this.responsible;
            dto.commissionedAt = this.commissionedAt;
            dto.decommissionedAt = this.decommissionedAt;
            dto.capabilities = this.capabilities;
            dto.currentStatus = this.currentStatus;
            dto.compatibleTooling = this.compatibleTooling;
            dto.parameters = this.parameters;
            dto.createdAt = this.createdAt;
            dto.updatedAt = this.updatedAt;
            return dto;
        }
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    /** Является ли оборудование доступным. */
    public boolean isAvailable() {
        return "AVAILABLE".equalsIgnoreCase(this.status);
    }

    /** Поддерживает ли оборудование указанную операцию. */
    public boolean supportsOperation(String operationType) {
        if (operationType == null || capabilities == null) {
            return false;
        }
        return capabilities.stream()
                .anyMatch(c -> operationType.equalsIgnoreCase(c.getOperationType()));
    }

    /** Есть ли возможности. */
    public boolean hasCapabilities() {
        return capabilities != null && !capabilities.isEmpty();
    }

    /** Есть ли совместимая оснастка. */
    public boolean hasCompatibleTooling() {
        return compatibleTooling != null && !compatibleTooling.isEmpty();
    }

    // ------------------------------------------------------------------
    // Геттеры и сеттеры
    // ------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getInventoryNo() { return inventoryNo; }
    public void setInventoryNo(String inventoryNo) { this.inventoryNo = inventoryNo; }

    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }

    public Integer getManufactureYear() { return manufactureYear; }
    public void setManufactureYear(Integer manufactureYear) { this.manufactureYear = manufactureYear; }

    public String getEnterpriseCode() { return enterpriseCode; }
    public void setEnterpriseCode(String enterpriseCode) { this.enterpriseCode = enterpriseCode; }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResponsible() { return responsible; }
    public void setResponsible(String responsible) { this.responsible = responsible; }

    public Instant getCommissionedAt() { return commissionedAt; }
    public void setCommissionedAt(Instant commissionedAt) { this.commissionedAt = commissionedAt; }

    public Instant getDecommissionedAt() { return decommissionedAt; }
    public void setDecommissionedAt(Instant decommissionedAt) { this.decommissionedAt = decommissionedAt; }

    public List<EquipmentCapabilityDto> getCapabilities() { return capabilities; }
    public void setCapabilities(List<EquipmentCapabilityDto> capabilities) { this.capabilities = capabilities; }

    public EquipmentStatusDto getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(EquipmentStatusDto currentStatus) { this.currentStatus = currentStatus; }

    public List<String> getCompatibleTooling() { return compatibleTooling; }
    public void setCompatibleTooling(List<String> compatibleTooling) { this.compatibleTooling = compatibleTooling; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EquipmentDto other)) {
            return false;
        }
        return Objects.equals(id, other.id)
                && Objects.equals(inventoryNo, other.inventoryNo)
                && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, inventoryNo, name);
    }

    @Override
    public String toString() {
        return "EquipmentDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", inventoryNo='" + inventoryNo + '\'' +
                ", status='" + status + '\'' +
                ", location='" + location + '\'' +
                ", capabilities=" + (capabilities == null ? 0 : capabilities.size()) +
                '}';
    }
}