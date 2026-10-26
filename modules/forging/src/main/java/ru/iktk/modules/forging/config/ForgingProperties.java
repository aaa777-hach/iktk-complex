package ru.iktk.modules.forging.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.iktk.modules.forging.model.ForgingMethod;
import ru.iktk.modules.forging.model.ForgingTransition.TransitionType;

import java.math.BigDecimal;

/**
 * Настройки штамповки. Справочные значения (для реальных норм — отдельный проект).
 */
@ConfigurationProperties(prefix = "iktk.forging")
@Getter
@Setter
public class ForgingProperties {

    private BigDecimal minFilletRadius = BigDecimal.valueOf(2);
    private BigDecimal minWallThickness = BigDecimal.valueOf(3);
    private BigDecimal minDraftAngle = BigDecimal.valueOf(1);
    private BigDecimal maxHeightToDiameterRatio = BigDecimal.valueOf(3);
    private BigDecimal coldForgingMaxThickness = BigDecimal.valueOf(20);

    public BigDecimal getFlashCoefficient(ForgingMethod method) {
        return switch (method) {
            case HOT_DIE_FORGING -> BigDecimal.valueOf(0.15);
            case COLD_DIE_FORGING -> BigDecimal.valueOf(0.05);
            case UPSETTING -> BigDecimal.valueOf(0.08);
            case EXTRUSION -> BigDecimal.valueOf(0.10);
            case SHEET_STAMPING -> BigDecimal.valueOf(0.12);
        };
    }

    public BigDecimal getScaleLossCoefficient(ForgingMethod method) {
        return method == ForgingMethod.HOT_DIE_FORGING
                ? BigDecimal.valueOf(0.02)
                : BigDecimal.valueOf(0.005);
    }

    public BigDecimal getTemperatureFor(TransitionType type, String material) {
        if (type == TransitionType.FINISHING || type == TransitionType.BLOCKING) {
            return BigDecimal.valueOf(1200);
        }
        return BigDecimal.valueOf(1150);
    }

    public BigDecimal getDeformationDegreeFor(TransitionType type) {
        return switch (type) {
            case UPSETTING -> BigDecimal.valueOf(0.4);
            case DRAWING -> BigDecimal.valueOf(0.3);
            case PIERCING -> BigDecimal.valueOf(0.2);
            case BLOCKING -> BigDecimal.valueOf(0.5);
            case FINISHING -> BigDecimal.valueOf(0.15);
            case TRIMMING -> BigDecimal.valueOf(0.0);
        };
    }

    public BigDecimal getYieldStrength(String material, BigDecimal temperature) {
        if (material == null) return BigDecimal.valueOf(200);
        String m = material.toLowerCase();
        if (m.contains("алюм")) return BigDecimal.valueOf(80);
        if (m.contains("чугун")) return BigDecimal.valueOf(150);
        if (m.contains("сталь")) return BigDecimal.valueOf(250);
        if (m.contains("бронз")) return BigDecimal.valueOf(200);
        return BigDecimal.valueOf(220);
    }

    public BigDecimal getForceCoefficient(TransitionType type) {
        return switch (type) {
            case UPSETTING -> BigDecimal.valueOf(1.2);
            case DRAWING -> BigDecimal.valueOf(1.1);
            case PIERCING -> BigDecimal.valueOf(1.3);
            case BLOCKING -> BigDecimal.valueOf(1.5);
            case FINISHING -> BigDecimal.valueOf(1.4);
            case TRIMMING -> BigDecimal.valueOf(0.8);
        };
    }

    public String getToolingMaterial(ForgingMethod method) {
        return "Сталь 5ХНМ";
    }

    public BigDecimal estimateToolingCost(ForgingMethod method, Object geometry) {
        return BigDecimal.valueOf(250000);
    }
}
