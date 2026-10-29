package ru.iktk.modules.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.dto.RawItemDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Эвристическая оценка геометрии из STEP (сессия 33, путь А).
 *
 * <p>BREP не парсится. Материал — regex по имени; объём — по числу узлов;
 * толщины/уклоны — дефолты по материалу. Все эвристики в {@code warnings}.
 */
@Service
public class GeometryEstimateService {

    private static final Logger log = LoggerFactory.getLogger(GeometryEstimateService.class);

    /**
     * Regex материала (без \b — для кириллицы в Java границы не работают надёжно).
     * Сработает и на «Сталь 45 вал» → steel, и на «AlSi10Mg bracket» → AlSi10Mg.
     */
    private static final Pattern MATERIAL_PATTERN = Pattern.compile(
            "(?iu)(steel|сталь|алюмин\\w*|alsi\\w*|титан|titan\\w*|ti-?6al|бронз\\w*|bronze|чугун|cast.?iron|латун\\w*|brass|медь|copper|12х18н10т|20х|40х|30хгса|20гл|35л|40л|д16|амг)"
    );

    private static final double DENSITY_STEEL = 7.85;
    private static final double DENSITY_AL    = 2.70;
    private static final double DENSITY_TI    = 4.51;
    private static final double DENSITY_BRONZE = 8.80;
    private static final double DENSITY_CAST_IRON = 7.20;

    public EstimateResult estimate(RawBomDto bom, String explicitMaterial) {
        EstimateResult r = new EstimateResult();
        String mat = detectMaterial(bom, explicitMaterial, r.warnings);
        r.material = mat;

        int items = (bom.getItems() == null) ? 0 : bom.getItems().size();
        r.complexityScore = estimateComplexity(items, r.warnings);

        BigDecimal volume = BigDecimal.valueOf(1000L + 250L * items).setScale(4, RoundingMode.HALF_UP);
        r.volume = volume;
        r.warnings.add("volume estimated: 1000 + 250*items (" + items + "); BREP not parsed");

        double density = densityOf(mat);
        BigDecimal mass = volume.multiply(BigDecimal.valueOf(density))
                .multiply(BigDecimal.valueOf(1e-6))
                .setScale(4, RoundingMode.HALF_UP);
        r.mass = mass;
        r.warnings.add("mass estimated: volume * density " + density + " g/cm3");

        applyMaterialDefaults(mat, r);

        r.nominalDimensions = BigDecimal.valueOf(100L + 10L * items).setScale(4, RoundingMode.HALF_UP);
        r.warnings.add("nominalDimensions estimated from items; BREP not parsed");

        r.undercuts = false;
        r.hotSpots = false;
        r.warnings.add("undercuts/hotSpots not detected: BREP not parsed (requires OpenCascade)");

        log.info("GeometryEstimateService.estimate: material={}, volume={}, mass={}, complexity={}, warnings={}",
                mat, r.volume, r.mass, r.complexityScore, r.warnings.size());
        return r;
    }

    private String detectMaterial(RawBomDto bom, String explicit, List<String> warnings) {
        if (explicit != null && !explicit.isBlank()) {
            warnings.add("material from request: " + explicit);
            return explicit;
        }
        String m = matchMaterial(bom.getRootDesignation());
        if (m == null) m = matchMaterial(bom.getRootName());
        if (m == null && bom.getItems() != null) {
            for (RawItemDto it : bom.getItems()) {
                m = matchMaterial(it.getDesignation());
                if (m == null) m = matchMaterial(it.getName());
                if (m == null) m = matchMaterial(it.getMaterial());
                if (m != null) break;
            }
        }
        if (m == null) {
            warnings.add("material not detected: fallback Steel 45");
            return "Steel 45";
        }
        warnings.add("material detected by regex: " + m);
        return m;
    }

    private String matchMaterial(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = MATERIAL_PATTERN.matcher(text);
        if (!m.find()) return null;
        String raw = m.group(1);
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.contains("сталь") || low.equals("steel")) return "Steel 45";
        if (low.contains("алюмин")) return "AlSi10Mg";
        if (low.contains("alsi")) return "AlSi10Mg";
        if (low.contains("титан") || low.contains("titan")) return "Ti-6Al-4V";
        if (low.contains("бронз") || low.equals("bronze")) return "Bronze";
        if (low.contains("чугун") || low.contains("cast")) return "Cast Iron";
        if (low.contains("латун") || low.equals("brass")) return "Brass";
        if (low.contains("медь") || low.equals("copper")) return "Copper";
        return raw;
    }

    private void applyMaterialDefaults(String material, EstimateResult r) {
        String m = (material == null) ? "" : material.toLowerCase(Locale.ROOT);
        if (m.contains("al") || m.contains("алюмин")) {
            r.minWallThickness = BigDecimal.valueOf(3.0);
            r.minFilletRadius = BigDecimal.valueOf(1.5);
            r.minDraftAngle = BigDecimal.valueOf(0.5);
        } else if (m.contains("ti") || m.contains("титан")) {
            r.minWallThickness = BigDecimal.valueOf(2.5);
            r.minFilletRadius = BigDecimal.valueOf(1.5);
            r.minDraftAngle = BigDecimal.valueOf(1.0);
        } else if (m.contains("чугун") || m.contains("cast")) {
            r.minWallThickness = BigDecimal.valueOf(5.0);
            r.minFilletRadius = BigDecimal.valueOf(3.0);
            r.minDraftAngle = BigDecimal.valueOf(1.5);
        } else {
            r.minWallThickness = BigDecimal.valueOf(4.0);
            r.minFilletRadius = BigDecimal.valueOf(2.0);
            r.minDraftAngle = BigDecimal.valueOf(1.0);
        }
        r.warnings.add("wall/fillet/draft are material defaults (BREP not parsed)");
    }

    private double densityOf(String material) {
        String m = (material == null) ? "" : material.toLowerCase(Locale.ROOT);
        if (m.contains("al") || m.contains("алюмин")) return DENSITY_AL;
        if (m.contains("ti") || m.contains("титан")) return DENSITY_TI;
        if (m.contains("бронз") || m.equals("bronze")) return DENSITY_BRONZE;
        if (m.contains("чугун") || m.contains("cast")) return DENSITY_CAST_IRON;
        return DENSITY_STEEL;
    }

    private int estimateComplexity(int items, List<String> warnings) {
        if (items <= 1) return 1;
        double log2 = Math.log(items + 1.0) / Math.log(2.0);
        int score = (int) Math.ceil(log2);
        warnings.add("complexityScore estimated from items=" + items + " -> " + score);
        return Math.max(1, Math.min(10, score));
    }

    public static class EstimateResult {
        public String material;
        public BigDecimal volume;
        public BigDecimal mass;
        public BigDecimal minWallThickness;
        public BigDecimal minFilletRadius;
        public BigDecimal minDraftAngle;
        public BigDecimal nominalDimensions;
        public int complexityScore;
        public boolean undercuts;
        public boolean hotSpots;
        public final List<String> warnings = new ArrayList<>();

        public String getMaterial() { return material; }
        public BigDecimal getVolume() { return volume; }
        public BigDecimal getMass() { return mass; }
        public BigDecimal getMinWallThickness() { return minWallThickness; }
        public BigDecimal getMinFilletRadius() { return minFilletRadius; }
        public BigDecimal getMinDraftAngle() { return minDraftAngle; }
        public BigDecimal getNominalDimensions() { return nominalDimensions; }
        public int getComplexityScore() { return complexityScore; }
        public boolean isUndercuts() { return undercuts; }
        public boolean isHotSpots() { return hotSpots; }
        public List<String> getWarnings() { return warnings; }
    }
}
