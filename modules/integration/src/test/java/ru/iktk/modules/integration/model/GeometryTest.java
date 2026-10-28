package ru.iktk.modules.integration.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Geometry — JPA-сущность (геттеры, defaults)")
class GeometryTest {

    private Geometry newFilled() {
        Geometry g = new Geometry();
        ReflectionTestUtils.setField(g, "id", "g-1");
        ReflectionTestUtils.setField(g, "objectRef", "geo-12345");
        ReflectionTestUtils.setField(g, "projectId", "p-1");
        ReflectionTestUtils.setField(g, "material", "Steel 20Kh");
        ReflectionTestUtils.setField(g, "volume", BigDecimal.valueOf(1000));
        ReflectionTestUtils.setField(g, "mass", BigDecimal.valueOf(7850));
        ReflectionTestUtils.setField(g, "minWallThickness", BigDecimal.valueOf(3));
        ReflectionTestUtils.setField(g, "minFilletRadius", BigDecimal.valueOf(2));
        ReflectionTestUtils.setField(g, "minDraftAngle", BigDecimal.valueOf(1));
        ReflectionTestUtils.setField(g, "nominalDimensions", BigDecimal.valueOf(100));
        ReflectionTestUtils.setField(g, "complexityScore", 4);
        ReflectionTestUtils.setField(g, "undercuts", true);
        ReflectionTestUtils.setField(g, "hotSpots", false);
        return g;
    }

    @Test
    @DisplayName("геттеры возвращают заполненные значения")
    void getters_returnFilledValues() {
        Geometry g = newFilled();
        assertThat(g.getObjectRef()).isEqualTo("geo-12345");
        assertThat(g.getProjectId()).isEqualTo("p-1");
        assertThat(g.getMaterial()).isEqualTo("Steel 20Kh");
        assertThat(g.getVolume()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(g.getMass()).isEqualByComparingTo(BigDecimal.valueOf(7850));
        assertThat(g.getMinWallThickness()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(g.getMinFilletRadius()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(g.getMinDraftAngle()).isEqualByComparingTo(BigDecimal.valueOf(1));
        assertThat(g.getNominalDimensions()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(g.getComplexityScore()).isEqualTo(4);
        assertThat(g.isUndercuts()).isTrue();
        assertThat(g.isHotSpots()).isFalse();
    }

    @Test
    @DisplayName("defaults: complexityScore=0, undercuts=false, hotSpots=false")
    void defaults() {
        Geometry g = new Geometry();
        assertThat(g.getComplexityScore()).isZero();
        assertThat(g.isUndercuts()).isFalse();
        assertThat(g.isHotSpots()).isFalse();
        assertThat(g.getObjectRef()).isNull();
    }

    @Test
    @DisplayName("protected no-arg конструктор доступен через reflection (требование JPA)")
    void protectedNoArgConstructor() throws Exception {
        var ctor = Geometry.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isProtected(ctor.getModifiers())).isTrue();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }

    @Test
    @DisplayName("complexityScore — примитив int (не Integer)")
    void complexityScore_isPrimitiveInt() throws Exception {
        var f = Geometry.class.getDeclaredField("complexityScore");
        assertThat(f.getType()).isEqualTo(int.class);
    }
}