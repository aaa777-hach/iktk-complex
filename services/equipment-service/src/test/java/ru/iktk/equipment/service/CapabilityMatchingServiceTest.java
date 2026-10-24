package ru.iktk.equipment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.iktk.equipment.model.Equipment;
import ru.iktk.equipment.model.EquipmentAssignment;
import ru.iktk.equipment.model.EquipmentCapability;
import ru.iktk.equipment.repository.EquipmentAssignmentRepository;
import ru.iktk.equipment.repository.EquipmentRepository;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CapabilityMatchingService — unit-тесты")
class CapabilityMatchingServiceTest {

    @Mock private EquipmentRepository equipmentRepository;
    @Mock private EquipmentAssignmentRepository assignmentRepository;
    @InjectMocks private CapabilityMatchingService service;

    // ---------- helpers ----------

    private static Equipment eq(String id) {
        return new Equipment(id, "eq-" + id, "TURNING");
    }

    private static EquipmentCapability cap(Equipment e, String opType) {
        EquipmentCapability c = new EquipmentCapability(e.getId() + "-c-" + opType, e.getId(), opType);
        e.addCapability(c);
        return c;
    }

    private static Map<String, Object> req(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ---------- findMatching: guards ----------

    @Test
    @DisplayName("findMatching: null requirement -> empty")
    void fm_null() {
        assertThat(service.findMatching(null)).isEmpty();
    }

    @Test
    @DisplayName("findMatching: missing operationType -> empty")
    void fm_missingOp() {
        assertThat(service.findMatching(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("findMatching: blank operationType -> empty")
    void fm_blankOp() {
        assertThat(service.findMatching(req("operationType", "   "))).isEmpty();
    }

    @Test
    @DisplayName("findMatching: empty candidates -> empty")
    void fm_emptyCandidates() {
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of());
        assertThat(service.findMatching(req("operationType", "TURNING"))).isEmpty();
    }

    @Test
    @DisplayName("findMatching: null candidates -> empty")
    void fm_nullCandidates() {
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(null);
        assertThat(service.findMatching(req("operationType", "TURNING"))).isEmpty();
    }

    @Test
    @DisplayName("findMatching: equipment with null capabilities -> skipped")
    void fm_nullCapsList() {
        Equipment e = eq("e1");
        e.setCapabilities(null);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING"))).isEmpty();
    }

    @Test
    @DisplayName("findMatching: cap with null operationType -> skipped")
    void fm_nullCapOp() {
        Equipment e = eq("e1");
        EquipmentCapability c = new EquipmentCapability("c1", "e1", null);
        e.addCapability(c);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING"))).isEmpty();
    }

    @Test
    @DisplayName("findMatching: cap with different operationType -> skipped")
    void fm_otherCapOp() {
        Equipment e = eq("e1");
        cap(e, "MILLING");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING"))).isEmpty();
    }

    @Test
    @DisplayName("findMatching: operationType is case-insensitive")
    void fm_caseInsensitive() {
        Equipment e = eq("e1");
        cap(e, "turning");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING"))).isPresent();
    }

    // ---------- filters ----------

    @Test
    @DisplayName("reqCnc=true, cap not CNC -> skipped")
    void fm_cncFails() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setCncCapable(false);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING", "requiredCnc", true))).isEmpty();
    }

    @Test
    @DisplayName("reqCnc=true, cap CNC -> passes")
    void fm_cncPasses() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setCncCapable(true);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING", "requiredCnc", true))).isPresent();
    }

    @Test
    @DisplayName("reqCnc=false, cap not CNC -> passes")
    void fm_cncFalsePasses() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setCncCapable(false);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING", "requiredCnc", false))).isPresent();
    }

    @Test
    @DisplayName("reqPower > capPower -> skipped")
    void fm_powerFails() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setPowerKw(new BigDecimal("5"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredPowerKw", new BigDecimal("10")))).isEmpty();
    }

    @Test
    @DisplayName("reqPower <= capPower -> passes")
    void fm_powerPasses() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setPowerKw(new BigDecimal("10"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredPowerKw", new BigDecimal("5")))).isPresent();
    }

    @Test
    @DisplayName("reqPower set, capPower null -> no filter")
    void fm_powerNullCap() {
        Equipment e = eq("e1");
        cap(e, "TURNING");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredPowerKw", new BigDecimal("10")))).isPresent();
    }

    @Test
    @DisplayName("reqForce > capForce -> skipped")
    void fm_forceFails() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setForceKn(new BigDecimal("5"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredForceKn", new BigDecimal("10")))).isEmpty();
    }

    @Test
    @DisplayName("reqForce <= capForce -> passes")
    void fm_forcePasses() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setForceKn(new BigDecimal("10"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredForceKn", new BigDecimal("5")))).isPresent();
    }

    @Test
    @DisplayName("reqAccuracy IT8, cap grade 10 -> skipped")
    void fm_accuracyFails() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setAccuracyGrade(10);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredAccuracy", "IT8"))).isEmpty();
    }

    @Test
    @DisplayName("reqAccuracy IT8, cap grade 8 -> passes")
    void fm_accuracyPasses() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setAccuracyGrade(8);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredAccuracy", "IT8"))).isPresent();
    }

    @Test
    @DisplayName("reqMaterial not in cap.materials -> skipped")
    void fm_materialFails() {
        Equipment e = eq("e1");
        EquipmentCapability c = cap(e, "TURNING");
        c.setMaterials(Set.of("ALUMINUM"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "material", "STEEL"))).isEmpty();
    }

    @Test
    @DisplayName("reqMaterial in cap.materials (case-insens) -> passes")
    void fm_materialPasses() {
        Equipment e = eq("e1");
        EquipmentCapability c = cap(e, "TURNING");
        c.setMaterials(Set.of("steel"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "material", "STEEL"))).isPresent();
    }

    @Test
    @DisplayName("reqMaterial set, cap.materials null -> no filter")
    void fm_materialNullCap() {
        Equipment e = eq("e1");
        EquipmentCapability c = cap(e, "TURNING");
        c.setMaterials(null);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "material", "STEEL"))).isPresent();
    }

    @Test
    @DisplayName("reqMaterial set, cap.materials empty -> no filter")
    void fm_materialEmptyCap() {
        Equipment e = eq("e1");
        EquipmentCapability c = cap(e, "TURNING");
        c.setMaterials(new HashSet<>());
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "material", "STEEL"))).isPresent();
    }

    @Test
    @DisplayName("reqDimensions: cap dimensions too small -> skipped")
    void fm_dimsFails() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setMaxDimensions("100x100x100");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "maxDimensions", "200x200x200"))).isEmpty();
    }

    @Test
    @DisplayName("reqDimensions: cap dimensions fits -> passes")
    void fm_dimsPasses() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setMaxDimensions("300x300x300");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "maxDimensions", "200x200x200"))).isPresent();
    }

    @Test
    @DisplayName("reqDimensions: invalid format -> dimensionsFit returns true")
    void fm_dimsInvalidFormat() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setMaxDimensions("not-a-dims");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "maxDimensions", "200x200x200"))).isPresent();
    }

    @Test
    @DisplayName("reqDimensions: Cyrillic 'х' as separator -> parses")
    void fm_dimsCyrillic() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setMaxDimensions("300х300х300");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "maxDimensions", "200x200x200"))).isPresent();
    }

    // ---------- sorting ----------

    @Test
    @DisplayName("returns candidate with higher score first")
    void fm_sortedByScore() {
        Equipment e1 = eq("e1");
        cap(e1, "TURNING"); // all fields null -> defaults

        Equipment e2 = eq("e2");
        EquipmentCapability c2 = cap(e2, "TURNING");
        c2.setAccuracyGrade(8);
        c2.setPowerKw(new BigDecimal("10"));

        when(equipmentRepository.findActiveWithCapabilities("TURNING"))
                .thenReturn(List.of(e1, e2));

        Optional<Map<String, Object>> result = service.findMatching(
                req("operationType", "TURNING",
                        "requiredAccuracy", "IT8",
                        "requiredPowerKw", new BigDecimal("5")));

        assertThat(result).isPresent();
        assertThat(result.get().get("equipmentId")).isEqualTo("e2");
    }

    @Test
    @DisplayName("result map contains expected keys")
    void fm_resultMapKeys() {
        Equipment e = eq("e1");
        cap(e, "TURNING");
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));

        Map<String, Object> r = service.findMatching(req("operationType", "TURNING")).orElseThrow();
        assertThat(r).containsKeys("equipmentId", "equipmentName", "equipmentType",
                "capabilityId", "operationType", "score", "rationale");
    }

    // ---------- findAlternatives ----------

    @Test
    @DisplayName("findAlternatives: null opType -> empty")
    void fa_nullOp() {
        assertThat(service.findAlternatives(null, "IT7", null)).isEmpty();
    }

    @Test
    @DisplayName("findAlternatives: blank opType -> empty")
    void fa_blankOp() {
        assertThat(service.findAlternatives("   ", "IT7", null)).isEmpty();
    }

    @Test
    @DisplayName("findAlternatives: empty candidates -> delegate to matchAll")
    void fa_noCandidates() {
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of());
        assertThat(service.findAlternatives("TURNING", "IT7", List.of())).isEmpty();
    }

    @Test
    @DisplayName("findAlternatives: copies fields from first candidate")
    void fa_copiesFields() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setCncCapable(true);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));

        Map<String, Object> first = new HashMap<>();
        first.put("requiredCnc", true);

        assertThat(service.findAlternatives("TURNING", null, List.of(first))).hasSize(1);
    }

    @Test
    @DisplayName("findAlternatives: null first candidate -> no crash")
    void fa_nullFirst() {
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of());
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(null);
        assertThat(service.findAlternatives("TURNING", "IT7", candidates)).isEmpty();
    }

    // ---------- assign ----------

    @Test
    @DisplayName("assign: null projectId -> IllegalArgumentException")
    void as_nullProject() {
        assertThatThrownBy(() -> service.assign(null, "e1", "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    @DisplayName("assign: null equipmentId -> IllegalArgumentException")
    void as_nullEquipment() {
        assertThatThrownBy(() -> service.assign("p1", null, "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equipmentId");
    }

    @Test
    @DisplayName("assign: existing assignment -> no save")
    void as_existing() {
        EquipmentAssignment existing = new EquipmentAssignment("a1", "p1", "e1", "u");
        when(assignmentRepository.findByProjectIdAndEquipmentIdAndActiveTrue("p1", "e1"))
                .thenReturn(Optional.of(existing));
        service.assign("p1", "e1", "u");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign: new assignment -> saved with correct fields")
    void as_new() {
        when(assignmentRepository.findByProjectIdAndEquipmentIdAndActiveTrue("p1", "e1"))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(EquipmentAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.assign("p1", "e1", "u");

        ArgumentCaptor<EquipmentAssignment> captor = ArgumentCaptor.forClass(EquipmentAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        EquipmentAssignment saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo("p1");
        assertThat(saved.getEquipmentId()).isEqualTo("e1");
        assertThat(saved.getAssignedBy()).isEqualTo("u");
        assertThat(saved.isActive()).isTrue();
    }

    // ---------- bool/dec via findMatching ----------

    @Test
    @DisplayName("reqCnc as string 'true' -> parsed true, cap not CNC -> skipped")
    void bool_stringTrue() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setCncCapable(false);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredCnc", "true"))).isEmpty();
    }

    @Test
    @DisplayName("requiredPowerKw as string -> parsed as BigDecimal")
    void dec_stringNumber() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setPowerKw(new BigDecimal("10"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredPowerKw", "5"))).isPresent();
    }

    @Test
    @DisplayName("requiredPowerKw as Integer -> parsed as BigDecimal")
    void dec_integer() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setPowerKw(new BigDecimal("10"));
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredPowerKw", 5))).isPresent();
    }

    @Test
    @DisplayName("requiredAccuracy non-numeric -> parseAccuracy returns null (no filter)")
    void accuracy_nonNumeric() {
        Equipment e = eq("e1");
        cap(e, "TURNING").setAccuracyGrade(10);
        when(equipmentRepository.findActiveWithCapabilities("TURNING")).thenReturn(List.of(e));
        assertThat(service.findMatching(req("operationType", "TURNING",
                "requiredAccuracy", "abc"))).isPresent();
    }
}