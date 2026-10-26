package ru.iktk.modules.machining.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * Настройки модуля механообработки.
 *
 * <p>Справочные значения — заглушки; реальные справочники подключаются
 * в отдельной сессии.
 */
@ConfigurationProperties(prefix = "iktk.machining")
public class MachiningProperties {

    private BigDecimal minInternalRadius = BigDecimal.valueOf(2.0);
    private BigDecimal minWallThickness = BigDecimal.valueOf(1.0);
    private BigDecimal maxHoleDepthRatio = BigDecimal.valueOf(10.0);
    private BigDecimal maxAccuracy = BigDecimal.valueOf(0.02);
    private BigDecimal grindingThreshold = BigDecimal.valueOf(0.01);

    private Cnc cnc = new Cnc();

    // ------------------------------------------------------------------
    // Справочные значения
    // ------------------------------------------------------------------

    public BigDecimal getMinInternalRadius() { return minInternalRadius; }
    public void setMinInternalRadius(BigDecimal v) { this.minInternalRadius = v; }

    public BigDecimal getMinWallThickness() { return minWallThickness; }
    public void setMinWallThickness(BigDecimal v) { this.minWallThickness = v; }

    public BigDecimal getMaxHoleDepthRatio() { return maxHoleDepthRatio; }
    public void setMaxHoleDepthRatio(BigDecimal v) { this.maxHoleDepthRatio = v; }

    public BigDecimal getMaxAccuracy() { return maxAccuracy; }
    public void setMaxAccuracy(BigDecimal v) { this.maxAccuracy = v; }

    public BigDecimal getGrindingThreshold() { return grindingThreshold; }
    public void setGrindingThreshold(BigDecimal v) { this.grindingThreshold = v; }

    public Cnc getCnc() { return cnc; }
    public void setCnc(Cnc v) { this.cnc = v; }

    public String getCncDefaultPostprocessor() {
        return cnc == null || cnc.getDefaultPostprocessor() == null
                ? "ISO6983"
                : cnc.getDefaultPostprocessor();
    }

    // ------------------------------------------------------------------
    // Вычислимые заглушки
    // ------------------------------------------------------------------

    public String getDefaultSetupScheme(int setupOrder, GeometryData geometry) {
        return "Схема установки " + setupOrder;
    }

    public List<String> getFixturesFor(int setupOrder, GeometryData geometry) {
        return List.of("Тиски", "Прихваты");
    }

    public List<String> getSurfacesFor(int setupOrder, GeometryData geometry) {
        return List.of("Поверхность " + setupOrder);
    }

    public boolean isCncCapable(MachiningMethod method) {
        return method != null && method.isCnc();
    }

    public BigDecimal getCuttingSpeed(MachiningMethod method, String material, CuttingTool tool) {
        return BigDecimal.valueOf(120);
    }

    public BigDecimal getFeed(MachiningMethod method, String material, CuttingTool tool) {
        return BigDecimal.valueOf(0.2);
    }

    public BigDecimal getDepthOfCut(MachiningMethod method, BigDecimal allowance) {
        return allowance == null ? BigDecimal.valueOf(1.0)
                : allowance.max(BigDecimal.valueOf(1.0));
    }

    public BigDecimal getToolLife(MachiningMethod method, CuttingTool tool) {
        return BigDecimal.valueOf(60);
    }

    // ------------------------------------------------------------------
    // Вложенный класс
    // ------------------------------------------------------------------

    public static class Cnc {
        private String defaultPostprocessor = "ISO6983";

        public String getDefaultPostprocessor() { return defaultPostprocessor; }
        public void setDefaultPostprocessor(String v) { this.defaultPostprocessor = v; }
    }
}