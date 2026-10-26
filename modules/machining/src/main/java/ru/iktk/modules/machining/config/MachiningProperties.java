package ru.iktk.modules.machining.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Настройки модуля механообработки.
 *
 * <p>Справочники режимов резания (V, S, t, T) — в {@code cuttingData}:
 * ключ 1 — материал заготовки (normalize: lowercase, trim), ключ 2 — метод.
 * При отсутствии пары — fallback на {@code DEFAULT_CUTTING_DATA}.
 *
 * <p>Имена материалов: {@code steel-45}, {@code steel 20kh}, {@code alsi10mg},
 * {@code al-6061}, {@code stainless-304}. Match — точное или по подстроке.
 */
@ConfigurationProperties(prefix = "iktk.machining")
public class MachiningProperties {

    private BigDecimal minInternalRadius = BigDecimal.valueOf(2.0);
    private BigDecimal minWallThickness = BigDecimal.valueOf(1.0);
    private BigDecimal maxHoleDepthRatio = BigDecimal.valueOf(10.0);
    private BigDecimal maxAccuracy = BigDecimal.valueOf(0.02);
    private BigDecimal grindingThreshold = BigDecimal.valueOf(0.01);
    private BigDecimal blankLength = BigDecimal.valueOf(100);
    private BigDecimal blankWidth = BigDecimal.valueOf(100);
    private BigDecimal blankHeight = BigDecimal.valueOf(20);

    private Cnc cnc = new Cnc();

    /**
     * Справочник режимов резания: material → method → CuttingData.
     * Может быть переопределён через YAML (например, в будущем).
     */
    private Map<String, Map<MachiningMethod, CuttingData>> cuttingData = defaultCuttingData();

    /** Fallback, если материал не найден. */
    private static final Map<MachiningMethod, CuttingData> DEFAULT_CUTTING_DATA = Map.of(
            MachiningMethod.MILLING,   new CuttingData(bd(120), bd(0.2), bd(2),   bd(60)),
            MachiningMethod.DRILLING,  new CuttingData(bd(25),  bd(0.15), bd(1),  bd(45)),
            MachiningMethod.TURNING,   new CuttingData(bd(180), bd(0.3), bd(2),   bd(60)),
            MachiningMethod.BORING,    new CuttingData(bd(120), bd(0.2), bd(1),   bd(60)),
            MachiningMethod.REAMING,   new CuttingData(bd(15),  bd(0.1), bd(0.5), bd(60)),
            MachiningMethod.GRINDING,  new CuttingData(bd(30),  bd(0.05), bd(0.05), bd(30)),
            MachiningMethod.EDM,       new CuttingData(bd(0),   bd(0),   bd(0),   bd(0)),
            MachiningMethod.LASER,     new CuttingData(bd(0),   bd(0),   bd(0),   bd(0)),
            MachiningMethod.CONTROL,   new CuttingData(bd(0),   bd(0),   bd(0),   bd(0))
    );

    /** Типичная глубина резания по методу (для getDepthOfCut без material). */
    private static final Map<MachiningMethod, BigDecimal> TYPICAL_DEPTH = Map.of(
            MachiningMethod.MILLING,   bd(2),
            MachiningMethod.TURNING,   bd(2),
            MachiningMethod.BORING,    bd(1),
            MachiningMethod.DRILLING,  bd(1),
            MachiningMethod.REAMING,   bd(0.5),
            MachiningMethod.GRINDING,  bd(0.05)
    );

    // ------------------------------------------------------------------
    // Справочные значения (технологичность)
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

    public BigDecimal getBlankLength() { return blankLength; }
    public void setBlankLength(BigDecimal v) { this.blankLength = v; }

    public BigDecimal getBlankWidth() { return blankWidth; }
    public void setBlankWidth(BigDecimal v) { this.blankWidth = v; }

    public BigDecimal getBlankHeight() { return blankHeight; }
    public void setBlankHeight(BigDecimal v) { this.blankHeight = v; }

    public Cnc getCnc() { return cnc; }
    public void setCnc(Cnc v) { this.cnc = v; }

    public String getCncDefaultPostprocessor() {
        return cnc == null || cnc.getDefaultPostprocessor() == null
                ? "ISO6983"
                : cnc.getDefaultPostprocessor();
    }

    public Map<String, Map<MachiningMethod, CuttingData>> getCuttingData() { return cuttingData; }
    public void setCuttingData(Map<String, Map<MachiningMethod, CuttingData>> v) { this.cuttingData = v; }

    // ------------------------------------------------------------------
    // Установы и базирование (упрощённые)
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

    // ------------------------------------------------------------------
    // Режимы резания — lookup
    // ------------------------------------------------------------------

    public BigDecimal getCuttingSpeed(MachiningMethod method, String material, CuttingTool tool) {
        return lookup(method, material).cuttingSpeedMPerMin();
    }

    public BigDecimal getFeed(MachiningMethod method, String material, CuttingTool tool) {
        return lookup(method, material).feedMmPerRev();
    }

    public BigDecimal getDepthOfCut(MachiningMethod method, BigDecimal allowance) {
        BigDecimal t = TYPICAL_DEPTH.getOrDefault(method, BigDecimal.ONE);
        if (allowance == null) {
            return t;
        }
        return t.min(allowance);
    }

    public BigDecimal getToolLife(MachiningMethod method, String material, CuttingTool tool) {
        return lookup(method, material).toolLifeMin();
    }

    /**
     * Найти режимы резания для пары (material, method).
     *
     * <p>Порядок:
     * <ol>
     *   <li>точное совпадение (после normalize);</li>
     *   <li>substring (в обе стороны);</li>
     *   <li>DEFAULT_CUTTING_DATA.</li>
     * </ol>
     */
    private CuttingData lookup(MachiningMethod method, String material) {
        MachiningMethod m = method == null ? MachiningMethod.MILLING : method;
        String key = normalize(material);

        if (key != null && cuttingData != null) {
            Map<MachiningMethod, CuttingData> exact = cuttingData.get(key);
            if (exact != null && exact.containsKey(m)) {
                return exact.get(m);
            }
            for (Map.Entry<String, Map<MachiningMethod, CuttingData>> e : cuttingData.entrySet()) {
                String k = e.getKey();
                if (k != null && (key.contains(k) || k.contains(key))) {
                    CuttingData d = e.getValue().get(m);
                    if (d != null) return d;
                }
            }
        }
        return DEFAULT_CUTTING_DATA.getOrDefault(m,
                DEFAULT_CUTTING_DATA.get(MachiningMethod.MILLING));
    }

    private static String normalize(String s) {
        if (s == null || s.isBlank()) return null;
        return s.toLowerCase(Locale.ROOT).trim();
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    // ------------------------------------------------------------------
    // Дефолтный справочник (5 материалов × 5 методов)
    // ------------------------------------------------------------------

    private static Map<String, Map<MachiningMethod, CuttingData>> defaultCuttingData() {
        Map<String, Map<MachiningMethod, CuttingData>> map = new LinkedHashMap<>();

        map.put("steel-45", Map.of(
                MachiningMethod.MILLING,   new CuttingData(bd(120), bd(0.2),  bd(2),    bd(60)),
                MachiningMethod.DRILLING,  new CuttingData(bd(25),  bd(0.15), bd(1),    bd(45)),
                MachiningMethod.TURNING,   new CuttingData(bd(180), bd(0.3),  bd(2),    bd(60)),
                MachiningMethod.BORING,    new CuttingData(bd(120), bd(0.2),  bd(1),    bd(60)),
                MachiningMethod.GRINDING,  new CuttingData(bd(30),  bd(0.05), bd(0.05), bd(30))
        ));

        map.put("steel 20kh", Map.of(
                MachiningMethod.MILLING,   new CuttingData(bd(100), bd(0.2),  bd(2),    bd(60)),
                MachiningMethod.DRILLING,  new CuttingData(bd(20),  bd(0.15), bd(1),    bd(45)),
                MachiningMethod.TURNING,   new CuttingData(bd(160), bd(0.3),  bd(2),    bd(60)),
                MachiningMethod.BORING,    new CuttingData(bd(100), bd(0.2),  bd(1),    bd(60)),
                MachiningMethod.GRINDING,  new CuttingData(bd(25),  bd(0.05), bd(0.05), bd(30))
        ));

        map.put("alsi10mg", Map.of(
                MachiningMethod.MILLING,   new CuttingData(bd(300), bd(0.15), bd(3),    bd(90)),
                MachiningMethod.DRILLING,  new CuttingData(bd(60),  bd(0.1),  bd(1),    bd(60)),
                MachiningMethod.TURNING,   new CuttingData(bd(400), bd(0.25), bd(3),    bd(90)),
                MachiningMethod.BORING,    new CuttingData(bd(300), bd(0.15), bd(2),    bd(90)),
                MachiningMethod.GRINDING,  new CuttingData(bd(80),  bd(0.03), bd(0.05), bd(45))
        ));

        map.put("al-6061", Map.of(
                MachiningMethod.MILLING,   new CuttingData(bd(400), bd(0.15), bd(3),    bd(90)),
                MachiningMethod.DRILLING,  new CuttingData(bd(80),  bd(0.1),  bd(1),    bd(60)),
                MachiningMethod.TURNING,   new CuttingData(bd(500), bd(0.25), bd(3),    bd(90)),
                MachiningMethod.BORING,    new CuttingData(bd(400), bd(0.15), bd(2),    bd(90)),
                MachiningMethod.GRINDING,  new CuttingData(bd(100), bd(0.03), bd(0.05), bd(45))
        ));

        map.put("stainless-304", Map.of(
                MachiningMethod.MILLING,   new CuttingData(bd(80),  bd(0.15), bd(1.5),  bd(45)),
                MachiningMethod.DRILLING,  new CuttingData(bd(15),  bd(0.1),  bd(1),    bd(30)),
                MachiningMethod.TURNING,   new CuttingData(bd(120), bd(0.2),  bd(1.5),  bd(45)),
                MachiningMethod.BORING,    new CuttingData(bd(80),  bd(0.15), bd(1),    bd(45)),
                MachiningMethod.GRINDING,  new CuttingData(bd(20),  bd(0.04), bd(0.05), bd(30))
        ));

        return map;
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