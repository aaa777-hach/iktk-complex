package ru.iktk.modules.casting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.iktk.modules.casting.model.CastingMethod;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "iktk.casting")
public class CastingProperties {

    private String defaultMethod = "SAND_CASTING";
    private BigDecimal minWallThickness = BigDecimal.valueOf(3);
    private BigDecimal minFilletRadius = BigDecimal.valueOf(2);
    private BigDecimal minDraftAngle = BigDecimal.valueOf(1);

    public String getDefaultMethod() { return defaultMethod; }
    public void setDefaultMethod(String v) { this.defaultMethod = v; }
    public BigDecimal getMinWallThickness() { return minWallThickness; }
    public void setMinWallThickness(BigDecimal v) { this.minWallThickness = v; }
    public BigDecimal getMinFilletRadius() { return minFilletRadius; }
    public void setMinFilletRadius(BigDecimal v) { this.minFilletRadius = v; }
    public BigDecimal getMinDraftAngle() { return minDraftAngle; }
    public void setMinDraftAngle(BigDecimal v) { this.minDraftAngle = v; }

    public BigDecimal getShrinkageCoefficient(String material, CastingMethod method) {
        if (material == null) return BigDecimal.valueOf(0.01);
        String m = material.toLowerCase();
        if (m.contains("Р°Р»СЋРјРёРЅ")) return BigDecimal.valueOf(0.013);
        if (m.contains("С‡СѓРіСѓРЅ")) return BigDecimal.valueOf(0.010);
        if (m.contains("СЃС‚Р°Р»СЊ")) return BigDecimal.valueOf(0.020);
        if (m.contains("Р±СЂРѕРЅР·")) return BigDecimal.valueOf(0.015);
        return BigDecimal.valueOf(0.012);
    }

    public BigDecimal getMachiningAllowance(CastingMethod method, BigDecimal dimensions) {
        return BigDecimal.valueOf(3);
    }

    public String getToolingMaterial(CastingMethod method) {
        return "РЎС‚Р°Р»СЊ 45";
    }

    public BigDecimal estimateToolingCost(CastingMethod method, Object geometry) {
        return BigDecimal.valueOf(150000);
    }
}