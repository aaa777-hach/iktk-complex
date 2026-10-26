package ru.iktk.modules.machining.model;

import java.math.BigDecimal;

/**
 * Режущий инструмент.
 */
public class CuttingTool {

    private String type;
    private BigDecimal diameter;
    private String material;
    private String coating;

    public CuttingTool() {
    }

    private CuttingTool(Builder b) {
        this.type = b.type;
        this.diameter = b.diameter;
        this.material = b.material;
        this.coating = b.coating;
    }

    public String getType() { return type; }
    public BigDecimal getDiameter() { return diameter; }
    public String getMaterial() { return material; }
    public String getCoating() { return coating; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String type;
        private BigDecimal diameter;
        private String material;
        private String coating;

        public Builder type(String v) { this.type = v; return this; }
        public Builder diameter(BigDecimal v) { this.diameter = v; return this; }
        public Builder material(String v) { this.material = v; return this; }
        public Builder coating(String v) { this.coating = v; return this; }

        public CuttingTool build() { return new CuttingTool(this); }
    }
}