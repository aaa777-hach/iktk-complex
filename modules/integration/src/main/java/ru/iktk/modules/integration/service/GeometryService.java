package ru.iktk.modules.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GeometryService {

    private static final Logger log = LoggerFactory.getLogger(GeometryService.class);

    public Map<String, Object> buildGeometry(String projectId, String objectRef) {
        String ref = (objectRef == null || objectRef.isBlank()) ? "default-object" : objectRef;
        int seed = Math.abs(ref.hashCode());
        double volume = 1000 + (seed % 500);
        double mass = volume * 0.00785;
        double minWall = 4 + (seed % 3);
        double minFillet = 2 + (seed % 2);
        double minDraft = 1 + (seed % 2);
        double nominal = 100 + (seed % 50);
        int complexity = 2 + (seed % 4);
        boolean undercuts = (seed % 7) == 0;
        boolean hotSpots = (seed % 5) == 0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectRef", ref);
        m.put("material", "Steel 20Kh");
        m.put("volume", BigDecimal.valueOf(volume));
        m.put("mass", BigDecimal.valueOf(mass).setScale(3, RoundingMode.HALF_UP));
        m.put("minWallThickness", BigDecimal.valueOf(minWall));
        m.put("minFilletRadius", BigDecimal.valueOf(minFillet));
        m.put("minDraftAngle", BigDecimal.valueOf(minDraft));
        m.put("nominalDimensions", BigDecimal.valueOf(nominal));
        m.put("complexityScore", complexity);
        m.put("undercuts", undercuts);
        m.put("hotSpots", hotSpots);

        log.info("GeometryService: projectId={}, objectRef={}, volume={}, complexity={}",
                projectId, ref, volume, complexity);
        return m;
    }
}
