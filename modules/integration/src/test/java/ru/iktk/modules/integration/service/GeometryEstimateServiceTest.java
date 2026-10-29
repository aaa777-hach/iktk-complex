package ru.iktk.modules.integration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.dto.RawItemDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GeometryEstimateService — эвристики из STEP (сессия 33)")
class GeometryEstimateServiceTest {

    private GeometryEstimateService service;

    @BeforeEach
    void setUp() {
        service = new GeometryEstimateService();
    }

    private RawItemDto item(String designation, String material) {
        RawItemDto it = new RawItemDto();
        it.setId("i1");
        it.setDesignation(designation);
        it.setMaterial(material);
        it.setQuantity(1);
        it.setName(designation);
        return it;
    }

    private RawBomDto bom(String rootName, List<RawItemDto> items) {
        RawBomDto b = new RawBomDto();
        b.setRootId("root");
        b.setRootName(rootName);
        b.setRootDesignation(rootName);
        b.setItems(items);
        return b;
    }

    @Test
    @DisplayName("material из request перекрывает regex")
    void material_fromRequest() {
        GeometryEstimateService.EstimateResult r =
                service.estimate(bom("AnyPart", List.of()), "Ti-6Al-4V");
        assertThat(r.getMaterial()).isEqualTo("Ti-6Al-4V");
        assertThat(r.getWarnings()).anyMatch(w -> w.contains("material from request"));
    }

    @Test
    @DisplayName("material: regex по rootName (Сталь) -> Steel 45")
    void material_regex_steel() {
        GeometryEstimateService.EstimateResult r =
                service.estimate(bom("Сталь 45 вал", List.of()), null);
        assertThat(r.getMaterial()).isEqualTo("Steel 45");
        assertThat(r.getWarnings()).anyMatch(w -> w.contains("detected by regex"));
    }

    @Test
    @DisplayName("material: не найден -> Steel 45 + warning fallback")
    void material_fallback() {
        GeometryEstimateService.EstimateResult r =
                service.estimate(bom("unknown-part-xyz", List.of()), null);
        assertThat(r.getMaterial()).isEqualTo("Steel 45");
        assertThat(r.getWarnings()).anyMatch(w -> w.contains("not detected"));
    }

    @Test
    @DisplayName("material: AlSi10Mg из item.designation")
    void material_fromItems_al() {
        RawBomDto b = bom("assembly", List.of(item("AlSi10Mg bracket", null)));
        GeometryEstimateService.EstimateResult r = service.estimate(b, null);
        assertThat(r.getMaterial()).isEqualTo("AlSi10Mg");
        assertThat(r.getMinWallThickness()).isEqualByComparingTo("3.0");
    }

    @Test
    @DisplayName("complexity: 0 items -> 1; 3 items -> 2; 7 items -> 3")
    void complexity_fromItemsCount() {
        assertThat(service.estimate(bom("x", List.of()), null).getComplexityScore()).isEqualTo(1);
        assertThat(service.estimate(bom("x", List.of(item("a", null), item("b", null), item("c", null))), null)
                .getComplexityScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("volume/mass: steel -> density 7.85; пустой BOM -> 1000 мм³")
    void volumeMass_steel() {
        GeometryEstimateService.EstimateResult r =
                service.estimate(bom("Сталь 20", List.of()), null);
        assertThat(r.getVolume()).isEqualByComparingTo("1000.0000");
        // 1000 * 7.85e-6 = 0.00785
        assertThat(r.getMass()).isEqualByComparingTo("0.0079");
        assertThat(r.getWarnings()).anyMatch(w -> w.contains("volume estimated"));
    }
}