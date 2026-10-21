package ru.iktk.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorCatalogDto {
    private String id;
    private String vendorId;
    private String vendorName;
    private String equipmentType;
    private String equipmentModel;
    private BigDecimal price;
    private Integer leadTimeDays;
    private String contactInfo;
    private String region;
    private Boolean active;
    private Map<String, Object> specifications;
    private Instant createdAt;
    private Instant updatedAt;
}