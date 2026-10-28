package ru.iktk.modules.machining.client.dto;

import java.math.BigDecimal;

/**
 * DTO ответа tooling-service (Jackson-friendly).
 * Маппится в {@link ru.iktk.modules.machining.model.CuttingTool} через
 * {@link ru.iktk.modules.machining.client.ToolingClientRouter}.
 */
public class ToolingDto {

    private String type;
    private BigDecimal diameter;
    private String material;
    private String coating;

    public ToolingDto() {
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