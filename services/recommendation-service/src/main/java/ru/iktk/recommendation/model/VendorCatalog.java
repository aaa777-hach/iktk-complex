package ru.iktk.recommendation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * Доменная модель записи каталога внешнего оборудования ИКТК.
 *
 * <p>Описывает оборудование, доступное у внешних поставщиков
 * (производителей, дилеров, арендных компаний). Используется
 * {@code EquipmentRecommendationService} (позиция 96) при формировании
 * рекомендаций типа {@code PURCHASE} и {@code RENT}.
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code name} — наименование оборудования;</li>
 *   <li>{@code manufacturer} — производитель;</li>
 *   <li>{@code model} — модель;</li>
 *   <li>{@code equipmentType} — тип (TURNING, MILLING, PRESS, CASTING, ...);</li>
 *   <li>{@code operationTypes} — поддерживаемые типы операций;</li>
 *   <li>{@code specs} — технические характеристики (JSONB);</li>
 *   <li>{@code cost} — стоимость (руб.);</li>
 *   <li>{@code rentalCostPerDay} — стоимость аренды в сутки (руб.);</li>
 *   <li>{@code leadTimeDays} — срок поставки (дни);</li>
 *   <li>{@code warrantyMonths} — гарантия (месяцы);</li>
 *   <li>{@code country} — страна производства;</li>
 *   <li>{@code vendorName} — поставщик/дилер;</li>
 *   <li>{@code vendorContact} — контакт поставщика;</li>
 *   <li>{@code available} — доступно ли к заказу;</li>
 *   <li>{@code source} — источник данных (MANUAL, IMPORT, API);</li>
 *   <li>{@code externalId} — идентификатор во внешней системе.</li>
 * </ul>
 *
 * <p>Таблица: {@code vendor_catalog} (PostgreSQL, миграция V6).
 */
@Entity
@Table(
        name = "vendor_catalog",
        indexes = {
                @Index(name = "idx_vendor_type",         columnList = "equipment_type"),
                @Index(name = "idx_vendor_manufacturer", columnList = "manufacturer"),
                @Index(name = "idx_vendor_available",    columnList = "available"),
                @Index(name = "idx_vendor_cost",         columnList = "cost"),
                @Index(name = "idx_vendor_lead_time",    columnList = "lead_time_days"),
                @Index(name = "idx_vendor_external",     columnList = "external_id", unique = true),
                @Index(name = "idx_vendor_created_at",   columnList = "created_at DESC")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VendorCatalog {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Идентификатор во внешней системе (для дедупликации при импорте). */
    @Size(max = 128)
    @Column(name = "external_id", length = 128, unique = true)
    private String externalId;

    // ------------------------------------------------------------------
    // Описание оборудования
    // ------------------------------------------------------------------

    /** Наименование оборудования. */
    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Производитель. */
    @Size(max = 128)
    @Column(name = "manufacturer", length = 128)
    private String manufacturer;

    /** Модель. */
    @Size(max = 128)
    @Column(name = "model", length = 128)
    private String model;

    /** Тип оборудования (TURNING, MILLING, PRESS, CASTING, ...). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "equipment_type", nullable = false, length = 64)
    private String equipmentType;

    /** Описание. */
    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    /** Страна производства. */
    @Size(max = 64)
    @Column(name = "country", length = 64)
    private String country;

    // ------------------------------------------------------------------
    // Технические характеристики
    // ------------------------------------------------------------------

    /**
     * Технические характеристики (JSONB).
     *
     * <p>Произвольный набор: точность, габариты, мощность, усилие,
     * число осей, материалы и т.п.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specs", columnDefinition = "jsonb")
    private Map<String, Object> specs;

    /** Требуется ли ЧПУ. */
    @Column(name = "cnc_capable", nullable = false)
    private boolean cncCapable = false;

    /** Максимальные габариты заготовки. */
    @Size(max = 64)
    @Column(name = "max_dimensions", length = 64)
    private String maxDimensions;

    /** Мощность (кВт). */
    @Column(name = "power_kw", precision = 10, scale = 3)
    private BigDecimal powerKw;

    /** Максимальное усилие (кН). */
    @Column(name = "force_kn", precision = 12, scale = 3)
    private BigDecimal forceKn;

    // ------------------------------------------------------------------
    // Коммерческие параметры
    // ------------------------------------------------------------------

    /** Стоимость покупки (руб.). */
    @Column(name = "cost", precision = 14, scale = 2)
    private BigDecimal cost;

    /** Стоимость аренды в сутки (руб.). */
    @Column(name = "rental_cost_per_day", precision = 14, scale = 2)
    private BigDecimal rentalCostPerDay;

    /** Срок поставки/реализации (дни). */
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    /** Гарантия (месяцы). */
    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    /** Условия поставки / примечания. */
    @Size(max = 1000)
    @Column(name = "terms", length = 1000)
    private String terms;

    // ------------------------------------------------------------------
    // Поставщик
    // ------------------------------------------------------------------

    /** Наименование поставщика/дилера. */
    @Size(max = 255)
    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    /** Контакт поставщика (телефон, email, сайт). */
    @Size(max = 255)
    @Column(name = "vendor_contact", length = 255)
    private String vendorContact;

    /** Регион поставщика. */
    @Size(max = 64)
    @Column(name = "vendor_region", length = 64)
    private String vendorRegion;

    // ------------------------------------------------------------------
    // Доступность и источник
    // ------------------------------------------------------------------

    /** Доступно ли оборудование к заказу. */
    @Column(name = "available", nullable = false)
    private boolean available = true;

    /** Источник данных: MANUAL, IMPORT, API. */
    @Size(max = 32)
    @Column(name = "source", length = 32)
    private String source = "MANUAL";

    // ------------------------------------------------------------------
    // Дополнительно
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

    /** Момент последней синхронизации с внешним источником. */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    protected VendorCatalog() {
    }

    public VendorCatalog(String id, String name, String equipmentType) {
        this.id = id;
        this.name = name;
        this.equipmentType = equipmentType;
        this.available = true;
        this.source = "MANUAL";
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
        private String externalId;
        private String name;
        private String manufacturer;
        private String model;
        private String equipmentType;
        private String description;
        private String country;
        private Map<String, Object> specs;
        private boolean cncCapable = false;
        private String maxDimensions;
        private BigDecimal powerKw;
        private BigDecimal forceKn;
        private BigDecimal cost;
        private BigDecimal rentalCostPerDay;
        private Integer leadTimeDays;
        private Integer warrantyMonths;
        private String terms;
        private String vendorName;
        private String vendorContact;
        private String vendorRegion;
        private boolean available = true;
        private String source = "MANUAL";
        private Map<String, Object> parameters;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant lastSyncedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder externalId(String v) { this.externalId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder manufacturer(String v) { this.manufacturer = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder equipmentType(String v) { this.equipmentType = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder country(String v) { this.country = v; return this; }
        public Builder specs(Map<String, Object> v) { this.specs = v; return this; }
        public Builder cncCapable(boolean v) { this.cncCapable = v; return this; }
        public Builder maxDimensions(String v) { this.maxDimensions = v; return this; }
        public Builder powerKw(BigDecimal v) { this.powerKw = v; return this; }
        public Builder forceKn(BigDecimal v) { this.forceKn = v; return this; }
        public Builder cost(BigDecimal v) { this.cost = v; return this; }
        public Builder rentalCostPerDay(BigDecimal v) { this.rentalCostPerDay = v; return this; }
        public Builder leadTimeDays(Integer v) { this.leadTimeDays = v; return this; }
        public Builder warrantyMonths(Integer v) { this.warrantyMonths = v; return this; }
        public Builder terms(String v) { this.terms = v; return this; }
        public Builder vendorName(String v) { this.vendorName = v; return this; }
        public Builder vendorContact(String v) { this.vendorContact = v; return this; }
        public Builder vendorRegion(String v) { this.vendorRegion = v; return this; }
        public Builder available(boolean v) { this.available = v; return this; }
        public Builder source(String v) { this.source = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }
        public Builder lastSyncedAt(Instant v) { this.lastSyncedAt = v; return this; }

        public VendorCatalog build() {
            VendorCatalog v = new VendorCatalog();
            v.id = this.id;
            v.externalId = this.externalId;
            v.name = this.name;
            v.manufacturer = this.manufacturer;
            v.model = this.model;
            v.equipmentType = this.equipmentType;
            v.description = this.description;
            v.country = this.country;
            v.specs = this.specs;
            v.cncCapable = this.cncCapable;
            v.maxDimensions = this.maxDimensions;
            v.powerKw = this.powerKw;
            v.forceKn = this.forceKn;
            v.cost = this.cost;
            v.rentalCostPerDay = this.rentalCostPerDay;
            v.leadTimeDays = this.leadTimeDays;
            v.warrantyMonths = this.warrantyMonths;
            v.terms = this.terms;
            v.vendorName = this.vendorName;
            v.vendorContact = this.vendorContact;
            v.vendorRegion = this.vendorRegion;
            v.available = this.available;
            v.source = this.source == null ? "MANUAL" : this.source;
            v.parameters = this.parameters;
            Instant now = Instant.now();
            v.createdAt = this.createdAt == null ? now : this.createdAt;
            v.updatedAt = this.updatedAt == null ? now : this.updatedAt;
            v.lastSyncedAt = this.lastSyncedAt;
            return v;
        }
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /** Доступно ли оборудование. */
    public boolean isAvailable() {
        return available;
    }

    /** Поддерживает ли оборудование ЧПУ. */
    public boolean isCncCapable() {
        return cncCapable;
    }

    /** Стоимость аренды (если задана). */
    public boolean hasRentalOption() {
        return rentalCostPerDay != null
                && rentalCostPerDay.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Импортировано ли из внешней системы. */
    public boolean isImported() {
        return "IMPORT".equalsIgnoreCase(source) || "API".equalsIgnoreCase(source);
    }

    /** Есть ли контакт поставщика. */
    public boolean hasVendorContact() {
        return vendorContact != null && !vendorContact.isBlank();
    }

    /** Пометить как недоступное. */
    public void markUnavailable() {
        this.available = false;
        touch();
    }

    /** Пометить как доступное. */
    public void markAvailable() {
        this.available = true;
        touch();
    }

    /** Обновить момент синхронизации. */
    public void markSynced() {
        this.lastSyncedAt = Instant.now();
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

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; touch(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; touch(); }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; touch(); }

    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; touch(); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; touch(); }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; touch(); }

    public Map<String, Object> getSpecs() { return specs; }
    public void setSpecs(Map<String, Object> specs) { this.specs = specs; touch(); }

    public boolean isCncCapableFlag() { return cncCapable; }
    public void setCncCapable(boolean cncCapable) { this.cncCapable = cncCapable; touch(); }

    public String getMaxDimensions() { return maxDimensions; }
    public void setMaxDimensions(String maxDimensions) { this.maxDimensions = maxDimensions; touch(); }

    public BigDecimal getPowerKw() { return powerKw; }
    public void setPowerKw(BigDecimal powerKw) { this.powerKw = powerKw; touch(); }

    public BigDecimal getForceKn() { return forceKn; }
    public void setForceKn(BigDecimal forceKn) { this.forceKn = forceKn; touch(); }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; touch(); }

    public BigDecimal getRentalCostPerDay() { return rentalCostPerDay; }
    public void setRentalCostPerDay(BigDecimal rentalCostPerDay) { this.rentalCostPerDay = rentalCostPerDay; touch(); }

    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; touch(); }

    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; touch(); }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; touch(); }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; touch(); }

    public String getVendorContact() { return vendorContact; }
    public void setVendorContact(String vendorContact) { this.vendorContact = vendorContact; touch(); }

    public String getVendorRegion() { return vendorRegion; }
    public void setVendorRegion(String vendorRegion) { this.vendorRegion = vendorRegion; touch(); }

    public boolean isAvailableFlag() { return available; }
    public void setAvailable(boolean available) { this.available = available; touch(); }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; touch(); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; touch(); }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VendorCatalog other)) {
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
        return "VendorCatalog{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", equipmentType='" + equipmentType + '\'' +
                ", cost=" + cost +
                ", leadTimeDays=" + leadTimeDays +
                ", available=" + available +
                '}';
    }
}