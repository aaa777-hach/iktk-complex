package ru.iktk.equipment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Equipment и EquipmentCapability — бизнес-методы и состояние")
class EquipmentAndCapabilityTest {

    // ===== Equipment: конструктор и статусы =====

    @Test
    @DisplayName("Equipment: конструктор ставит AVAILABLE и timestamps")
    void equipment_constructorDefaults() {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        assertThat(e.getId()).isEqualTo("eq1");
        assertThat(e.getName()).isEqualTo("Станок");
        assertThat(e.getType()).isEqualTo("TURNING");
        assertThat(e.getStatus()).isEqualTo("AVAILABLE");
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
        assertThat(e.getCapabilities()).isEmpty();
    }

    @Test
    @DisplayName("Equipment: markAvailable/markBusy/markMaintenance меняют status")
    void equipment_statusTransitions() {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        e.markBusy();
        assertThat(e.getStatus()).isEqualTo("BUSY");
        assertThat(e.isAvailable()).isFalse();

        e.markMaintenance();
        assertThat(e.getStatus()).isEqualTo("MAINTENANCE");

        e.markAvailable();
        assertThat(e.getStatus()).isEqualTo("AVAILABLE");
        assertThat(e.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Equipment: decommission ставит DECOMMISSIONED и decommissionedAt")
    void equipment_decommission() {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        assertThat(e.getDecommissionedAt()).isNull();
        e.decommission();
        assertThat(e.getStatus()).isEqualTo("DECOMMISSIONED");
        assertThat(e.getDecommissionedAt()).isNotNull();
    }

    @Test
    @DisplayName("Equipment: touch через setStatus обновляет updatedAt")
    void equipment_touchOnSetter() throws InterruptedException {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        Instant before = e.getUpdatedAt();
        Thread.sleep(5);
        e.setLocation("Цех 1");
        assertThat(e.getUpdatedAt()).isAfter(before);
        assertThat(e.getLocation()).isEqualTo("Цех 1");
    }

    // ===== Equipment: supportsOperation / addCapability =====

    @Test
    @DisplayName("Equipment: supportsOperation — null/нет совпадений/совпадение")
    void equipment_supportsOperation() {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        assertThat(e.supportsOperation(null)).isFalse();
        assertThat(e.supportsOperation("MILLING")).isFalse();

        EquipmentCapability cap = new EquipmentCapability("c1", null, "MILLING");
        e.addCapability(cap);

        assertThat(e.supportsOperation("MILLING")).isTrue();
        assertThat(cap.getEquipmentId()).isEqualTo("eq1");
        assertThat(e.getCapabilities()).hasSize(1);
    }

    @Test
    @DisplayName("Equipment: addCapability(null) — no-op")
    void equipment_addNullCapability_noop() {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        e.addCapability(null);
        assertThat(e.getCapabilities()).isEmpty();
    }

    @Test
    @DisplayName("Equipment: removeCapability удаляет и трогает updatedAt")
    void equipment_removeCapability() throws InterruptedException {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        EquipmentCapability cap = new EquipmentCapability("c1", "eq1", "MILLING");
        e.addCapability(cap);
        Instant before = e.getUpdatedAt();
        Thread.sleep(5);
        e.removeCapability(cap);
        assertThat(e.getCapabilities()).isEmpty();
        assertThat(e.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("Equipment: removeCapability(null) — no-op")
    void equipment_removeNullCapability_noop() {
        Equipment e = new Equipment("eq1", "Станок", "TURNING");
        e.removeCapability(null);
        assertThat(e.getCapabilities()).isEmpty();
    }

    @Test
    @DisplayName("Equipment: equals/hashCode по id, toString содержит поля")
    void equipment_equalityAndToString() {
        Equipment a = new Equipment("eq1", "Станок", "TURNING");
        Equipment b = new Equipment("eq1", "Другой", "MILLING");
        Equipment c = new Equipment("eq2", "Станок", "TURNING");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("eq1", "Станок", "TURNING", "AVAILABLE");
    }

    // ===== EquipmentCapability: конструктор и флаги =====

    @Test
    @DisplayName("EquipmentCapability: конструктор ставит cncCapable=false и timestamps")
    void capability_constructorDefaults() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.getEquipmentId()).isEqualTo("eq1");
        assertThat(c.getOperationType()).isEqualTo("TURNING");
        assertThat(c.isCncCapable()).isFalse();
        assertThat(c.isCncCapableFlag()).isFalse();
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getUpdatedAt()).isNotNull();
        assertThat(c.getMaterials()).isEmpty();
    }

    @Test
    @DisplayName("EquipmentCapability: supportsMaterial — null/нет материалов/совпадение без регистра")
    void capability_supportsMaterial() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        assertThat(c.supportsMaterial(null)).isFalse();
        assertThat(c.supportsMaterial("Steel")).isFalse();

        c.addMaterial("Steel");
        assertThat(c.supportsMaterial("steel")).isTrue();
        assertThat(c.supportsMaterial("STEEL")).isTrue();
        assertThat(c.supportsMaterial("Aluminum")).isFalse();
    }

    @Test
    @DisplayName("EquipmentCapability: addMaterial(null/blank) — no-op")
    void capability_addMaterial_noop() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        c.addMaterial(null);
        c.addMaterial("   ");
        assertThat(c.getMaterials()).isEmpty();
    }

    @Test
    @DisplayName("EquipmentCapability: supportsMaterial при materials=null -> false")
    void capability_supportsMaterial_nullMaterials() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        c.setMaterials(null);
        assertThat(c.supportsMaterial("Steel")).isFalse();
    }

    @Test
    @DisplayName("EquipmentCapability: supportsPower — null/нет powerKw/больше/меньше")
    void capability_supportsPower() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        assertThat(c.supportsPower(null)).isTrue();
        assertThat(c.supportsPower(BigDecimal.ONE)).isFalse();

        c.setPowerKw(new BigDecimal("10.0"));
        assertThat(c.supportsPower(new BigDecimal("5.0"))).isTrue();
        assertThat(c.supportsPower(new BigDecimal("10.0"))).isTrue();
        assertThat(c.supportsPower(new BigDecimal("15.0"))).isFalse();
    }

    @Test
    @DisplayName("EquipmentCapability: supportsForce — null/нет forceKn/больше/меньше")
    void capability_supportsForce() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "PRESS");
        assertThat(c.supportsForce(null)).isTrue();
        assertThat(c.supportsForce(BigDecimal.ONE)).isFalse();

        c.setForceKn(new BigDecimal("100.0"));
        assertThat(c.supportsForce(new BigDecimal("50.0"))).isTrue();
        assertThat(c.supportsForce(new BigDecimal("150.0"))).isFalse();
    }

    @Test
    @DisplayName("EquipmentCapability: supportsCncOperation — cncCapable && operationType с CNC")
    void capability_supportsCncOperation() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "MILLING_CNC");
        assertThat(c.supportsCncOperation()).isFalse(); // cncCapable=false

        c.setCncCapable(true);
        assertThat(c.supportsCncOperation()).isTrue();

        c.setOperationType("MILLING");
        assertThat(c.supportsCncOperation()).isFalse(); // нет CNC в имени

        c.setOperationType("mill_cnc_5axis");
        assertThat(c.supportsCncOperation()).isTrue();

        c.setOperationType(null);
        assertThat(c.supportsCncOperation()).isFalse();
    }

    @Test
    @DisplayName("EquipmentCapability: isMainOperation — MAIN/main/иначе")
    void capability_isMainOperation() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        assertThat(c.isMainOperation()).isFalse();

        c.setOperationCategory("MAIN");
        assertThat(c.isMainOperation()).isTrue();

        c.setOperationCategory("main");
        assertThat(c.isMainOperation()).isTrue();

        c.setOperationCategory("AUXILIARY");
        assertThat(c.isMainOperation()).isFalse();
    }

    @Test
    @DisplayName("EquipmentCapability: touch через setter обновляет updatedAt")
    void capability_touchOnSetter() throws InterruptedException {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        Instant before = c.getUpdatedAt();
        Thread.sleep(5);
        c.setTolerance("IT7");
        assertThat(c.getUpdatedAt()).isAfter(before);
        assertThat(c.getTolerance()).isEqualTo("IT7");
    }

    @Test
    @DisplayName("EquipmentCapability: equals/hashCode по id, toString содержит поля")
    void capability_equalityAndToString() {
        EquipmentCapability a = new EquipmentCapability("c1", "eq1", "TURNING");
        EquipmentCapability b = new EquipmentCapability("c1", "eq2", "MILLING");
        EquipmentCapability c = new EquipmentCapability("c2", "eq1", "TURNING");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("c1", "eq1", "TURNING");
    }

    @Test
    @DisplayName("EquipmentCapability: сеттеры ЧПУ и габаритов")
    void capability_settersCncAndDimensions() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "MILLING");
        c.setCncCapable(true);
        c.setAxesCount(5);
        c.setCncSystem("Fanuc");
        c.setMaxDiameterMm(new BigDecimal("500.0"));
        c.setMaxLengthMm(new BigDecimal("1200.0"));
        c.setMaxMassKg(new BigDecimal("3000.0"));

        assertThat(c.isCncCapable()).isTrue();
        assertThat(c.getAxesCount()).isEqualTo(5);
        assertThat(c.getCncSystem()).isEqualTo("Fanuc");
        assertThat(c.getMaxDiameterMm()).isEqualByComparingTo("500.0");
        assertThat(c.getMaxLengthMm()).isEqualByComparingTo("1200.0");
        assertThat(c.getMaxMassKg()).isEqualByComparingTo("3000.0");
    }

    @Test
    @DisplayName("EquipmentCapability: addMaterial добавляет без дублей")
    void capability_addMaterial_dedup() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        c.addMaterial("Steel");
        c.addMaterial("Steel");
        c.addMaterial("Aluminum");
        Set<String> materials = c.getMaterials();
        assertThat(materials).hasSize(2).contains("Steel", "Aluminum");
    }

    @Test
    @DisplayName("EquipmentCapability: addMaterial при materials=null инициализирует HashSet")
    void capability_addMaterial_nullInit() {
        EquipmentCapability c = new EquipmentCapability("c1", "eq1", "TURNING");
        c.setMaterials(null);
        c.addMaterial("Steel");
        assertThat(c.getMaterials()).isNotNull().containsExactly("Steel");
    }

    @Test
    @DisplayName("Equipment: EquipmentStatusEnum — 4 значения")
    void equipmentStatusEnum_values() {
        Equipment.EquipmentStatusEnum[] values = Equipment.EquipmentStatusEnum.values();
        assertThat(values).hasSize(4);
        assertThat(values).containsExactly(
                Equipment.EquipmentStatusEnum.AVAILABLE,
                Equipment.EquipmentStatusEnum.BUSY,
                Equipment.EquipmentStatusEnum.MAINTENANCE,
                Equipment.EquipmentStatusEnum.DECOMMISSIONED);
    }
}