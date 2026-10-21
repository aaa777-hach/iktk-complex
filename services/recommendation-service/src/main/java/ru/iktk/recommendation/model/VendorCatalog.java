package ru.iktk.recommendation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "vendor_catalog", indexes = {
        @Index(name = "idx_vendor_type", columnList = "equipment_type"),
        @Index(name = "idx_vendor_region", columnList = "region")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
public class VendorCatalog {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @Column(name = "vendor_id", length = 64)
    private String vendorId;

    @Column(name = "vendor_name", length = 256)
    private String vendorName;

    @Column(name = "equipment_type", length = 128)
    private String equipmentType;

    @Column(name = "equipment_model", length = 128)
    private String equipmentModel;

    @Column(name = "price", precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "contact_info", length = 512)
    private String contactInfo;

    @Column(name = "region", length = 128)
    private String region;

    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specifications", columnDefinition = "jsonb")
    private Map<String, Object> specifications;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VendorCatalog() {}

    @PrePersist
    void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getVendorId() { return vendorId; }
    public void setVendorId(String v) { this.vendorId = v; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String v) { this.vendorName = v; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String v) { this.equipmentType = v; }
    public String getEquipmentModel() { return equipmentModel; }
    public void setEquipmentModel(String v) { this.equipmentModel = v; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal v) { this.price = v; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer v) { this.leadTimeDays = v; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String v) { this.contactInfo = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active = v; }
    public Map<String, Object> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, Object> v) { this.specifications = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getName() { return vendorName; }
    public java.math.BigDecimal getCost() { return price; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VendorCatalog x)) return false;
        return Objects.equals(id, x.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}