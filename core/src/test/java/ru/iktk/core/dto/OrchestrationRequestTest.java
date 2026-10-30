package ru.iktk.core.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.core.domain.Project;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestrationRequest — DTO запроса")
class OrchestrationRequestTest {

    @Test
    @DisplayName("builder: все поля заданы -> геттеры возвращают значения")
    void builder_allFields_set() {
        OrchestrationRequest r = OrchestrationRequest.builder()
                .projectName("Корпус")
                .designation("КР-001")
                .description("Описание")
                .objectType(Project.ObjectType.PART)
                .enterpriseCode("PLANT-01")
                .requestedBy("ivanov")
                .responsibleEngineer("petrov")
                .geometryId("geo-1")
                .assemblyId("asm-1")
                .plannedProcessTypes(List.of("MACHINING"))
                .stubProcessType("MACHINING")
                .material("Steel 45")
                .volume(125000.0)
                .mass(0.98)
                .complexity(3)
                .warnings(List.of("w1", "w2"))
                .payload(Map.of("k", "v"))
                .build();

        assertThat(r.getProjectName()).isEqualTo("Корпус");
        assertThat(r.getDesignation()).isEqualTo("КР-001");
        assertThat(r.getDescription()).isEqualTo("Описание");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.PART);
        assertThat(r.getEnterpriseCode()).isEqualTo("PLANT-01");
        assertThat(r.getRequestedBy()).isEqualTo("ivanov");
        assertThat(r.getResponsibleEngineer()).isEqualTo("petrov");
        assertThat(r.getGeometryId()).isEqualTo("geo-1");
        assertThat(r.getAssemblyId()).isEqualTo("asm-1");
        assertThat(r.getPlannedProcessTypes()).containsExactly("MACHINING");
        assertThat(r.getStubProcessType()).isEqualTo("MACHINING");
        assertThat(r.getMaterial()).isEqualTo("Steel 45");
        assertThat(r.getVolume()).isEqualTo(125000.0);
        assertThat(r.getMass()).isEqualTo(0.98);
        assertThat(r.getComplexity()).isEqualTo(3);
        assertThat(r.getWarnings()).containsExactly("w1", "w2");
        assertThat(r.getPayload()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("builder: пустой -> все null/false")
    void builder_empty_defaults() {
        OrchestrationRequest r = OrchestrationRequest.builder().build();
        assertThat(r.getProjectName()).isNull();
        assertThat(r.getDesignation()).isNull();
        assertThat(r.getDescription()).isNull();
        assertThat(r.getObjectType()).isNull();
        assertThat(r.getEnterpriseCode()).isNull();
        assertThat(r.getRequestedBy()).isNull();
        assertThat(r.getResponsibleEngineer()).isNull();
        assertThat(r.getGeometryId()).isNull();
        assertThat(r.getAssemblyId()).isNull();
        assertThat(r.getPlannedProcessTypes()).isNull();
        assertThat(r.getStubProcessType()).isNull();
        assertThat(r.getMaterial()).isNull();
        assertThat(r.getVolume()).isNull();
        assertThat(r.getMass()).isNull();
        assertThat(r.getComplexity()).isNull();
        assertThat(r.getWarnings()).isNull();
        assertThat(r.getPayload()).isNull();

        assertThat(r.hasGeometry()).isFalse();
        assertThat(r.hasAssembly()).isFalse();
        assertThat(r.hasStubProcessType()).isFalse();
        assertThat(r.hasModelInfo()).isFalse();
        assertThat(r.isPart()).isFalse();
        assertThat(r.isAssembly()).isFalse();
    }

    @Test
    @DisplayName("конструктор (projectName, designation, objectType, requestedBy)")
    void constructor_main() {
        OrchestrationRequest r = new OrchestrationRequest(
                "Корпус", "КР-001", Project.ObjectType.ASSEMBLY, "ivanov");
        assertThat(r.getProjectName()).isEqualTo("Корпус");
        assertThat(r.getDesignation()).isEqualTo("КР-001");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(r.getRequestedBy()).isEqualTo("ivanov");
        assertThat(r.isAssembly()).isTrue();
    }

    @Test
    @DisplayName("hasGeometry: null/blank/ok")
    void hasGeometry_cases() {
        OrchestrationRequest r = new OrchestrationRequest();
        assertThat(r.hasGeometry()).isFalse();
        r.setGeometryId("  ");
        assertThat(r.hasGeometry()).isFalse();
        r.setGeometryId("geo-1");
        assertThat(r.hasGeometry()).isTrue();
    }

    @Test
    @DisplayName("hasAssembly: null/blank/ok")
    void hasAssembly_cases() {
        OrchestrationRequest r = new OrchestrationRequest();
        assertThat(r.hasAssembly()).isFalse();
        r.setAssemblyId("");
        assertThat(r.hasAssembly()).isFalse();
        r.setAssemblyId("asm-1");
        assertThat(r.hasAssembly()).isTrue();
    }

    @Test
    @DisplayName("hasStubProcessType: null/blank/ok")
    void hasStubProcessType_cases() {
        OrchestrationRequest r = new OrchestrationRequest();
        assertThat(r.hasStubProcessType()).isFalse();
        r.setStubProcessType("   ");
        assertThat(r.hasStubProcessType()).isFalse();
        r.setStubProcessType("MACHINING");
        assertThat(r.hasStubProcessType()).isTrue();
    }

    @Test
    @DisplayName("hasModelInfo: каждое поле + пустой warnings")
    void hasModelInfo_cases() {
        OrchestrationRequest r = new OrchestrationRequest();
        assertThat(r.hasModelInfo()).isFalse();

        r.setMaterial("Steel 45");
        assertThat(r.hasModelInfo()).isTrue();

        r = new OrchestrationRequest();
        r.setVolume(1.0);
        assertThat(r.hasModelInfo()).isTrue();

        r = new OrchestrationRequest();
        r.setMass(1.0);
        assertThat(r.hasModelInfo()).isTrue();

        r = new OrchestrationRequest();
        r.setComplexity(2);
        assertThat(r.hasModelInfo()).isTrue();

        r = new OrchestrationRequest();
        r.setWarnings(List.of());
        assertThat(r.hasModelInfo()).isFalse();
        r.setWarnings(List.of("w"));
        assertThat(r.hasModelInfo()).isTrue();
    }

    @Test
    @DisplayName("isPart/isAssembly: только для соответствующего objectType")
    void isPart_isAssembly() {
        OrchestrationRequest r = new OrchestrationRequest();
        assertThat(r.isPart()).isFalse();
        assertThat(r.isAssembly()).isFalse();

        r.setObjectType(Project.ObjectType.PART);
        assertThat(r.isPart()).isTrue();
        assertThat(r.isAssembly()).isFalse();

        r.setObjectType(Project.ObjectType.ASSEMBLY);
        assertThat(r.isPart()).isFalse();
        assertThat(r.isAssembly()).isTrue();
    }

    @Test
    @DisplayName("equals/hashCode/toString")
    void equals_hashCode_toString() {
        OrchestrationRequest a = OrchestrationRequest.builder()
                .projectName("Корпус").designation("КР-001")
                .objectType(Project.ObjectType.PART).requestedBy("ivanov").build();
        OrchestrationRequest b = OrchestrationRequest.builder()
                .projectName("Корпус").designation("КР-001")
                .objectType(Project.ObjectType.PART).requestedBy("ivanov").build();
        OrchestrationRequest c = OrchestrationRequest.builder()
                .projectName("Другой").designation("КР-002")
                .objectType(Project.ObjectType.ASSEMBLY).requestedBy("petrov").build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(null).isNotEqualTo("other");
        assertThat(a).isEqualTo(a);

        assertThat(a.toString())
                .contains("Корпус").contains("КР-001").contains("PART").contains("ivanov");
    }
}