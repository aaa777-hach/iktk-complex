package ru.iktk.modules.casting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public class ShrinkageCalculation {

    @Column(name = "shrinkage_percent", precision = 8, scale = 4)
    private BigDecimal shrinkagePercent;

    @Column(name = "linear_allowance_mm", precision = 8, scale = 4)
    private BigDecimal linearAllowanceMm;

    @Column(name = "shrinkage_material", length = 100)
    private String material;

    @Enumerated(EnumType.STRING)
    @Column(name = "shrinkage_method", length = 50)
    private CastingMethod method;

    public ShrinkageCalculation() {}

    public BigDecimal getShrinkagePercent() { return shrinkagePercent; }
    public void setShrinkagePercent(BigDecimal v) { this.shrinkagePercent = v; }
    public BigDecimal getLinearAllowanceMm() { return linearAllowanceMm; }
    public void setLinearAllowanceMm(BigDecimal v) { this.linearAllowanceMm = v; }
    public String getMaterial() { return material; }
    public void setMaterial(String v) { this.material = v; }
    public CastingMethod getMethod() { return method; }
    public void setMethod(CastingMethod v) { this.method = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ShrinkageCalculation s = new ShrinkageCalculation();
        public Builder shrinkagePercent(BigDecimal v) { s.shrinkagePercent = v; return this; }
        public Builder linearAllowanceMm(BigDecimal v) { s.linearAllowanceMm = v; return this; }
        public Builder material(String v) { s.material = v; return this; }
        public Builder method(CastingMethod v) { s.method = v; return this; }
        public ShrinkageCalculation build() { return s; }
    }
}