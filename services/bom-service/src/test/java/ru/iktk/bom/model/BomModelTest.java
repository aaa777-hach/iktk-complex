package ru.iktk.bom.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BOM-модели — сеттеры/геттеры, touch(), equals/hashCode")
class BomModelTest {

    // ===== AssemblyUnit =====

    @Test
    @DisplayName("AssemblyUnit: все сеттеры и геттеры")
    void assemblyUnit_allSettersAndGetters() {
        AssemblyUnit u = new AssemblyUnit();
        Instant now = Instant.now();
        u.setId("u1");
        u.setProjectId("proj1");
        u.setParentId("p0");
        u.setDesignation("СБ-001");
        u.setName("Корпус");
        u.setDescription("desc");
        u.setGeometryId("geo-1");
        u.setCadSystem("SIEMENS");
        u.setItemCount(5);
        u.setMaxDepth(2);
        u.setTotalMassKg(new BigDecimal("12.5"));
        u.setVersion(3);
        u.setStatus("ACTIVE");
        u.setUpdatedBy("ivanov");
        u.setParameters(Map.of("k", "v"));
        u.setCreatedAt(now);
        u.setUpdatedAt(now);

        assertThat(u.getId()).isEqualTo("u1");
        assertThat(u.getProjectId()).isEqualTo("proj1");
        assertThat(u.getParentId()).isEqualTo("p0");
        assertThat(u.getDesignation()).isEqualTo("СБ-001");
        assertThat(u.getName()).isEqualTo("Корпус");
        assertThat(u.getDescription()).isEqualTo("desc");
        assertThat(u.getGeometryId()).isEqualTo("geo-1");
        assertThat(u.getCadSystem()).isEqualTo("SIEMENS");
        assertThat(u.getItemCount()).isEqualTo(5);
        assertThat(u.getMaxDepth()).isEqualTo(2);
        assertThat(u.getTotalMassKg()).isEqualByComparingTo("12.5");
        assertThat(u.getVersion()).isEqualTo(3);
        assertThat(u.getStatus()).isEqualTo("ACTIVE");
        assertThat(u.getUpdatedBy()).isEqualTo("ivanov");
        assertThat(u.getParameters()).containsEntry("k", "v");
        assertThat(u.getCreatedAt()).isEqualTo(now);
        assertThat(u.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("AssemblyUnit: touch() обновляет updatedAt через сеттер")
    void assemblyUnit_touchOnSetter() throws InterruptedException {
        AssemblyUnit u = new AssemblyUnit();
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        Instant before = u.getUpdatedAt();
        Thread.sleep(5);
        u.setName("Новое");
        assertThat(u.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("AssemblyUnit: equals/hashCode по id")
    void assemblyUnit_equality() {
        AssemblyUnit a = new AssemblyUnit();
        a.setId("u1");
        AssemblyUnit b = new AssemblyUnit();
        b.setId("u1");
        b.setName("другое");
        AssemblyUnit c = new AssemblyUnit();
        c.setId("u2");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    // ===== BomItem =====

    @Test
    @DisplayName("BomItem: все сеттеры и геттеры")
    void bomItem_allSettersAndGetters() {
        BomItem i = new BomItem();
        i.setId("i1");
        i.setAssemblyId("u1");
        i.setParentId("i0");
        i.setOrder(1);
        i.setLevel(2);
        i.setPosition("01");
        i.setDesignation("Д-001");
        i.setName("Вал");
        i.setQuantity(3);
        i.setUnit("шт");
        i.setMaterial("Сталь");
        i.setType("PART");
        i.setGeometryId("geo-1");
        i.setCadSystem("KOMPAS");
        i.setMassKg(new BigDecimal("1.5"));
        i.setNote("note");
        i.setParameters(Map.of("k", "v"));

        assertThat(i.getId()).isEqualTo("i1");
        assertThat(i.getAssemblyId()).isEqualTo("u1");
        assertThat(i.getParentId()).isEqualTo("i0");
        assertThat(i.getOrder()).isEqualTo(1);
        assertThat(i.getLevel()).isEqualTo(2);
        assertThat(i.getPosition()).isEqualTo("01");
        assertThat(i.getDesignation()).isEqualTo("Д-001");
        assertThat(i.getName()).isEqualTo("Вал");
        assertThat(i.getQuantity()).isEqualTo(3);
        assertThat(i.getUnit()).isEqualTo("шт");
        assertThat(i.getMaterial()).isEqualTo("Сталь");
        assertThat(i.getType()).isEqualTo("PART");
        assertThat(i.getGeometryId()).isEqualTo("geo-1");
        assertThat(i.getCadSystem()).isEqualTo("KOMPAS");
        assertThat(i.getMassKg()).isEqualByComparingTo("1.5");
        assertThat(i.getNote()).isEqualTo("note");
        assertThat(i.getParameters()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("BomItem: touch() через setCreatedAt/setUpdatedAt")
    void bomItem_setTimestamps() {
        BomItem i = new BomItem();
        Instant now = Instant.now();
        i.setCreatedAt(now);
        i.setUpdatedAt(now);
        assertThat(i.getCreatedAt()).isEqualTo(now);
        assertThat(i.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("BomItem: equals/hashCode по id")
    void bomItem_equality() {
        BomItem a = new BomItem();
        a.setId("i1");
        BomItem b = new BomItem();
        b.setId("i1");
        b.setName("другое");
        BomItem c = new BomItem();
        c.setId("i2");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    // ===== AssemblyConnection =====

    @Test
    @DisplayName("AssemblyConnection: все сеттеры и геттеры")
    void assemblyConnection_allSettersAndGetters() {
        AssemblyConnection c = new AssemblyConnection();
        c.setId("c1");
        c.setAssemblyId("u1");
        c.setType("BOLT");
        c.setName("Болтовое");
        c.setDescription("desc");
        c.setPart1Id("i1");
        c.setPart2Id("i2");
        c.setPart1Designation("Д-001");
        c.setPart2Designation("Д-002");
        c.setOrder(1);
        c.setNeedsTool(true);
        c.setNeedsFixture(true);
        c.setNeedsControl(false);
        c.setToolType("KEY");
        c.setFixtureType("JIG");
        c.setDiameterMm(new BigDecimal("10.0"));
        c.setClearanceMm(new BigDecimal("0.1"));
        c.setInterferenceMm(new BigDecimal("0.05"));
        c.setTorqueNm(new BigDecimal("25.0"));
        c.setPressForceKn(new BigDecimal("5.0"));
        c.setWeldLengthMm(new BigDecimal("50.0"));
        c.setWeldLegMm(new BigDecimal("3.0"));
        c.setParameters(Map.of("k", "v"));
        c.setNote("note");

        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.getAssemblyId()).isEqualTo("u1");
        assertThat(c.getType()).isEqualTo("BOLT");
        assertThat(c.getName()).isEqualTo("Болтовое");
        assertThat(c.getDescription()).isEqualTo("desc");
        assertThat(c.getPart1Id()).isEqualTo("i1");
        assertThat(c.getPart2Id()).isEqualTo("i2");
        assertThat(c.getPart1Designation()).isEqualTo("Д-001");
        assertThat(c.getPart2Designation()).isEqualTo("Д-002");
        assertThat(c.getOrder()).isEqualTo(1);
        assertThat(c.isNeedsTool()).isTrue();
        assertThat(c.isNeedsFixture()).isTrue();
        assertThat(c.isNeedsControl()).isFalse();
        assertThat(c.getToolType()).isEqualTo("KEY");
        assertThat(c.getFixtureType()).isEqualTo("JIG");
        assertThat(c.getDiameterMm()).isEqualByComparingTo("10.0");
        assertThat(c.getClearanceMm()).isEqualByComparingTo("0.1");
        assertThat(c.getInterferenceMm()).isEqualByComparingTo("0.05");
        assertThat(c.getTorqueNm()).isEqualByComparingTo("25.0");
        assertThat(c.getPressForceKn()).isEqualByComparingTo("5.0");
        assertThat(c.getWeldLengthMm()).isEqualByComparingTo("50.0");
        assertThat(c.getWeldLegMm()).isEqualByComparingTo("3.0");
        assertThat(c.getParameters()).containsEntry("k", "v");
        assertThat(c.getNote()).isEqualTo("note");
    }

    @Test
    @DisplayName("AssemblyConnection: setCreatedAt/setUpdatedAt")
    void assemblyConnection_setTimestamps() {
        AssemblyConnection c = new AssemblyConnection();
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        assertThat(c.getCreatedAt()).isEqualTo(now);
        assertThat(c.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("AssemblyConnection: equals/hashCode по id")
    void assemblyConnection_equality() {
        AssemblyConnection a = new AssemblyConnection();
        a.setId("c1");
        AssemblyConnection b = new AssemblyConnection();
        b.setId("c1");
        b.setType("WELD");
        AssemblyConnection c = new AssemblyConnection();
        c.setId("c2");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    // ===== Enum-значения =====

    @Test
    @DisplayName("BomItemType: значения enum")
    void bomItemType_values() {
        BomItem.BomItemType[] values = BomItem.BomItemType.values();
        assertThat(values).isNotEmpty();
        assertThat(values).contains(BomItem.BomItemType.PART);
    }

    @Test
    @DisplayName("ConnectionType: значения enum")
    void connectionType_values() {
        AssemblyConnection.ConnectionType[] values = AssemblyConnection.ConnectionType.values();
        assertThat(values).isNotEmpty();
    }
}