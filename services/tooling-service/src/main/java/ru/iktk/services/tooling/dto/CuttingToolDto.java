package ru.iktk.services.tooling.dto;

import java.math.BigDecimal;

/**
 * Режущий инструмент (DTO для REST-контракта tooling-service).
 *
 * <p>Jackson-friendly: no-arg конструктор + getters/setters.
 */
public class CuttingToolDto {

    private String type;
    private BigDecimal diameter;
    private String material;
    private String coating;

    public CuttingToolDto() {
    }

    public CuttingToolDto(String type, BigDecimal diameter, String material, String coating) {
        this.type = type;
        this.diameter = diameter;
        this.material = material;
        this.coating = coating;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getDiameter() { return diameter; }
    public void setDiameter(BigDecimal diameter) { this.diameter = diameter; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getCoating() { return coating; }
    public void setCoating(String coating) { this.coating = coating; }
}